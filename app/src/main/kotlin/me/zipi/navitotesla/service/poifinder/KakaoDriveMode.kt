package me.zipi.navitotesla.service.poifinder

/**
 * 카카오내비 주행 화면의 모드. 알림 제목은 두 모드 모두 `길안내 주행 중` 이 뜨므로
 * 화면을 봐야 구분된다.
 */
enum class KakaoDriveMode {
    GUIDANCE,
    SAFE_DRIVE,
    UNKNOWN,
}
