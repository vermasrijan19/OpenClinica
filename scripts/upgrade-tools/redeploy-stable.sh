#!/usr/bin/env bash
# Build WARs, restart Tomcat with them, report startup root causes.
# Usage: redeploy.sh [--reset-db] [--no-build]
set -uo pipefail
cd /home/sverma/IdeaProjects/OpenClinica
S=${S:-/tmp/oc-upgrade}; mkdir -p "$S"
T=~/opt/apache-tomcat-8.5.100
MVN=/home/sverma/.local/share/JetBrains/Toolbox/apps/intellij-idea-2/plugins/maven-plugin/lib/maven3/bin/mvn
reset=0 build=1
for a in "$@"; do case $a in --reset-db) reset=1;; --no-build) build=0;; esac; done

if [[ $build == 1 ]]; then
    JAVA_HOME=~/opt/jdk8u514-b01 $MVN -B -o clean package -DskipTests >"$S/build.log" 2>&1
    if ! grep -q 'BUILD SUCCESS' "$S/build.log"; then
        echo "BUILD FAILED"; grep -A2 '^\[ERROR\] /' "$S/build.log" | sed -E 's#/home/sverma/IdeaProjects/OpenClinica/##' | head -40; exit 1
    fi
fi

systemctl --user stop openclinica
ln -sfn /home/sverma/opt/openclinica-stable/OpenClinica.war "$T/webapps/OpenClinica.war"
ln -sfn /home/sverma/opt/openclinica-stable/OpenClinica-ws.war "$T/webapps/OpenClinica-ws.war"
rm -rf "$T/webapps/OpenClinica" "$T/webapps/OpenClinica-ws" "$T/work/Catalina"
: >"$T/logs/localhost.$(date +%F).log"
if [[ $reset == 1 ]]; then
    PGPASSWORD=clinica psql -q -h 127.0.0.1 -U clinica -d openclinica -c 'SET client_min_messages=warning; DROP SCHEMA public CASCADE; CREATE SCHEMA public;'
fi
since=$(date '+%F %T')
systemctl --user start openclinica
for _ in $(seq 1 100); do
    c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost:8085/OpenClinica/pages/login/login)
    journalctl --user -u openclinica --since "$since" -o cat | grep -q 'Server startup in' && break
    sleep 3
done
w=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost:8085/OpenClinica/pages/login/login)
s=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost:8085/OpenClinica-ws/)
echo "web login: HTTP $w   ws: HTTP $s"
journalctl --user -u openclinica --since "$since" -o cat | grep -E 'Context \[.*\] startup failed|Server startup in' | cut -c1-200
L="$T/logs/localhost.$(date +%F).log"
# deepest "Caused by" per failing context
grep -E 'Caused by' "$L" | sed -E 's/^\s*//' | awk '!seen[$0]++' | tail -6 | cut -c1-420
