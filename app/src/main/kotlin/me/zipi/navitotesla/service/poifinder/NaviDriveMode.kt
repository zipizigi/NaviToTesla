package me.zipi.navitotesla.service.poifinder

/**
 * 주행 화면의 모드. 카카오·네이버 모두 안전운전 모드에서도 길안내와 같은 알림을 띄우므로
 * 알림만으로는 구분되지 않고 화면을 봐야 한다.
 */
enum class NaviDriveMode {
    GUIDANCE,
    SAFE_DRIVE,
    UNKNOWN,
}
