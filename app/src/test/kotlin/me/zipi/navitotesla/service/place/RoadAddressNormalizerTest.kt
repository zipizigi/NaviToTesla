package me.zipi.navitotesla.service.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 입력 문자열은 Kakao/TMAP/Naver API 실제 응답 실측값. */
class RoadAddressNormalizerTest {
    private fun norm(s: String) = RoadAddressNormalizer.normalize(s)

    private fun assertConverge(
        expected: String,
        vararg observed: String,
    ) = observed.forEach { assertEquals("입력: $it", expected, norm(it)) }

    // === 3사 수렴 (실측 조합) ===

    @Test
    fun `일반 시도 - 코엑스`() =
        assertConverge(
            "서울 강남구 영동대로 513",
            "서울 강남구 영동대로 513",
            "서울특별시 강남구 영동대로 513",
            "서울특별시 강남구 영동대로 513 지하1층 O-107호 라운지",
        )

    @Test
    fun `특별자치시 - 정부세종청사`() =
        assertConverge(
            "세종 다솜2로 94",
            "세종특별자치시 다솜2로 94",
            "세종시 다솜2로 94",
        )

    @Test
    fun `특별자치도 - 강릉역`() =
        assertConverge(
            "강원 강릉시 용지로 176",
            "강원특별자치도 강릉시 용지로 176",
            "강원 강릉시 용지로 176",
            "강원도 강릉시 용지로 176",
        )

    @Test
    fun `특별자치도 - 전주한옥마을`() =
        assertConverge(
            "전북 전주시 완산구 기린대로 99",
            "전북 전주시 완산구 기린대로 99",
            "전북특별자치도 전주시 완산구 기린대로 99",
        )

    @Test
    fun `통합시 - 목포역`() =
        assertConverge(
            "전남광주 목포시 영산로 98",
            "전남광주통합특별시 목포시 영산로 98",
            "전남광주 목포시 영산로 98",
        )

    @Test
    fun `읍 토큰 - 포항역`() =
        assertConverge(
            "경북 포항시 북구 포항역로 1",
            "경북 포항시 북구 흥해읍 포항역로 1",
            "경북 포항시 북구 포항역로 1",
            "경상북도 포항시 북구 흥해읍 포항역로 1",
        )

    @Test
    fun `면 토큰 - 행담도휴게소`() =
        assertConverge(
            "충남 당진시 서해안고속도로 275",
            "충남 당진시 신평면 서해안고속도로 275",
            "충남 당진시 서해안고속도로 275",
            "충청남도 당진시 신평면 서해안고속도로 275",
        )

    @Test
    fun `읍 토큰과 부번 - 여수해상케이블카`() =
        assertConverge(
            "전남광주 여수시 돌산로 3600-1",
            "전남광주통합특별시 여수시 돌산읍 돌산로 3600-1",
            "전남광주 여수시 돌산로 3600-1",
        )

    @Test
    fun `지하 건물번호 - 고속터미널역`() =
        assertConverge(
            "서울 서초구 신반포로 188",
            "서울 서초구 신반포로 지하 188",
            "서울 서초구 신반포로 188",
        )

    @Test
    fun `괄호 부가정보 - 국립아시아문화전당`() =
        assertConverge(
            "전남광주 동구 문화전당로 38",
            "전남광주통합특별시 동구 문화전당로 38",
            "전남광주통합특별시 동구 문화전당로 38 (광산동, 아시아문화전당)",
        )

    @Test
    fun `읍 토큰과 상세주소 동시 - 청주국제공항`() =
        assertConverge(
            "충북 청주시 청원구 오창대로 980",
            "충북 청주시 청원구 내수읍 오창대로 980",
            "충북 청주시 청원구 오창대로 980",
            "충청북도 청주시 청원구 내수읍 오창대로 980 5-4",
        )

    @Test
    fun `번길 도로명 - 판교`() =
        assertConverge(
            "경기 성남시 분당구 판교로289번길 20",
            "경기 성남시 분당구 판교로289번길 20",
        )

    // === Naver 상세주소 꼬리 절단 ===

    @Test
    fun `건물명 꼬리 제거`() {
        assertEquals("서울 영등포구 여의대로 108", norm("서울특별시 영등포구 여의대로 108 더현대 서울"))
        assertEquals("경기 하남시 미사대로 750", norm("경기도 하남시 미사대로 750 스타필드 하남"))
    }

    @Test
    fun `층 호 꼬리 제거`() {
        assertEquals("서울 영등포구 영중로 15", norm("서울특별시 영등포구 영중로 15 영등포 타임스퀘어 4층 409호"))
        assertEquals("서울 중구 남대문로 81", norm("서울특별시 중구 남대문로 81 B1층"))
        assertEquals("서울 강남구 테헤란로 521", norm("서울특별시 강남구 테헤란로 521 파르나스몰 지하1층"))
    }

    // === 지번주소 (실측) ===

    @Test
    fun `지번 0 번지 제거 - 대부도`() =
        assertConverge(
            "경기 안산시 단원구 대부동동",
            "경기 안산시 단원구 대부동동",
            "경기 안산시 단원구 대부동동 0",
            "경기도 안산시 단원구 대부동동",
        )

    @Test
    fun `지번 0 번지 제거 - 백령도`() =
        assertConverge(
            "인천 옹진군 백령면 북포리",
            "인천 옹진군 백령면 북포리",
            "인천 옹진군 백령면 북포리 0",
            "인천광역시 옹진군 백령면 북포리",
        )

    @Test
    fun `산 번지 띄어쓰기 통일 - 무등산`() =
        assertConverge(
            "전남광주 북구 금곡동 산 1-1",
            "전남광주통합특별시 북구 금곡동 산 1-1",
            "전남광주통합특별시 북구 금곡동 산1-1",
        )

    @Test
    fun `지번은 읍 면 토큰을 유지`() =
        assertConverge(
            "경북 포항시 북구 흥해읍 이인리 137-1",
            "경북 포항시 북구 흥해읍 이인리 137-1",
            "경상북도 포항시 북구 흥해읍 이인리 137-1",
        )

    @Test
    fun `번지 없는 지번은 그대로`() {
        assertEquals("부산 해운대구 우동", norm("부산광역시 해운대구 우동"))
        assertEquals("서울 성동구 성수동2가", norm("서울특별시 성동구 성수동2가"))
    }

    @Test
    fun `산으로 시작하는 동리 이름은 분리하지 않음`() {
        assertEquals("경기 군포시 산본동 1", norm("경기도 군포시 산본동 1"))
        assertEquals("경기 안성시 원곡면 산하리 산 67-3", norm("경기 안성시 원곡면 산하리 산 67-3"))
    }

    @Test
    fun `단독 0 은 제거하지 않음`() {
        assertEquals("0", norm("0"))
    }

    @Test
    fun `지번 번지 뒤 상세주소 절단 - 시청`() =
        assertConverge(
            "서울 중구 태평로1가 31",
            "서울 중구 태평로1가 31",
            "서울특별시 중구 태평로1가 31 서울특별시청",
        )

    @Test
    fun `지번 번지 뒤 상세주소 절단 - 상가`() =
        assertConverge(
            "경기 수원시 권선구 서둔동 381",
            "경기 수원시 권선구 서둔동 381",
            "경기도 수원시 권선구 서둔동 381 타임빌라스 수원 1F",
        )

    @Test
    fun `지번 부번 뒤 층 정보 절단`() =
        assertConverge(
            "서울 성동구 성수동2가 339-136",
            "서울 성동구 성수동2가 339-136",
            "서울특별시 성동구 성수동2가 339-136 1층",
        )

    @Test
    fun `숫자를 포함한 동 이름은 번지로 오인하지 않음`() {
        assertEquals("서울 성동구 성수동2가 339", norm("서울특별시 성동구 성수동2가 339 상세"))
        assertEquals("제주 제주시 이도이동 1176-1", norm("제주특별자치도 제주시 이도이동 1176-1 제주시청"))
    }

    @Test
    fun `산 번지 뒤 상세주소 절단`() =
        assertConverge(
            "전남광주 북구 금곡동 산 1-1",
            "전남광주통합특별시 북구 금곡동 산1-1 무등산",
            "전남광주통합특별시 북구 금곡동 산 1-1",
        )

    // === 도로명 미확인 시 보존 (지번/좌표 보호) ===

    @Test
    fun `지번주소는 읍 토큰을 유지하고 절단하지 않음`() {
        val jibun = "제주 서귀포시 대정읍 마라리 100"
        assertEquals(jibun, norm(jibun))
    }

    @Test
    fun `지번주소 시도 표기만 정규화`() {
        assertEquals("제주 서귀포시 대정읍 마라리 100", norm("제주특별자치도 서귀포시 대정읍 마라리 100"))
    }

    @Test
    fun `좌표 문자열은 불변`() {
        assertEquals("37.566645,126.978256", norm("37.566645,126.978256"))
    }

    @Test
    fun `건물번호가 없으면 절단하지 않음`() {
        assertEquals("서울 강남구 테헤란로", norm("서울특별시 강남구 테헤란로"))
    }

    @Test
    fun `건물번호 자리가 숫자가 아니면 절단하지 않음`() {
        assertEquals("서울 강남구 테헤란로 일대", norm("서울특별시 강남구 테헤란로 일대"))
    }

    @Test
    fun `알 수 없는 첫 토큰은 보존`() {
        assertEquals("Seoul 강남구 영동대로 513", norm("Seoul 강남구 영동대로 513"))
    }

    // === 경계값 ===

    @Test
    fun `빈 문자열과 공백만 있는 입력`() {
        assertEquals("", norm(""))
        assertEquals("", norm("   "))
    }

    @Test
    fun `연속 공백 정리`() {
        assertEquals("서울 강남구 영동대로 513", norm("  서울특별시   강남구  영동대로   513  "))
    }

    @Test
    fun `시군구 없이 도로명으로 시작하는 입력`() {
        assertEquals("영동대로 513", norm("영동대로 513"))
    }

    @Test
    fun `이미 정규화된 입력은 멱등`() {
        val canonical = "서울 강남구 영동대로 513"
        assertEquals(canonical, norm(canonical))
        assertEquals(canonical, norm(norm(canonical)))
    }

    // === 지하 건물번호 (지하철역 실측) ===

    @Test
    fun `도로명과 번호 사이 지하 제거`() {
        assertEquals("경기 성남시 분당구 성남대로 491", norm("경기 성남시 분당구 성남대로 지하 491"))
        assertEquals("서울 중구 을지로 42", norm("서울 중구 을지로 지하 42"))
        assertEquals("서울 종로구 종로 55", norm("서울 종로구 종로 지하 55"))
        assertEquals("대구 중구 달구벌대로 2100", norm("대구 중구 달구벌대로 지하 2100"))
    }

    @Test
    fun `지하 건물번호에 부번이 붙은 경우`() =
        assertConverge(
            "서울 구로구 새말로 117-21",
            "서울 구로구 새말로 지하 117-21",
            "서울특별시 구로구 새말로 117-21",
        )

    @Test
    fun `지하 와 상세주소 꼬리가 동시에 있는 경우`() =
        assertConverge(
            "서울 강남구 강남대로 396",
            "서울 강남구 강남대로 지하 396",
            "서울특별시 강남구 강남대로 지하 396 강남역지하쇼핑센터",
            "서울특별시 강남구 강남대로 396 강남역 지하1층",
        )

    @Test
    fun `번호 뒤 지하층 표기는 꼬리로 절단`() {
        assertEquals("서울 중구 청계천로 100", norm("서울특별시 중구 청계천로 100 지하1층"))
        assertEquals("경기 성남시 분당구 성남대로 601", norm("경기도 성남시 분당구 성남대로 601 AK PLAZA 분당점 지하 내"))
        assertEquals("서울 중구 수표동 99", norm("서울특별시 중구 수표동 99 지하1층"))
    }

    // === 도로명주소 규격 표기 (juso.go.kr 기준) ===

    @Test
    fun `상세주소는 쉼표로 구분되는 규격 표기도 절단`() =
        assertConverge(
            "서울 중구 세종대로 110",
            "서울 중구 세종대로 110",
            "서울특별시 중구 세종대로 110, 3층",
            "서울특별시 중구 세종대로 110,3층",
        )

    @Test
    fun `공동주택 동 호 상세주소 절단`() =
        assertConverge(
            "서울 서초구 신반포로 270",
            "서울 서초구 신반포로 270",
            "서울특별시 서초구 신반포로 270, 101동 101호",
            "서울특별시 서초구 신반포로 270, 101동 101호 (반포동, 반포자이아파트)",
        )

    @Test
    fun `참고항목 괄호는 법정동과 건물명 모두 제거`() {
        assertEquals("서울 중구 세종대로 110", norm("서울특별시 중구 세종대로 110 (태평로1가)"))
        assertEquals("경기 의정부시 청사로 1", norm("경기도 의정부시 청사로 1(신곡동)"))
    }

    @Test
    fun `규격 위반으로 동이 끼어든 경우 제거`() =
        assertConverge(
            "서울 영등포구 여의대로 108",
            "서울 영등포구 여의대로 108",
            "서울특별시 영등포구 여의도동 여의대로 108",
        )

    @Test
    fun `읍 면 은 시군구 단위 고유성에 영향 없어 제거`() =
        assertConverge(
            "경기 안성시 경부고속도로 372",
            "경기 안성시 경부고속도로 372",
            "경기 안성시 원곡면 경부고속도로 372",
        )

    @Test
    fun `서로 다른 시군구의 동일 도로명 건물번호는 구분 유지`() {
        // 강남구 도곡로 434 와 송파구 도곡로 434 는 모두 실존하는 다른 주소.
        assertEquals("서울 강남구 도곡로 434", norm("서울특별시 강남구 도곡로 434"))
        assertEquals("서울 송파구 도곡로 434", norm("서울특별시 송파구 도곡로 434"))
    }

    @Test
    fun `좌표는 쉼표를 구분자로 유지`() {
        assertEquals("37.5,127.0", norm("37.5,127.0"))
        assertEquals("37.5, 127.0", norm("37.5, 127.0"))
        assertEquals("-37.5,-127.0", norm("-37.5,-127.0"))
    }

    // === isSpecific (건물번호·번지 유무) ===

    @Test
    fun `건물번호나 번지가 있으면 specific`() {
        listOf(
            "서울 강남구 영동대로 513",
            "경기 가평군 문화로 13-42",
            "서울 중구 태평로1가 31",
            "전남광주 북구 금곡동 산 1-1",
            "경기 성남시 분당구 성남대로 491",
        ).forEach { assertTrue(it, RoadAddressNormalizer.isSpecific(it)) }
    }

    @Test
    fun `시군구 동 단위 주소는 specific 아님`() {
        listOf(
            "경기 여주시",
            "부산 해운대구 우동",
            "경기 안산시 단원구 대부동동",
            "서울 강남구 테헤란로",
            "서울 강남구 테헤란로 일대",
            "37.566645,126.978256",
            "",
        ).forEach { assertFalse(it, RoadAddressNormalizer.isSpecific(it)) }
    }

    @Test
    fun `정규화 결과를 그대로 isSpecific 에 넣어 판정`() {
        assertTrue(RoadAddressNormalizer.isSpecific(norm("서울특별시 강남구 영동대로 513 지하1층 O-107호")))
        assertFalse(RoadAddressNormalizer.isSpecific(norm("경기도 여주시")))
        assertFalse(RoadAddressNormalizer.isSpecific(norm("경기 안산시 단원구 대부동동 0")))
    }

    // === 시도 표기 테이블 ===

    /** 테이블에 항목이 추가되면 자동으로 검증 대상에 포함됨. */
    @Test
    fun `테이블의 모든 시도 변형이 canonical 로 수렴`() {
        RoadAddressNormalizer.SIDO_CANONICAL.forEach { (variant, canonical) ->
            assertEquals(
                "변형: $variant",
                "$canonical 중구 세종대로 110",
                norm("$variant 중구 세종대로 110"),
            )
        }
    }

    @Test
    fun `광역시 시 단축 표기`() {
        assertEquals("서울 강남구 영동대로 513", norm("서울시 강남구 영동대로 513"))
        assertEquals("부산 동구 중앙대로 206", norm("부산시 동구 중앙대로 206"))
        assertEquals("대구 동구 동대구로 550", norm("대구시 동구 동대구로 550"))
        assertEquals("인천 영종구 공항로 272", norm("인천시 영종구 공항로 272"))
        assertEquals("대전 서구 둔산로 100", norm("대전시 서구 둔산로 100"))
        assertEquals("울산 남구 대공원로 94", norm("울산시 남구 대공원로 94"))
    }

    /** "광주시" 는 경기도 광주시와 구분되지 않아 시도 alias 로 두지 않음. */
    @Test
    fun `광주시는 시도로 치환하지 않음`() {
        assertEquals("광주시 동구 문화전당로 38", norm("광주시 동구 문화전당로 38"))
        assertEquals("광주시 경안로 10", norm("광주시 경안로 10"))
        assertEquals("경기 광주시 경안로 10", norm("경기도 광주시 경안로 10"))
    }

    @Test
    fun `도 단축 표기`() {
        assertEquals("경기 수원시 팔달구 정조로 825", norm("경기도 수원시 팔달구 정조로 825"))
        assertEquals("충북 충주시 충원대로 539", norm("충청북도 충주시 충원대로 539"))
        assertEquals("경남 통영시 발개로 205", norm("경상남도 통영시 발개로 205"))
        assertEquals("제주 제주시 공항로 2", norm("제주도 제주시 공항로 2"))
    }

    @Test
    fun `세종은 시군구 없이도 수렴`() =
        assertConverge(
            "세종 다솜2로 94",
            "세종 다솜2로 94",
            "세종시 다솜2로 94",
            "세종특별자치시 다솜2로 94",
        )

    /** Places 응답·레거시 키에 구 표기가 남아 있어 통합 canonical 로 모은다. */
    @Test
    fun `통합 이전 광주 전남 표기도 통합 canonical 로 수렴`() =
        assertConverge(
            "전남광주 동구 문화전당로 38",
            "전남광주 동구 문화전당로 38",
            "전남광주통합특별시 동구 문화전당로 38",
            "광주 동구 문화전당로 38",
            "광주광역시 동구 문화전당로 38",
        ).also {
            assertConverge(
                "전남광주 목포시 영산로 98",
                "전남광주통합특별시 목포시 영산로 98",
                "전남 목포시 영산로 98",
                "전라남도 목포시 영산로 98",
            )
        }

    // === 한국 주소 어순이 아닌 입력 방어 ===

    /** Places 응답이 도로명부터 시작하는 형식이면 절단 시 시군구가 사라져 충돌 키가 된다. */
    @Test
    fun `도로명이 첫 토큰이면 절단하지 않음`() {
        assertEquals("충원대로 539 충주시 충청북도 대한민국", norm("충원대로 539, 충주시, 충청북도, 대한민국"))
        assertEquals("영동대로 513", norm("영동대로 513"))
        assertEquals("중앙로 1", norm("중앙로 1"))
    }

    @Test
    fun `번지가 첫 토큰이면 절단하지 않음`() {
        assertEquals("108 Yeouidae-ro Yeongdeungpo-gu Seoul", norm("108 Yeouidae-ro, Yeongdeungpo-gu, Seoul"))
        assertEquals("31 태평로1가 중구 서울특별시", norm("31, 태평로1가, 중구, 서울특별시"))
    }

    // === trimDetail (전송용: 원문 표기 유지, 꼬리만 절단) ===

    @Test
    fun `trimDetail 은 상세주소만 자르고 시도 표기를 유지`() {
        assertEquals(
            "서울특별시 강남구 영동대로 513",
            RoadAddressNormalizer.trimDetail("서울특별시 강남구 영동대로 513 지하1층 O-107호 라운지"),
        )
        assertEquals("서울특별시 중구 세종대로 110", RoadAddressNormalizer.trimDetail("서울특별시 중구 세종대로 110"))
        assertEquals(
            "서울특별시 서초구 신반포로 270",
            RoadAddressNormalizer.trimDetail("서울특별시 서초구 신반포로 270, 101동 101호 (반포동, 반포자이아파트)"),
        )
    }

    @Test
    fun `trimDetail 은 읍면과 지하를 유지`() {
        assertEquals(
            "경북 포항시 북구 흥해읍 포항역로 1",
            RoadAddressNormalizer.trimDetail("경북 포항시 북구 흥해읍 포항역로 1"),
        )
        assertEquals(
            "경기 성남시 분당구 성남대로 지하 491",
            RoadAddressNormalizer.trimDetail("경기 성남시 분당구 성남대로 지하 491 수내역"),
        )
    }

    @Test
    fun `trimDetail 은 지번 번지 뒤를 자름`() {
        assertEquals(
            "서울특별시 중구 태평로1가 31",
            RoadAddressNormalizer.trimDetail("서울특별시 중구 태평로1가 31 서울특별시청"),
        )
    }

    @Test
    fun `trimDetail 은 도로명 뒤가 번호가 아니어도 번지까지 절단`() {
        // "언주로 30길 21" 처럼 도로명 토큰 뒤가 번호가 아닌 경우 normalize 와 결과가 갈리면 안 됨.
        assertEquals("서울 강남구 언주로 30길 21", RoadAddressNormalizer.trimDetail("서울 강남구 언주로 30길 21 3층"))
        assertEquals("서울 강남구 언주로 30길 21", norm("서울 강남구 언주로 30길 21 3층"))
    }

    @Test
    fun `trimDetail 은 산 번지와 0 번지를 normalize 와 동일하게 처리`() {
        assertEquals(
            "전남광주 북구 금곡동 산 1-1",
            RoadAddressNormalizer.trimDetail("전남광주 북구 금곡동 산1-1 무등산"),
        )
        assertEquals(
            "경기 안산시 단원구 대부동동",
            RoadAddressNormalizer.trimDetail("경기 안산시 단원구 대부동동 0"),
        )
    }

    @Test
    fun `trimDetail 은 좌표와 불완전 주소를 그대로 둠`() {
        assertEquals("37.5,127.0", RoadAddressNormalizer.trimDetail("37.5,127.0"))
        assertEquals("경기도 여주시", RoadAddressNormalizer.trimDetail("경기도 여주시"))
        assertEquals("영동대로 513", RoadAddressNormalizer.trimDetail("영동대로 513"))
    }

    /** 시도 위치가 아닌 "OO시" 는 시군구 이름이므로 치환 대상이 아님 (경기 광주시, 제주 제주시). */
    @Test
    fun `시군구 위치의 시 이름은 치환하지 않음`() {
        assertEquals("경기 광주시 경안로 10", norm("경기도 광주시 경안로 10"))
        assertEquals("제주 제주시 공항로 2", norm("제주특별자치도 제주시 공항로 2"))
        assertEquals("경기 성남시 분당구 판교역로 235", norm("경기도 성남시 분당구 판교역로 235"))
    }
}
