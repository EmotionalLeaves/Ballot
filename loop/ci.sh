#!/bin/bash
# usage: ci.sh [wait]  — prints latest CI run status for main; on failure dumps error lines
API=https://api.github.com/repos/EmotionalLeaves/ballot
SHA=$(git -C "$(dirname "$0")" rev-parse HEAD)
for i in $(seq 1 120); do
  RUN=$(curl -s "$API/actions/runs?head_sha=$SHA&per_page=5")
  STATUS=$(echo "$RUN" | python3 -c "import json,sys;d=json.load(sys.stdin);r=[x for x in d.get('workflow_runs',[]) if x['name']==sys.argv[1]];print((r[0]['status']+' '+str(r[0]['conclusion'])+' '+str(r[0]['id'])) if r else 'none')" "${WF:-CI}")
  echo "$(date +%T) $STATUS"
  case "$STATUS" in completed*) break;; esac
  sleep 30
done
ID=$(echo $STATUS | awk '{print $3}')
CONC=$(echo $STATUS | awk '{print $2}')
if [ "$CONC" != "success" ] && [ -n "$ID" ]; then
  rm -rf /tmp/cilogs && mkdir -p /tmp/cilogs
  curl -sL "$API/actions/runs/$ID/logs" -o /tmp/cilogs/logs.zip && cd /tmp/cilogs && unzip -oq logs.zip 2>/dev/null
  grep -rhE "^.{0,40}(e: |error:|FAILED|What went wrong|Lint found|Error:|\.kt:[0-9]+)" /tmp/cilogs --include=*.txt | grep -v "^.*warning:" | sed 's/^[0-9T:.Z-]* //' | sort -u | head -120
fi
