package me.zipi.navitotesla.model

import java.util.Locale

data class SendSettings(
    val defaultMode: SendMode,
    val fallbackMode: SendMode,
    val treatUnknownAsNotSearchable: Boolean,
    /**
     * 발송 transport. ByApi (owner-api `share`) 는 ShareRequest.locale="ko-KR" 을 보내서 한국어
     * 컨텍스트가 명시되지만, ByApp (Tesla Android app share-target) 는 호스트 기기의 locale 이
     * 그대로 전달되어 비한국어 locale 일 때 raw 한국어 주소를 잘못 파싱한다.
     * SendPlanner 는 이 정보를 받아 ByApp + 비한국어 케이스에만 URL wrap 을 강제한다.
     */
    val shareTransport: ShareTransport,
    /** `Locale.getDefault()` 를 주입. 테스트 격리용. */
    val locale: Locale,
)

enum class ShareTransport { APP, API }
