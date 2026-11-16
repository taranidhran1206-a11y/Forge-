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
dump()  { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb exec-out cat /sdcard/ui.xml > "$OUT/ui.xml" 2>/dev/null; }
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

adb logcat -c
adb install -r -g android/app/build/outputs/apk/debug/app-debug.apk | tail -1 | tee -a "$REPORT"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell am start -n $PKG/.SplashActivity >/dev/null
sleep 12
shot 01_today; alive "launch" launch

# --- Tick a labor
before=$(text_of tv_labor_count)
tap id iv_labor_check 0; sleep 2
after=$(text_of tv_labor_count)
shot 02_labor_ticked; alive "tick labor" tick
note "LABORS   count before='$before' after='$after'"
[ "$before" != "$after" ] && note "PASS     ticking a labor changes the count" || note "FAIL     ticking a labor did not change the count"

# --- Focus session and nudge
tap text Focus; shot 03_focus; alive "open Focus" focus
tap id btn_focus_start; sleep 3; shot 04_focus_running; alive "start focus" focusstart
note "FOCUS    label='$(text_of tv_focus_label)' time='$(text_of tv_focus_time)'"
adb shell input keyevent KEYCODE_HOME; sleep 28
adb shell dumpsys notification --noredact > "$OUT/notifications.txt" 2>/dev/null
if grep -q "forge_focus_nudge" "$OUT/notifications.txt"; then note "PASS     a nudge notification was posted while away"; else note "FAIL     no nudge notification after 28s away"; fi
if grep -q "forge_focus_session" "$OUT/notifications.txt"; then note "PASS     the session notification is showing"; else note "FAIL     no session notification"; fi
adb shell cmd statusbar expand-notifications; sleep 2; shot 05_notifications_while_away
adb shell cmd statusbar collapse; sleep 1
adb shell am start -n $PKG/.MainActivity >/dev/null 2>&1 || adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 5
tap text Focus; sleep 2; shot 06_focus_after_return; alive "return to Focus" focusreturn
note "NUDGES   count='$(text_of tv_nudge_count)' drifts='$(text_of tv_drift_count)' away='$(text_of tv_away_time)'"
tap id btn_focus_stop; sleep 2; alive "end focus" focusend

# --- Coach
tap text Coach; shot 07_coach; alive "open Coach" coach
tap id et_coach_input; adb shell input text "What%sshould%sI%sdo%snext"; sleep 1
tap id btn_coach_send; sleep 14
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
tap text Reflect; shot 09_reflect; alive "open Reflect" reflect
tap id et_reflection; adb shell input text "Stayed%son%stask%sbut%sstarted%slate"; sleep 1
tap id btn_submit_reflection; sleep 14
shot 10_reflect_saved; alive "save reflection" reflectsave
if has id tv_ref_text; then note "PASS     the reflection appears under past entries"; else note "FAIL     saved reflection not listed"; fi
if has id tv_ref_ai_response; then note "PASS     the reflection has a coach insight"; else note "INFO     no coach insight on the reflection"; fi

# --- Rank, Plan
tap text Rank; shot 11_rank; alive "open Rank" rank
tap text Today; sleep 1
adb shell input swipe 500 1800 500 500 300; sleep 1; shot 12_today_lower
tap id card_plan_link && { shot 13_plan; alive "open Plan" plan; adb shell input keyevent KEYCODE_BACK; sleep 1; }

# --- Widget tap, sent the way the launcher sends it
adb shell am broadcast -n $PKG/.widget.TodayPlanWidget -a com.forge.app.widget.TOGGLE_LABOR --el labor_id 2 >/dev/null
sleep 4
adb shell run-as $PKG cat shared_prefs/forge_widget_prefs.xml > "$OUT/widget_prefs.xml" 2>/dev/null
alive "widget tick broadcast" widget
tap text Today; sleep 1; adb shell input swipe 500 600 500 1800 300; sleep 1; shot 14_today_after_widget_tick
note "LABORS   count after widget tick='$(text_of tv_labor_count)'"

# --- Landscape
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1; sleep 4
shot 15_today_landscape; alive "rotate to landscape" landscape
tap text Focus; shot 16_focus_landscape; alive "Focus in landscape" focusland
tap text Rank; shot 17_rank_landscape
adb shell settings put system user_rotation 0; sleep 3

adb logcat -d -b crash > "$OUT/crash_all.txt"
adb logcat -d '*:E' | grep -i -E "forge|AndroidRuntime" | tail -150 > "$OUT/errors.txt"

# --- Device tests (widget inflation, widget tick, day carry-over)
( cd android && ./gradlew connectedDebugAndroidTest --no-daemon -q > "../$OUT/device_tests.log" 2>&1 ) \
  && note "PASS     device tests" || note "FAIL     device tests (see device_tests.log)"
mkdir -p "$OUT/test-results" && cp -r android/app/build/outputs/androidTest-results/connected/* "$OUT/test-results/" 2>/dev/null

echo "===== REPORT ====="; cat "$REPORT"
exit 0
