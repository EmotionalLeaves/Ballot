# Changelog

One line per loop iteration, newest last.

## 0.1.0

- Build: first full build of all six rounds (spine, ground, presence, voice, campaign, hardening).

## Loop

- c1 p1 t1 i1 `system_check_and_update`: Weekly counts now stop at the week's end, so review history shows real per-week scores; stage exits passed now count
- c1 p1 t1 i2 `system_check_and_update`: Daily brief builds after the chosen time instead of five hours early, and never during work or sleep
- c1 p1 t1 i3 `system_check_and_update`: Add the missing proactive trigger: a weekly score that drops two weeks running creates a shrink-the-plan task and one notification
