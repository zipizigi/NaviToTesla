#!/usr/bin/env bash
# adb 로 NaviToTesla debug 빌드의 DebugTriggerReceiver 에 가짜 길안내 알림을 송신한다.
# NotificationListener 가 받았을 때와 동일하게 ShareWorker.startShare 가 호출된다.
#
# 사용법:
#   scripts/debug-trigger.sh <목적지> [목적지 ...]
#   scripts/debug-trigger.sh --navi tmap <목적지>
#   APP_PACKAGE=me.zipi.navitotesla.debug scripts/debug-trigger.sh 보정로
#
# 옵션:
#   --navi {kakao|tmap}    어느 내비 알림으로 위장할지 (default: tmap)
#   --target <pkg>         broadcast 받을 debug 앱 패키지
#                          default: me.zipi.navitotesla.ns.debug (nostore debug)
#                          playstore debug 는 me.zipi.navitotesla.debug
#
# 예:
#   scripts/debug-trigger.sh 보정로
#   scripts/debug-trigger.sh 코엑스
#   scripts/debug-trigger.sh 보정로 코엑스 강남역
#   scripts/debug-trigger.sh --navi kakao 강남역
#   APP_PACKAGE=me.zipi.navitotesla.debug scripts/debug-trigger.sh 코엑스

set -euo pipefail

NAVI="tmap"
TARGET_PKG="${APP_PACKAGE:-me.zipi.navitotesla.ns.debug}"
DESTS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --navi)
            NAVI="$2"
            shift 2
            ;;
        --target)
            TARGET_PKG="$2"
            shift 2
            ;;
        -h|--help)
            sed -n '2,/^$/p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        --)
            shift
            DESTS+=("$@")
            break
            ;;
        -*)
            echo "알 수 없는 옵션: $1" >&2
            exit 1
            ;;
        *)
            DESTS+=("$1")
            shift
            ;;
    esac
done

if [[ ${#DESTS[@]} -eq 0 ]]; then
    echo "목적지를 하나 이상 지정." >&2
    echo "예: $0 보정로 코엑스" >&2
    exit 1
fi

case "$NAVI" in
    kakao)
        NAVI_PKG="com.locnall.KimGiSa"
        TITLE="길안내 주행 중"
        TEXT_PREFIX="목적지 : "
        ;;
    tmap)
        NAVI_PKG="com.skt.tmap.ku"
        TITLE="경로주행"
        TEXT_PREFIX=""
        ;;
    *)
        echo "지원하지 않는 navi: $NAVI (kakao|tmap)" >&2
        exit 1
        ;;
esac

for DEST in "${DESTS[@]}"; do
    TEXT="${TEXT_PREFIX}${DEST}"
    echo ">> $NAVI -> $TARGET_PKG  [$TITLE] '$TEXT'"
    adb shell am broadcast \
        -a me.zipi.navitotesla.DEBUG_TRIGGER \
        --es pkg "$NAVI_PKG" \
        --es title "$TITLE" \
        --es text "$TEXT" \
        -p "$TARGET_PKG"
done
