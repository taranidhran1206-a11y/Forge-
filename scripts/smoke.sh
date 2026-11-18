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
# --- Upgrade from the previous release: data must survive
if [ -f old-debug.apk ]; then
  adb install -g old-debug.apk | tail -1 | tee -a "$REPORT"
  adb shell am start -n $PKG/.SplashActivity >/dev/null; sleep 12
  tap id iv_labor_check 0; sleep 2
  old_count=$(text_of tv_labor_count)
  note "UPGRADE  old version count='$old_count'"
  adb shell am force-stop $PKG
fi
adb install -r -g android/app/build/outputs/apk/debug/app-debug.apk | tail -1 | tee -a "$REPORT"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell am start -n $PKG/.SplashActivity >/dev/null
sleep 12
shot 01_today; alive "launch" launch
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

# --- Distraction watch: Settings stands in for a distracting app, with a 15 s threshold
adb shell appops set $PKG GET_USAGE_STATS allow
tap id nav_focus; sleep 1
adb shell input swipe 500 1600 500 700 300; sleep 1
tap id btn_distraction_toggle; sleep 3
adb shell am start-foreground-service -n $PKG/.focus.DistractionService --es test_packages com.android.settings --ei test_threshold_sec 15 >/dev/null 2>&1
sleep 3
adb shell am start -a android.settings.SETTINGS >/dev/null; sleep 30
adb shell dumpsys notification --noredact > "$OUT/notifications_distraction.txt" 2>/dev/null
if grep -q "|$PKG|2101|" "$OUT/notifications_distraction.txt"; then note "PASS     distraction nudge posted while in a distracting app"; else note "FAIL     no distraction nudge after 30 s in Settings"; fi
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
  sleep 2; tap id btn_start_workout; sleep 2
  tap id btn_add_exercise; sleep 2
  tap text "Search exercises, e.g. bench or chest"; adb shell input text "barbell%sbench"; sleep 2
  shot 20_gym_picker
  tap text "Barbell Bench Press"; sleep 2
  tap text "Weight (kg)"; adb shell input text "60"
  tap text "Reps"; adb shell input text "8"
  tap text "Log set" || tap text "LOG SET"; sleep 2
  shot 21_gym_active; alive "log a set" gymset
  tap id btn_finish_workout; sleep 3
  shot 22_gym_finished; alive "finish workout" gymfinish
  dump; grep -q "Barbell Bench Press" "$OUT/ui.xml" && note "PASS     the workout is saved with its best lift" || note "FAIL     finished workout not shown"
  adb shell input keyevent KEYCODE_BACK; sleep 2
}
tap id nav_today; sleep 2
note "GYM      labors before='$before_gym' after='$(text_of tv_labor_count)' (Exercise labor should tick)"

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
