#!/usr/bin/env bash
# Smoke test on the emulator (Δ8): install, permissions with adb, pairing with
# a code of `pesto-server pair`, one full dictation through the mic button with
# a synthetic phrase (macOS `say`), and checks on the inserted text and the
# History. Run from android/, with the emulator running and the backend on the
# Mac (`uv run pesto-server`).
#
#   tools/smoke.sh
#
# No uiautomator while dictating: it suspends accessibility services.
set -euo pipefail

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG=com.victorpolitakis.pesto
SVC="$PKG/$PKG.accessibility.PestoAccessibilityService"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

step() { echo "▶ $*"; }
fail() { echo "✘ $*"; exit 1; }

wait_log() { # wait_log <tag> <pattern> <seconds>
  local i
  for ((i = 0; i < $3 * 2; i++)); do
    if "$ADB" logcat -d -s "$1" | grep -q -- "$2"; then return 0; fi
    sleep 0.5
  done
  return 1
}

step "device and backend"
"$ADB" get-state >/dev/null 2>&1 || fail "no device: start the emulator"
curl -sf http://127.0.0.1:8000/health >/dev/null || fail "no backend on 127.0.0.1:8000: run uv run pesto-server"
"$ADB" reverse tcp:8000 tcp:8000 >/dev/null

step "build and install"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
./gradlew -q assembleDebug
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null

step "permissions (adb, emulator only)"
"$ADB" shell pm grant "$PKG" android.permission.RECORD_AUDIO
# The setting can be lost right after an install: retry until it sticks.
for _ in 1 2 3 4 5; do
  "$ADB" shell settings put secure enabled_accessibility_services "$SVC"
  "$ADB" shell settings put secure accessibility_enabled 1
  sleep 2
  [ "$("$ADB" shell settings get secure enabled_accessibility_services)" = "$SVC" ] && break
done
[ "$("$ADB" shell settings get secure enabled_accessibility_services)" = "$SVC" ] || fail "accessibility service not enabled"

step "pairing (Δ24: no token in the APK)"
code=$(cd ../backend && uv run pesto-server pair | sed -nE 's/^Pairing code: ([0-9]{8}).*/\1/p')
[ -n "$code" ] || fail "no code from pesto-server pair"
"$ADB" logcat -c
"$ADB" shell am start -n "$PKG/.debug.TestFieldsActivity" --es do pair --es code "$code" >/dev/null
wait_log Pesto "pairing result=Paired" 10 || fail "pairing failed: $("$ADB" logcat -d -s Pesto | grep pairing | tail -1)"

step "synthetic phrase"
say -v Melina -o "$WORK/phrase.aiff" "Καλημέρα Γιώργο, το meeting πάει για την Πέμπτη στις δέκα."
afconvert -f WAVE -d LEI16@16000 -c 1 "$WORK/phrase.aiff" "$WORK/phrase.wav"
"$ADB" push "$WORK/phrase.wav" /data/local/tmp/pesto_phrase.wav >/dev/null
"$ADB" shell am start -n "$PKG/.debug.TestFieldsActivity" >/dev/null
sleep 2
"$ADB" shell "run-as $PKG sh -c 'mkdir -p files && cp /data/local/tmp/pesto_phrase.wav files/debug_audio.wav'"

db_count() {
  rm -f "$WORK"/pesto.db*
  for f in pesto.db pesto.db-wal pesto.db-shm; do
    "$ADB" exec-out run-as "$PKG" cat "databases/$f" > "$WORK/$f" 2>/dev/null || rm -f "$WORK/$f"
  done
  [ -s "$WORK/pesto.db" ] && sqlite3 "$WORK/pesto.db" "SELECT COUNT(*) FROM dictations;" || echo 0
}
before=$(db_count)

step "mic button over the keyboard"
"$ADB" logcat -c
"$ADB" shell am start -n "$PKG/.debug.TestFieldsActivity" --es do focus >/dev/null
wait_log Pesto/Button "shown package=$PKG" 10 || fail "button not shown: $("$ADB" logcat -d -s Pesto/Button | tail -3)"
anchor=$("$ADB" logcat -d -s Pesto/Button | grep "shown package=$PKG" | tail -1 | sed -E 's/.*anchor=([0-9]+),([0-9]+),([0-9]+),([0-9]+).*/\1 \2 \3 \4/')
read -r l t r b <<< "$anchor"

step "dictation"
"$ADB" shell input tap $(((l + r) / 2)) $(((t + b) / 2))
wait_log Pesto/Perf tap_to_ready 10 || fail "no ready from the backend"
sleep 6 # the phrase is ~4 s
"$ADB" shell am start -n "$PKG/.debug.TestFieldsActivity" --es do finish >/dev/null
wait_log Pesto "inserted method=" 15 || fail "nothing inserted: $("$ADB" logcat -d -s Pesto | tail -5)"
method=$("$ADB" logcat -d -s Pesto | grep "inserted method=" | tail -1 | sed -E 's/.*method=//')
"$ADB" logcat -d -s Pesto/Perf | sed -E 's/.*Pesto\/Perf: /  /'

step "checks"
sleep 1
after=$(db_count)
[ "$after" -eq $((before + 1)) ] || fail "History: expected $((before + 1)) rows, found $after"
"$ADB" shell uiautomator dump /sdcard/pesto_ui.xml >/dev/null 2>&1
"$ADB" shell cat /sdcard/pesto_ui.xml | grep -q "Καλημέρα" || fail "the text is not in the field"
"$ADB" shell run-as "$PKG" rm -f files/debug_audio.wav

echo "✔ smoke test passed (insert method $method, History $before → $after)"
