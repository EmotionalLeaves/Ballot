#!/bin/bash
# iter.sh "<action>" "<one-line change>"  — records one loop iteration and commits it locally
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
python3 - "$1" "$2" <<'PY'
import json,sys,datetime
action,msg=sys.argv[1],sys.argv[2]
routine=[[("system_check_and_update",50),("system_check_and_update",10),("system_check_and_update",1)],
 [("reduce_menus_and_buttons",5),("establish_single_access_point",5)],
 [("reduce_menus_and_buttons",1),("apply_professional_formatting",20)],
 [("test_core_functionality",20),("verify_goal_completion",20)],
 [("combine_screens",10),("remove_excess_navigation_elements",10),("apply_intuitive_layout",10)],
 [("overhaul_system_architecture",50),("apply_bold_aesthetics",50),("force_professional_standards",50)]]
s=json.load(open('LOOP_STATE.json'))
ph,tk=s['phase'],s['task']
assert routine[ph-1][tk][0]==action, f"state expects {routine[ph-1][tk][0]}"
s['iteration']+=1; s['total_iterations']+=1
line=f"- c{s['cycle']} p{ph} t{tk+1} i{s['iteration']} `{action}`: {msg}"
if s['iteration']>=routine[ph-1][tk][1]:
    s['iteration']=0; s['task']+=1
    if s['task']>=len(routine[ph-1]):
        s['task']=0; s['phase']+=1
        if s['phase']>6: s['phase']=1; s['cycle']+=1
s['updated']=datetime.date.today().isoformat()
json.dump(s,open('LOOP_STATE.json','w'),indent=2); open('LOOP_STATE.json','a').write("\n")
open('CHANGELOG.md','a').write(line+"\n")
print(line)
PY
git add -A && git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit -q -m "$2

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
${LOOP_SESSION:+Claude-Session: $LOOP_SESSION}"
