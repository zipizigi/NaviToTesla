package me.zipi.navitotesla.service.poifinder

/** 전송을 건너뛴 사유. 무성 실패 원인을 로그·이벤트에서 구분하기 위함. */
enum class IgnoreReason {
    SAFE_TITLE,
    TITLE_MISMATCH,
    TEXT_MISMATCH,
    NO_CAPTURE,
    TTL_EXPIRED,
    SAFE_DRIVE,
    EMPTY_DESTINATION,
}
