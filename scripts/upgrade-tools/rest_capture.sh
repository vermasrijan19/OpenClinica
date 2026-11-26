#!/usr/bin/env bash
# Capture REST endpoint responses (status, content-type, normalized body) into a directory.
# Usage: rest_capture.sh OUTDIR
set -u
out=$1; mkdir -p "$out"; rm -f "$out"/*
B=http://localhost:8085/OpenClinica
# monitor-role user (exercises the /rest study permission check); created if missing
PGPASSWORD=clinica psql -q -h 127.0.0.1 -U clinica -d openclinica >/dev/null 2>&1 <<'SQL'
INSERT INTO user_account (user_name, passwd, first_name, last_name, email, active_study, institutional_affiliation, status_id, owner_id, date_created, date_updated, date_lastvisit, passwd_timestamp, passwd_challenge_question, passwd_challenge_answer, phone, user_type_id, update_id, enabled, account_non_locked, lock_counter, run_webservices)
SELECT 'smokemonitor', md5('12345678'), 'Smoke', 'Monitor', 'm@example.com', 1, 'x', 1, 1, now(), now(), now(), now(), '', '', '', 2, 1, true, true, 0, false WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE user_name='smokemonitor');
INSERT INTO study_user_role (role_name, study_id, status_id, owner_id, date_created, user_name) SELECT 'monitor', 1, 1, 1, now(), 'smokemonitor' WHERE NOT EXISTS (SELECT 1 FROM study_user_role WHERE user_name='smokemonitor');
INSERT INTO authorities (username, authority) SELECT 'smokemonitor','ROLE_USER' WHERE NOT EXISTS (SELECT 1 FROM authorities WHERE username='smokemonitor');
SQL
login() {
    jar=$(mktemp)
    curl -s -c $jar -b $jar -o /dev/null $B/pages/login/login
    curl -s -c $jar -b $jar -o /dev/null --data-urlencode j_username=$1 --data-urlencode j_password=12345678 $B/j_spring_security_check
    curl -s -c $jar -b $jar -o /dev/null -L $B/MainMenu
}
i=0
login root
while read -r path; do
    [[ -z "$path" ]] && continue
    if [[ "$path" == "@"* ]]; then login "${path#@}"; continue; fi
    i=$((i+1))
    f="$out/$(printf %02d $i)"
    curl -s -b $jar -c $jar --max-time 60 -o "$f.body" -D "$f.hdr" "$B$path"
    code=$(head -1 "$f.hdr" | awk '{print $2}')
    ctype=$(grep -i '^content-type:' "$f.hdr" | tr -d '\r' | cut -d' ' -f2- | tr 'A-Z' 'a-z' | sed 's/; *charset=/;charset=/')
    # normalize volatile values
    python3 -I -c 'import re,sys; p=sys.argv[1]; s=open(p,errors="replace").read(); s=re.sub(r"jsessionid=[A-F0-9]+","",s); s=re.sub(r"[0-9]{4}-[0-9]{2}-[0-9]{2}[T ][0-9:.+-]+","<TS>",s); s=re.sub(r"MetaD[0-9]{14}[+-][0-9]{4}","MetaD<TS>",s); s=re.sub(r"app_printTime = \x27[^\x27]*\x27","app_printTime=<TS>",s); s=re.sub(r"Changeset: [0-9a-f]+","Changeset: <REV>",s); open(p,"w").write(s)' "$f.body"
    echo "$path	$code	$ctype	$(md5sum < "$f.body" | cut -c1-12)	$(wc -c < "$f.body")" >> "$out/summary.tsv"
done <<'EOF'
/rest/metadata/xml/view/S_DEFAULTS1
/rest/metadata/json/view/S_DEFAULTS1
/rest/metadata/xml/view/S_DEFAULTS1/*/*
/rest/metadata/json/view/S_DEFAULTS1/*/*
/rest/metadata/html/print/S_DEFAULTS1/*/*
/rest/clinicaldata/json/view/S_DEFAULTS1/*/*/*
/rest/clinicaldata/xml/view/S_DEFAULTS1/*/*/*
/rest/clinicaldata/html/print/S_DEFAULTS1/*/*/*
/rest2/openrosa/S_DEFAULTS1/formList
/rest2/openrosa/S_DEFAULTS1/manifest
/rest/metadata/xml/view/NOSUCHSTUDY
/pages/healthcheck/
/rest2/openrosa/S_DEFAULTS1/formXml
/rest2/openrosa/S_DEFAULTS1/formXml?formID=F_NOPE
/rest2/openrosa/S_DEFAULTS1/getSchedule
/rest2/openrosa/S_DEFAULTS1/downloadMedia
/rest/metadata/pdf/print/S_DEFAULTS1/*/*
/rest/clinicaldata/html/print/S_DEFAULTS1/*/*/*?includeDNs=y&includeAudits=y
/rest/clinicaldata/xml/view/S_DEFAULTS1/*/*/*?includeDNs=&includeAudits=y
/rest/metadata/nosuchthing
@smokemonitor
/rest/metadata/xml/view/*
/rest/metadata/xml/view/S_DEFAULTS1
/rest/clinicaldata/json/view/*/*/*/*
/rest/clinicaldata/json/view/S_DEFAULTS1/*/*/*
EOF
rm -f $jar
cat "$out/summary.tsv"
