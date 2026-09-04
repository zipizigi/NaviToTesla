package me.zipi.navitotesla.service.poifinder

/** 전송을 건너뛴 사유. 무성 실패의 원인을 로그·이벤트에서 구분하기 위함. */
enum class IgnoreReason {
    /** 알림 제목이 안전운전/안심주행 — 목적지 없는 주행 모드. */
    SAFE_TITLE,

    /** 알림 제목이 길안내 화이트리스트 밖. */
    TITLE_MISMATCH,

    /** 알림 본문이 안내 시작 문구가 아님. */
    TEXT_MISMATCH,

    /** 접근성으로 캡처한 목적지가 없음. */
    NO_CAPTURE,

    /** 캡처한 목적지가 너무 오래됨. */
    TTL_EXPIRED,

    /** 화면 판정 결과가 안전운전. */
    SAFE_DRIVE,

    /** 목적지 문자열을 뽑지 못함. */
    EMPTY_DESTINATION,
}
