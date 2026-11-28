#!/usr/bin/env bash
# Drives the debug build on an emulator and records what actually happens:
# screenshots, a pass/fail report, crash logs and notification state.
set -u
PKG=com.forge.app
OUT=smoke-out
mkdir -p "$OUT"
REPORT="$OUT/report.txt"
: > "$REPORT"

note()  { echo "$*" | tee -a "$REPORT"; }
shot()  { adb exec-out screencap -p > "$OUT/$1.png"; }
dump()  {
  for i in 1 2 3; do
    adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
    adb shell rm -f /sdcard/ui.xml
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb exec-out cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null
    grep -q "<hierarchy" "$OUT/ui.xml" && return 0
    sleep 3
  done
}
find()  { dump; python3 scripts/find_node.py "$OUT/ui.xml" "$1" "$2" "${3:-0}"; }
tap()   {
  local xy; xy=$(find "$1" "$2" "${3:-0}")
  if [ -z "$xy" ]; then note "MISSING  $1=$2"; return 1; fi
  adb shell input tap $xy; sleep 2; return 0
}
has()   { [ -n "$(find "$1" "$2")" ]; }
alive() {
  if [ -n "$(adb shell pidof $PKG | tr -d '\r')" ]; then note "ALIVE    after: $1"; else note "CRASHED  after: $1"; adb logcat -d -b crash > "$OUT/crash_after_${2}.txt"; adb shell am start -n $PKG/.SplashActivity >/dev/null; sleep 6; fi
}
text_of() { dump; python3 - "$OUT/ui.xml" "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    for n in ET.parse(sys.argv[1]).getroot().iter("node"):
        if n.get("resource-id", "").endswith(":id/" + sys.argv[2]):
            print(n.get("text", "")); break
except Exception:
    pass
PY
}

# --- Ask Gemini directly, to separate app faults from key or quota faults (the key is never printed)
python3 - >> "$REPORT" 2>&1 <<'PY'
import base64, json, re, urllib.request, urllib.error
enc = re.search(r'DEFAULT_KEY_ENCODED = "([^"]+)"', open("android/app/src/main/java/com/forge/app/util/Gemini.kt").read()).group(1)
key = base64.b64decode(enc).decode().strip()
body = json.dumps({"contents": [{"role": "user", "parts": [{"text": "Reply with the single word ok"}]}]}).encode()
for model in ["gemini-3.8-flash", "gemini-3.5-flash", "gemini-flash-lite-latest"]:
    req = urllib.request.Request(
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent" % model,
        data=body, headers={"Content-Type": "application/json", "x-goog-api-key": key})
    try:
        with urllib.request.urlopen(req, timeout=12) as r:
            print("GEMINI   %s -> %s" % (model, r.status))
    except urllib.error.HTTPError as e:
        try:
            msg = json.loads(e.read().decode()).get("error", {}).get("message", "")
        except Exception:
            msg = ""
        print("GEMINI   %s -> %s %s" % (model, e.code, msg[:220].replace(key, "<key>")))
    except Exception as e:
        print("GEMINI   %s -> %s" % (model, type(e).__name__))
PY

adb logcat -c
# Let the freshly booted emulator settle before driving it
sleep 40
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1

# --- Upgrade from the previous release: data must survive
if [ -f old-debug.apk ]; then
  adb install -g old-debug.apk | tail -1 | tee -a "$REPORT"
  adb shell am start -n $PKG/.SplashActivity >/dev/null; sleep 12
  for i in 1 2 3; do tap text "Wait" >/dev/null 2>&1 || break; sleep 2; done
  tap id iv_labor_check 0; sleep 2
  old_count=$(text_of tv_labor_count)
  note "UPGRADE  old version count='$old_count'"
  adb shell am force-stop $PKG
fi
adb install -r -g android/app/build/outputs/apk/debug/app-debug.apk | tail -1 | tee -a "$REPORT"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell am start -n $PKG/.SplashActivity >/dev/null
sleep 1; shot 00_splash
sleep 11
# The emulator's launcher sometimes shows "isn't responding" over the app; dismiss it
for i in 1 2 3; do tap text "Wait" >/dev/null 2>&1 || break; sleep 2; done
shot 01_today; alive "launch" launch
# Lock-screen plan card
adb shell dumpsys notification --noredact > "$OUT/notifications_plan.txt" 2>/dev/null
if grep -q "|$PKG|2200|" "$OUT/notifications_plan.txt"; then note "PASS     Today's plan card is posted"; else note "FAIL     Today's plan card missing"; fi
grep -A40 "|$PKG|2200|" "$OUT/notifications_plan.txt" | grep -E "android.title=|android.subText=|android.bigText=|requestPromoted" | head -5 >> "$REPORT"
adb shell input keyevent KEYCODE_SLEEP; sleep 2; adb shell input keyevent KEYCODE_WAKEUP; sleep 2
shot 01b_lockscreen_plan
adb shell wm dismiss-keyguard; sleep 2
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 3
if [ -n "${old_count:-}" ]; then
  new_count=$(text_of tv_labor_count)
  [ "$new_count" = "$old_count" ] && note "PASS     upgrade kept today's labors ($new_count)" || note "FAIL     upgrade lost data: before='$old_count' after='$new_count'"
fi

# --- Tick a labor
before=$(text_of tv_labor_count)
tap id iv_labor_check 0; sleep 2
after=$(text_of tv_labor_count)
shot 02_labor_ticked; alive "tick labor" tick
note "LABORS   count before='$before' after='$after'"
[ "$before" != "$after" ] && note "PASS     ticking a labor changes the count" || note "FAIL     ticking a labor did not change the count"

# --- Focus session and nudge
tap id nav_focus; shot 03_focus; alive "open Focus" focus
tap id btn_focus_start; sleep 3; shot 04_focus_running; alive "start focus" focusstart
if adb shell dumpsys notification --noredact | grep -q "|$PKG|2200|"; then note "FAIL     plan card should hide during focus"; else note "PASS     plan card hides while the focus timer runs"; fi
note "FOCUS    label='$(text_of tv_focus_label)' time='$(text_of tv_focus_time)' (expect REMAINING)"
adb shell input keyevent KEYCODE_HOME; sleep 28
adb shell dumpsys notification --noredact > "$OUT/notifications.txt" 2>/dev/null
# Posted notifications show up as records keyed by package and id (2001 session, 2002 nudge)
if grep -q "|$PKG|2002|" "$OUT/notifications.txt"; then note "PASS     a nudge notification was posted while away"; else note "FAIL     no nudge notification after 28s away"; fi
if grep -q "|$PKG|2001|" "$OUT/notifications.txt"; then note "PASS     the session notification is showing"; else note "FAIL     no session notification"; fi
grep -E "\|$PKG\|200[0-9]\|" "$OUT/notifications.txt" | head -5 >> "$REPORT"
adb shell cmd statusbar expand-notifications; sleep 2; shot 05_notifications_while_away
adb shell cmd statusbar collapse; sleep 1
adb shell am start -n $PKG/.MainActivity >/dev/null 2>&1 || adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 5
tap id nav_focus; sleep 2; shot 06_focus_after_return; alive "return to Focus" focusreturn
note "NUDGES   count='$(text_of tv_nudge_count)' drifts='$(text_of tv_drift_count)' away='$(text_of tv_away_time)'"
tap id btn_focus_stop; sleep 2; alive "end focus" focusend
sleep 2
if adb shell dumpsys notification --noredact | grep -q "|$PKG|2200|"; then note "PASS     plan card is back after the session"; else note "FAIL     plan card did not come back"; fi

# --- Distraction watch: Settings stands in for a distracting app
adb shell appops set $PKG GET_USAGE_STATS allow
tap id nav_focus; sleep 1
adb shell input swipe 500 1600 500 700 300; sleep 1
tap id btn_distraction_toggle; sleep 3
tap id tv_distraction_settings; sleep 1
# Pick Settings as the distracting app through the real picker, and the shortest threshold
tap id btn_choose_apps; sleep 2
tap text "Settings"; sleep 1
tap text "Save" || tap text "SAVE"; sleep 1
tap id chip_nudge_1; sleep 1
adb shell am start -a android.settings.SETTINGS >/dev/null; sleep 75
adb shell dumpsys notification --noredact > "$OUT/notifications_distraction.txt" 2>/dev/null
adb logcat -d -s ForgeDistraction | tail -40 > "$OUT/distraction_log.txt"
tail -5 "$OUT/distraction_log.txt" | cut -c1-200 >> "$REPORT"
if grep -q "|$PKG|2101|" "$OUT/notifications_distraction.txt"; then note "PASS     distraction nudge posted while in a distracting app"; else note "FAIL     no distraction nudge after 75 s in Settings"; fi
if grep -q "|$PKG|2100|" "$OUT/notifications_distraction.txt"; then note "PASS     distraction watch is running"; else note "FAIL     distraction watch notification missing"; fi
adb shell cmd statusbar expand-notifications; sleep 2; shot 18_distraction_nudge
adb shell cmd statusbar collapse; sleep 1
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 7
tap id nav_focus; sleep 2
adb shell input swipe 500 1600 500 700 300; sleep 2
shot 19_focus_distraction_card
note "DISTRACT total='$(text_of tv_distracted_total)' nudges='$(text_of tv_distraction_nudges)'"

# --- Gym log
tap id nav_today; sleep 2
adb shell input swipe 500 1800 500 400 300; sleep 1
adb shell input swipe 500 1800 500 400 300; sleep 1
before_gym=$(text_of tv_labor_count)
tap id card_gym_link && {
  sleep 3; shot 20_gym_idle; alive "open Gym" gymopen
  tap id btn_start_workout; sleep 2
  tap id btn_add_exercise; sleep 2
  tap id et_exercise_search; adb shell input text "barbell%sbench"; sleep 3
  shot 21_gym_picker
  tap text "Barbell Bench Press"; sleep 3
  tap id et_set_kg; adb shell input text "60"
  tap id et_set_reps; adb shell input text "8"
  tap id btn_set_done; sleep 2
  shot 22_gym_active; alive "log a set" gymset
  dump; grep -q 'content-desc="Done"' "$OUT/ui.xml" && note "PASS     the set stays ticked" || note "FAIL     the set didn't stay ticked"
  tap id btn_finish_workout || tap text "Finish workout"; sleep 3
  has text "Finish" && { tap text "Finish"; sleep 3; }
  shot 23_gym_finished; alive "finish workout" gymfinish
  dump; grep -q "Workout saved" "$OUT/ui.xml" && note "PASS     the workout is saved" || note "FAIL     no saved-workout summary"
  tap text "Nice" || tap text "NICE"; sleep 2
  dump; grep -q "Barbell Bench Press" "$OUT/ui.xml" && note "PASS     the workout shows on the Gym page" || note "FAIL     finished workout not shown"
  shot 24_gym_after
  adb shell input swipe 500 1800 500 500 300; sleep 1; shot 25_gym_lower
  adb shell input swipe 500 1800 500 500 300; sleep 1
  { tap text "Exercise library" || tap text "EXERCISE LIBRARY"; } && { sleep 3; shot 26_gym_library; alive "exercise library" gymlib; adb shell input keyevent KEYCODE_BACK; sleep 1; }
  adb shell input keyevent KEYCODE_BACK; sleep 2
}
# Leave any sheet still open so the rest of the run isn't stuck behind it
for i in 1 2; do has text "Close" && { tap text "Close"; sleep 1; }; done
has id nav_today || { adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1; }
tap id nav_today; sleep 2
note "GYM      labors before='$before_gym' after='$(text_of tv_labor_count)' (Exercise labor should tick)"

# --- Fuel
adb shell input swipe 500 1800 500 400 300; sleep 1
adb shell input swipe 500 1800 500 400 300; sleep 1
tap id card_fuel_link && {
  sleep 3; shot 30_fuel_goal_first; alive "open Fuel" fuelopen
  for i in 1 2 3; do has text "Save goal" && break; adb shell input swipe 500 1700 500 600 300; sleep 1; done
  tap text "Save goal" && note "PASS     first-time Fuel goal saved" || note "INFO     no goal sheet on first open"
  sleep 2; shot 31_fuel_day
  tap id btn_fuel_describe; sleep 2
  tap id et_fuel_describe; adb shell input text "2%sroti%scanai%swith%sdhal%sand%steh%starik"; sleep 1
  tap text "Estimate" || tap text "ESTIMATE"; sleep 35
  shot 32_fuel_result; alive "estimate a meal" fuelest
  for i in 1 2 3; do has id btn_fuel_save && break; adb shell input swipe 500 1700 500 600 300; sleep 1; done
  tap id btn_fuel_save && note "PASS     the meal estimate opened and was logged" || note "FAIL     no meal estimate to log"
  sleep 2; shot 33_fuel_logged
  note "FUEL     ring='$(text_of tv_fuel_left)'"
  adb shell input keyevent KEYCODE_BACK; sleep 2
}
tap id nav_today; sleep 2
adb shell input swipe 500 1800 500 400 300; sleep 1
adb shell input swipe 500 1800 500 400 300; sleep 1
note "TODAY    fuel='$(text_of tv_fuel_link)' verdict='$(text_of tv_verdict_link)' alarm='$(text_of tv_alarm_link)'"
shot 34_today_links

# --- Hercules' verdict
tap id card_verdict_link && {
  sleep 2; shot 35_verdict_empty
  tap id btn_verdict_run; sleep 40
  shot 36_verdict; alive "judge the day" verdict
  has id tv_verdict_score && note "PASS     the day has a verdict and score" || note "FAIL     no verdict score"
  adb shell input swipe 500 1700 500 600 300; sleep 1; shot 37_verdict_lower
  adb shell input keyevent KEYCODE_BACK; sleep 2
}

# --- Wake-up challenge
tap id card_alarm_link && {
  sleep 2; shot 40_alarms_empty; alive "open alarms" alarms
  tap text "New alarm"; sleep 2; shot 41_alarm_editor
  for i in 1 2 3; do has text "Save alarm" && break; adb shell input swipe 500 1700 500 600 300; sleep 1; done
  tap text "Save alarm"; sleep 2
  # Camera permission prompt for counting reps
  tap text "While using the app" || tap text "WHILE USING THE APP" || tap text "Allow" || true
  sleep 2; shot 42_alarm_list
  dump; grep -q "push-ups" "$OUT/ui.xml" && note "PASS     the alarm is saved" || note "FAIL     alarm not in the list"
  adb shell dumpsys alarm | grep -c "com.forge.app" | xargs -I{} echo "ALARM    scheduled entries for Forge: {}" | tee -a "$REPORT"
  adb shell input keyevent KEYCODE_BACK; sleep 2
}
note "TODAY    alarm card='$(text_of tv_alarm_link)'"

# Make the alarm ring in a minute (edit its time the way the app stores it), lock the
# screen, and let Android fire it for real. Then switch it off with sums.
adb shell run-as $PKG cat shared_prefs/forge_alarms.xml > "$OUT/alarms_before.xml" 2>/dev/null
python3 - "$OUT/alarms_before.xml" "$(adb shell date +%H:%M:%S | tr -d '\r')" > "$OUT/alarms_soon.xml" <<'PY'
import html, json, re, sys
xml = open(sys.argv[1]).read()
h, m, s = map(int, sys.argv[2].split(":"))
m += 2 if s > 40 else 1
h, m = (h + m // 60) % 24, m % 60
def fix(match):
    alarms = json.loads(html.unescape(match.group(1)))
    for a in alarms:
        a.update(hour=h, minute=m, days=[], enabled=True)
    return '<string name="alarms">' + html.escape(json.dumps(alarms), quote=True) + '</string>'
print(re.sub(r'<string name="alarms">(.*?)</string>', fix, xml, flags=re.S))
PY
if grep -q '"hour' "$OUT/alarms_soon.xml" || grep -q 'hour&quot;' "$OUT/alarms_soon.xml"; then
  adb shell am force-stop $PKG
  adb shell "run-as $PKG sh -c 'cat > shared_prefs/forge_alarms.xml'" < "$OUT/alarms_soon.xml"
  adb shell am start -n $PKG/.SplashActivity >/dev/null; sleep 10
  note "ALARM    next alarm clock: $(adb shell dumpsys alarm | grep -m1 -A2 'nextAlarmClock\|Next alarm clock' | tr -d '\r' | tr '\n' ' ' | cut -c1-160)"
  adb shell input keyevent KEYCODE_HOME; sleep 1
  adb shell input keyevent KEYCODE_SLEEP
  for i in $(seq 1 30); do
    sleep 5
    adb shell dumpsys activity services $PKG/.alarm.AlarmService | grep -q "ServiceRecord" && break
  done
  sleep 5; shot 43_alarm_ringing
  alive "alarm rings" alarmring
  adb shell dumpsys activity services $PKG/.alarm.AlarmService | grep -q "ServiceRecord" && note "PASS     the alarm rang on time" || note "FAIL     the alarm didn't ring"
  dump; grep -qi "Wake-up challenge" "$OUT/ui.xml" && note "PASS     the challenge opened over the lock screen" || note "FAIL     the challenge screen didn't open"
  tap text "Camera not working? Do 5 sums instead"; sleep 2
  for i in 1 2 3 4 5 6; do
    q=$(dump; python3 - "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
try:
    for n in ET.parse(sys.argv[1]).getroot().iter("node"):
        m = re.fullmatch(r"(\d+) ([+×]) (\d+)", n.get("text", ""))
        if m:
            a, op, b = int(m.group(1)), m.group(2), int(m.group(3))
            print(a + b if op == "+" else a * b); break
except Exception:
    pass
PY
)
    [ -z "$q" ] && break
    tap text "Answer"; adb shell input text "$q"; sleep 1
    tap text "Check" || tap text "CHECK"; sleep 2
  done
  shot 44_alarm_done; sleep 4
  adb shell dumpsys activity services $PKG/.alarm.AlarmService | grep -q "ServiceRecord" && note "FAIL     the alarm kept ringing after the sums" || note "PASS     the sums switched the alarm off"
  adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard; sleep 2
  adb shell am start -n $PKG/.SplashActivity >/dev/null; sleep 6
else
  note "FAIL     couldn't read the saved alarm to move it"
fi

# --- Coach
tap id nav_coach; shot 07_coach; alive "open Coach" coach
tap id et_coach_input; adb shell input text "What%sshould%sI%sdo%snext"; sleep 1
tap id btn_coach_send; sleep 60
shot 08_coach_reply; alive "send coach message" coachsend
dump; grep -o 'resource-id="com.forge.app:id/tv_ai_text"' "$OUT/ui.xml" | wc -l | xargs -I{} echo "COACH    ai replies on screen: {}" | tee -a "$REPORT"
python3 - "$OUT/ui.xml" >> "$REPORT" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    for n in ET.parse(sys.argv[1]).getroot().iter("node"):
        if n.get("resource-id", "").endswith(":id/tv_ai_text"):
            print("COACH    reply: " + n.get("text", "")[:300].replace("\n", " "))
except Exception as e:
    print("COACH    could not read reply", e)
PY

# --- Reflect
tap id nav_reflect; shot 09_reflect; alive "open Reflect" reflect
tap id et_reflection; adb shell input text "Stayed%son%stask%sbut%sstarted%slate"; sleep 1
tap id btn_submit_reflection; sleep 60
shot 10_reflect_saved; alive "save reflection" reflectsave
if has id tv_ref_text; then note "PASS     the reflection appears under past entries"; else note "FAIL     saved reflection not listed"; fi
if has id tv_ref_ai_response; then note "PASS     the reflection has a coach insight"; else note "INFO     no coach insight on the reflection"; fi

# --- Rank, Plan
tap id nav_progress; sleep 1; shot 11_rank; alive "open Rank" rank
note "RANK     $(text_of tv_rank_title) · $(text_of tv_xp_current) XP · $(text_of tv_xp_next)"
tap id nav_today; sleep 1
adb shell input swipe 500 1800 500 500 300; sleep 1; shot 12_today_lower
tap id card_plan_link && { shot 13_plan; alive "open Plan" plan; adb shell input keyevent KEYCODE_BACK; sleep 1; }

# --- Widget tap, sent the way the launcher sends it
adb shell am broadcast -n $PKG/.widget.TodayPlanWidget -a com.forge.app.widget.TOGGLE_LABOR --el labor_id 2 >/dev/null
sleep 4
adb shell run-as $PKG cat shared_prefs/forge_widget_prefs.xml > "$OUT/widget_prefs.xml" 2>/dev/null
alive "widget tick broadcast" widget
tap id nav_today; sleep 1; adb shell input swipe 500 600 500 1800 300; sleep 1; shot 14_today_after_widget_tick
note "LABORS   count after widget tick='$(text_of tv_labor_count)'"

# --- Widgets refresh with the app closed: tick through the widget, then read what it shows
adb shell am force-stop $PKG
adb shell am broadcast -n $PKG/.widget.TodayPlanWidget -a com.forge.app.widget.TOGGLE_LABOR --el labor_id 4 >/dev/null
sleep 5
adb shell run-as $PKG cat shared_prefs/forge_widget_prefs.xml > "$OUT/widget_prefs_closed.xml" 2>/dev/null
grep -o 'todayDone" value="[0-9]*"' "$OUT/widget_prefs_closed.xml" | tee -a "$REPORT"
adb shell am start -n $PKG/.SplashActivity >/dev/null; sleep 8

# --- Landscape
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1; sleep 4
shot 15_today_landscape; alive "rotate to landscape" landscape
tap id nav_focus; shot 16_focus_landscape; alive "Focus in landscape" focusland
tap id nav_progress; shot 17_rank_landscape
adb shell settings put system user_rotation 0; sleep 3

adb logcat -d -b crash > "$OUT/crash_all.txt"
adb logcat -d '*:E' | grep -i -E "forge|AndroidRuntime" | tail -150 > "$OUT/errors.txt"

# --- Device tests (widget inflation, widget tick, day carry-over)
( cd android && ./gradlew connectedDebugAndroidTest --no-daemon -q > "../$OUT/device_tests.log" 2>&1 ) \
  && note "PASS     device tests" || note "FAIL     device tests (see device_tests.log)"
mkdir -p "$OUT/test-results" && cp -r android/app/build/outputs/androidTest-results/connected/* "$OUT/test-results/" 2>/dev/null

echo "===== REPORT ====="; cat "$REPORT"
exit 0
