#!/usr/bin/env bash
# adb logcat 에서 Firebase App Check debug token 을 추출한다.
# debug 빌드에서 AppCheckUtil.initialize 가 호출되면 logcat 에
#   "Enter this debug secret into the allow list ... : <UUID>"
# 가 한 줄 출력되는데, 그걸 캡쳐해서 stdout 으로 토큰만 내보낸다.
#
# 사용법:
#   scripts/appcheck-debug-token.sh
#   scripts/appcheck-debug-token.sh --restart
#   APP_PACKAGE=me.zipi.navitotesla.debug scripts/appcheck-debug-token.sh
#
# 옵션:
#   --restart              앱을 강제 종료한 뒤 다시 띄워서 새로 토큰 출력 유도
#   --target <pkg>         대상 앱 패키지
#                          default: me.zipi.navitotesla.ns.debug (nostore debug)
#                          playstore debug 는 me.zipi.navitotesla.debug
#   --timeout <초>         토큰 캡쳐 대기 시간 (default: 30)
#   -h, --help             이 도움말

set -euo pipefail

TARGET_PKG="${APP_PACKAGE:-me.zipi.navitotesla.ns.debug}"
RESTART=0
TIMEOUT=30

while [[ $# -gt 0 ]]; do
    case "$1" in
        --restart)
            RESTART=1
            shift
            ;;
        --target)
            TARGET_PKG="$2"
            shift 2
            ;;
        --timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        -h|--help)
            sed -n '2,/^$/p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *)
            echo "알 수 없는 옵션: $1" >&2
            exit 1
            ;;
    esac
done

if ! adb get-state >/dev/null 2>&1; then
    echo "adb 디바이스가 연결되어 있지 않음." >&2
    exit 1
fi

PATTERN='Enter this debug secret into the allow list in the Firebase Console for your project: '

# 1) 현재 logcat buffer 에 이미 있으면 그대로 사용
if [[ "$RESTART" -eq 0 ]]; then
    EXISTING=$(adb logcat -d 2>/dev/null | grep -F "$PATTERN" | tail -1 || true)
    if [[ -n "$EXISTING" ]]; then
        echo "${EXISTING##*: }"
        exit 0
    fi
fi

# 2) 없거나 --restart 인 경우 앱 재시작 후 logcat tail
echo "logcat buffer 에 토큰 없음 — $TARGET_PKG 재시작 후 ${TIMEOUT}s 대기" >&2
adb shell am force-stop "$TARGET_PKG"
adb logcat -c
adb shell monkey -p "$TARGET_PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

DEADLINE=$(( $(date +%s) + TIMEOUT ))
while (( $(date +%s) < DEADLINE )); do
    LINE=$(adb logcat -d 2>/dev/null | grep -F "$PATTERN" | tail -1 || true)
    if [[ -n "$LINE" ]]; then
        echo "${LINE##*: }"
        exit 0
    fi
    sleep 1
done

echo "토큰을 ${TIMEOUT}초 안에 찾지 못함." >&2
echo "  - debug 빌드인지 확인 (src/debug/.../AppCheckUtil.kt 실행 경로)" >&2
echo "  - $TARGET_PKG 패키지명이 맞는지 확인" >&2
exit 1
