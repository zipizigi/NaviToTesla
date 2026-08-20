# 접근성 기능 사용 안내

Navi to Tesla 는 특정 내비게이션의 목적지를 가져오기 위해 Android 접근성(AccessibilityService) 기능을 사용합니다.

개인정보 처리 전반은 [개인정보 처리방침](PRIVACY.md)을 참고해 주세요.

## 접근 대상 앱

해당 앱은 접근성 기능을 통하지 않고 목적지를 가져올 방법이 없습니다.

- 네이버지도 (`com.nhn.android.nmap`)
- 카카오내비 (`com.locnall.KimGiSa`)

## 수집 정보

- 주행 목적지
- 현재 주행 안내 중인지 여부

## 수집 시점

- 내비게이션이 목적지로 안내를 시작할 때

## 화면을 읽는 범위

- 다른 앱의 화면은 읽지 않습니다.
- 목적지와 주행 모드 판별에 필요한 항목만 읽습니다.
- 목적지 화면과 주행 화면을 함께 확인하는 이유
  - 사용자 터치 없이 자동으로 시작되는 경로 안내 구분
  - 목적지 없이 주행만 하는 모드(안전운전·안심주행)에서 오동작 방지

## 사용 목적

- 고객 본인 소유 Tesla 차량으로 목적지 전송
- 빠른 응답을 위해 변환된 주소·좌표를 기기 내부에 임시 캐시

## 저장 정보 (기기 내부)

- 저장 대상: 목적지명, 변환된 주소, 좌표
- 저장 위치: 사용자 기기 내부
- 자동 정리: 일정 기간이 지난 캐시는 자동 삭제됩니다.

## 외부 공유 범위

- 목적지를 주소·좌표로 변환하기 위해 카카오 로컬 검색, 네이버 지도·검색 API를 호출합니다.
- 변환된 주소를 차량 내비게이션에서 검색할 수 있는지 확인하기 위해 Google Places API를 호출합니다.
- 변환된 목적지는 고객 본인 소유의 Tesla 차량으로 전송합니다. 사용자가 등록한 본인 차량에만 전송됩니다.
- 그 외 어떤 서버나 제3자에도 전송·공유하지 않습니다.

## 비활성화 방법

- Android 설정 → 접근성 → Navi to Tesla 에서 언제든 끌 수 있습니다.
- 앱 내 설정 탭의 접근성 서비스 라디오 버튼을 비활성화 할 수 있습니다.

## 비활성화 시 영향

- 위에 명시한 내비게이션 연동을 사용할 수 없습니다.
- 다른 내비게이션은 정상 동작합니다.

## 사용자 동의

- 앱 설정에서 접근성 기능 활성화 시도 시, 위 내용을 명시한 동의 다이얼로그가 표시됩니다.
- 앱에서 접근성 기능을 활성화하려면 안내 내용을 확인하고 허용해야 합니다.



# Accessibility Service Notice

Navi to Tesla uses the Android AccessibilityService to obtain the destination from certain navigation apps.

For overall data handling, see the [Privacy Policy](PRIVACY.md).

## Apps accessed

These apps offer no way to obtain the destination without the accessibility service.

- NAVER Maps (`com.nhn.android.nmap`)
- Kakao Navi (`com.locnall.KimGiSa`)

## Information collected

- The driving destination
- Whether route guidance is currently active

## When it is collected

- When the navigation app starts guiding to a destination

## Scope of screen reading

- Screens of other apps are not read.
- Reads only the destination and the items needed to tell the driving mode apart.
- Why both the destination screen and the driving screen are checked
  - Identifying route guidance that starts without any user touch
  - Preventing wrong sends in a drive-only mode with no destination (safe-driving mode)

## Purpose

- Sending the destination to the Tesla vehicle owned by the user
- Caching the resolved address and coordinates on the device for a faster response

## Stored information (on the device)

- Stored items: destination name, resolved address, coordinates
- Location: on the user's device
- Cleanup: cached entries are removed automatically after a certain period

## External sharing

- The Kakao Local Search and Naver Map/Search APIs are called to resolve the destination into an address and coordinates.
- The Google Places API is called to check whether the resolved address is searchable in the vehicle navigation.
- The resolved destination is sent to the Tesla vehicle owned by the user, and only to the vehicle the user has registered.
- The destination is not sent or shared with any other server or third party.

## How to turn it off

- Android Settings → Accessibility → Navi to Tesla, at any time.
- Or the accessibility radio button on the Settings tab in the app.

## Effect of turning it off

- Integration with the navigation apps listed above stops working.
- Other navigation apps keep working as usual.

## User consent

- Attempting to enable accessibility from the app's settings shows a consent dialog stating the above.
- Enabling accessibility from the app requires reviewing the notice and allowing it.

