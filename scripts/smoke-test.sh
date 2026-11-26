#!/usr/bin/env bash
# Smoke test for a running OpenClinica instance.
#
# Usage: OC_USER=root OC_PASS=secret scripts/smoke-test.sh [base-url]
#   base-url defaults to http://localhost:8085
#   Without OC_PASS only the anonymous checks run. The authenticated checks
#   create a temporary scheduled import job and delete it again.
#   OC_USER/OC_PASS can also be kept in a private file (chmod 600) as
#   shell assignments; default location ~/.config/openclinica/smoke-credentials,
#   override with OC_CREDENTIALS=/path.
#
# Every request is gzip-enabled and time-limited, so a response that
# advertises the wrong Content-Length (the old CompressingFilter bug)
# shows up as a timeout instead of passing.
set -uo pipefail

CREDS="${OC_CREDENTIALS:-$HOME/.config/openclinica/smoke-credentials}"
if [[ -z "${OC_PASS:-}" && -r "$CREDS" ]]; then
    # shellcheck source=/dev/null
    source "$CREDS"
fi

BASE="${1:-http://localhost:8085}"
WEB="$BASE/OpenClinica"
WS="$BASE/OpenClinica-ws"
MAX_TIME="${MAX_TIME:-5}"
JAR="$(mktemp)"
trap 'rm -f "$JAR" "$JAR.body"' EXIT

pass=0
fail=0
ok()  { pass=$((pass + 1)); printf '  ok    %s\n' "$1"; }
bad() { fail=$((fail + 1)); printf '  FAIL  %s\n' "$1"; }

# fetch URL -> sets CODE, TIME, EFFECTIVE; body in $JAR.body
fetch() {
    local out
    out=$(curl -s --compressed -L -b "$JAR" -c "$JAR" --max-time "$MAX_TIME" \
        -o "$JAR.body" -w '%{http_code} %{time_total} %{url_effective}' "$@") || true
    read -r CODE TIME EFFECTIVE <<<"${out:-000 0 -}"
}

expect_200() {
    local label="$1" url="$2"
    fetch "$url"
    if [[ "$CODE" == 200 ]]; then ok "$label ($TIME s)"; else bad "$label: HTTP $CODE ($TIME s)"; fi
}

echo "Anonymous checks against $BASE"
expect_200 "login page" "$WEB/"
for path in includes/styles.css includes/NewLoginStyles.css includes/jmesa/jquery.min.js \
            includes/jmesa/jquery-migrate-3.3.1.js includes/jmesa/jquery.blockUI.js \
            includes/global_functions_javascript.js includes/ua-parser.min.js \
            includes/wz_tooltip/wz_tooltip.js images/favicon.ico; do
    expect_200 "static $path" "$WEB/$path"
done
fetch "$WS/"
if [[ "$CODE" =~ ^(200|302|404)$ && "$CODE" != 000 ]]; then ok "web services app responds (HTTP $CODE)"; else bad "web services app: HTTP $CODE"; fi
for wsdl in study/v1/studyWsdl.wsdl data/v1/dataWsdl.wsdl studySubject/v1/studySubjectWsdl.wsdl \
            event/v1/eventWsdl.wsdl studyEventDefinition/v1/studyEventDefinitionWsdl.wsdl; do
    fetch "$WS/ws/$wsdl"
    if [[ "$CODE" == 200 ]] && grep -q 'wsdl:definitions' "$JAR.body"; then ok "WSDL $wsdl"; else bad "WSDL $wsdl: HTTP $CODE"; fi
done

if [[ -n "${OC_PASS:-}" ]]; then
    user="${OC_USER:-root}"
    echo "Authenticated checks as $user"
    : >"$JAR"
    fetch "$WEB/pages/login/login"
    fetch -X POST --data-urlencode "j_username=$user" --data-urlencode "j_password=$OC_PASS" \
        "$WEB/j_spring_security_check"
    if [[ "$EFFECTIVE" == *errorLogin* || "$EFFECTIVE" == *pages/login/login* ]]; then
        bad "login rejected (landed on $EFFECTIVE)"
    else
        ok "login accepted"
        for page in MainMenu ViewStudy ListStudySubjects ListUserAccounts ListCRF ViewNotes?module=submit; do
            fetch "$WEB/$page"
            if [[ "$CODE" != 200 ]]; then
                bad "$page: HTTP $CODE"
            elif [[ "$EFFECTIVE" == *pages/login/login* ]]; then
                bad "$page: redirected to login"
            elif grep -qiE 'Exception|HTTP Status 500' "$JAR.body"; then
                bad "$page: error text in page"
            else
                ok "$page ($TIME s)"
            fi
        done

        # Scheduler round trip (Quartz): create an import job, pause, resume, delete it again.
        job="smoketest_$$"
        group="importTrigger"
        fetch --data "action=confirmall&jobName=$job&jobDesc=smoke+test&contactEmail=&filePathDir=smoketest&hours=24&minutes=0&studyId=1" \
            "$WEB/CreateJobImport"
        if grep -q "successfully created a new job: $job" "$JAR.body"; then ok "scheduler: create job"; else bad "scheduler: create job (HTTP $CODE)"; fi
        fetch "$WEB/ViewImportJob"
        if grep -q "$job" "$JAR.body"; then ok "scheduler: job listed"; else bad "scheduler: job not listed"; fi
        for step in pause resume; do
            fetch "$WEB/PauseJob?tname=$job&gname=$group"
            if [[ "$CODE" == 200 ]] && ! grep -qiE 'Exception|HTTP Status 500' "$JAR.body"; then ok "scheduler: $step"; else bad "scheduler: $step (HTTP $CODE)"; fi
        done
        fetch "$WEB/PauseJob?tname=$job&gname=$group&del=y"
        fetch "$WEB/ViewImportJob"
        if [[ "$CODE" == 200 ]] && ! grep -q "$job" "$JAR.body"; then ok "scheduler: delete job"; else bad "scheduler: job still listed after delete"; fi
    fi
else
    echo "Skipping authenticated checks (set OC_PASS to enable)"
fi

echo "Passed: $pass  Failed: $fail"
[[ $fail -eq 0 ]]
