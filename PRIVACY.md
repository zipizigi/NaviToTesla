# 개인정보 수집 및 처리에 관한 안내
Navi to Tesla 앱에서는 아래와 같은 정보를 수집하고 처리하고 있습니다.

## 수집 정보
- Tesla 계정의 Refresh Token 및 Access Token, 차량 이름(Tesla API를 이용할경우)
- 사용자가 방문하고자 하는 목적지 주소
- 사용중에 발생하는 오류 로그, 기기명, 성능 측정 정보

## 수집 목적 및 보관 방법
Navi to Tesla는 Tesla 차량의 내비게이션 연동 목적으로 사용자가 방문하고자 하는 목적지 주소를 수집합니다.  
목적지 주소는 Tesla API 또는 Tesla 앱을 통해 차량으로 전송됩니다.  

내비게이션에서 얻은 목적지명만으로는 차량에 보낼 주소와 좌표를 알 수 없어, 아래 지도 서비스에 주소를 조회합니다.  
조회한 값은 주소·좌표 확인 목적에만 사용합니다.

- **카카오 로컬 검색 API** — 카카오내비 목적지의 주소·좌표 조회
- **네이버 지도·검색 API** — 네이버지도 목적지의 주소·좌표 조회
- **티맵 POI API** — 티맵 목적지의 주소·좌표 조회
- **Google Places API** — 조회된 주소를 차량 내비게이션에서 검색할 수 있는지 확인

Tesla API를 이용할 경우 Tesla의 Refresh Token과 Access Token을 이용합니다.  
해당 값은 매우 중요한 값이며 Tesla API 호출에만 이용하며 그외에는 사용하지 않습니다.  
해당 값은 절대 외부로 전송하지 않으며, 사용자 기기 내에 안전하게 암호화하여 저장합니다.  
  
앱 품질 향상을 위해 사용도중에 오류가 발생할 경우 오류가 발생할 당시의 로그, 기기명, 오류 발생 시간, 안드로이드 OS 정보 등을 수집합니다.  
해당 정보는 개인을 식별할 수 있는 중요한 정보가 포함되어 있지 않으며 앱 품질 향상과 오류 수정을 위해 사용합니다.  

## 접근성 서비스를 통한 목적지 수집
네이버지도와 카카오내비는 알림이나 다른 방법으로 목적지를 확인할 수 없어, Android 접근성 서비스(AccessibilityService)로 화면에서 목적지를 읽습니다.  
자세한 내용은 [접근성 기능 사용 안내](ACCESSIBILITY.md)를 참고해 주세요.

- **대상**: 네이버지도(`com.nhn.android.nmap`), 카카오내비(`com.locnall.KimGiSa`) 두 앱의 화면에서만 동작하며 다른 앱은 읽지 않습니다.
- **읽는 항목**: 목적지와 주행 모드 판별에 필요한 항목뿐입니다.
- **시점**: 해당 내비게이션의 경로 안내가 시작될 때 읽습니다. 
- **사용**: 읽은 목적지는 위 주소 조회를 거쳐 주소·좌표로 변환된 뒤 사용자 본인 소유의 Tesla 차량으로 전송됩니다.
- **동의와 해제**: 앱 설정에서 수집 내용을 확인하고 활성화해야 동작합니다. Android 설정에서 언제든 끌 수 있고, 껐을 때도 다른 내비게이션은 정상 동작합니다.

### 저장과 삭제
목적지명, 변환된 주소, 좌표는 사용자 기기 내부에 캐시로 저장되며 일정 기간이 지나면 자동 삭제됩니다.  
데이터 삭제 요청은 [계정 및 데이터 삭제 안내](DELETE.md)를 참고해 주세요.



# Information on collection and processing of personal information

The Navi to Tesla app collects and processes the following information.

## Collect information
- Vehicle name, Refresh Token and Access Token for Tesla account.(when using Tesla API)  
- Error log, device name, and performance measurement information that occur error during use
- Destination address the user wants to visit  

## Purpose of collection and storage method
Navi to Tesla collects the destination address you want to visit for the purpose of linking the navigation of your Tesla vehicle.  
The destination address is sent to the vehicle via the Tesla API or Tesla app.

A destination name from a navigation app is not enough to determine the address and coordinates to send to the vehicle, so the address is looked up against the services below.  
Values used for these lookups serve only to determine the address and coordinates.

- **Kakao Local Search API** — resolves the address and coordinates of a Kakao Navi destination
- **Naver Map / Search API** — resolves the address and coordinates of a NAVER Maps destination
- **TMAP POI API** — resolves the address and coordinates of a TMAP destination
- **Google Places API** — checks whether the resolved address is searchable in the vehicle navigation
  
When using the Tesla API, Tesla's Refresh Token and Access Token are used.  
This is a very important value and is only used for Tesla API calls, nothing else.  
The value is never transmitted to the outside, and is securely encrypted and stored within the user's device.  

In order to improve the quality of the app, if an error occurs during use, we collect the log, device name, error time, and Android OS information at the time the error occurred.  
This information does not contain any personally identifiable information and is used to improve the quality of the app and correct errors.

## Destination collection through the accessibility service
NAVER Maps and Kakao Navi provide no notification or other means of obtaining the destination, so Navi to Tesla reads the destination from the screen using the Android AccessibilityService.  
See the [Accessibility Service Notice](ACCESSIBILITY.md) for details.

- **Scope**: Works only on the screens of NAVER Maps (`com.nhn.android.nmap`) and Kakao Navi (`com.locnall.KimGiSa`). Screens of other apps are not read.
- **What is read**: Only the destination and the items needed to tell the driving mode apart.
- **When**: At the moment route guidance starts in those apps.
- **Use**: The destination goes through the address lookup described above, then is sent to the Tesla vehicle owned by the user.
- **Consent and opt-out**: The feature works only after the user reviews the disclosure in app settings and enables it. It can be turned off at any time in Android settings, and other navigation apps keep working when it is off.

### Storage and deletion
The destination name, resolved address, and coordinates are cached on the user's device and removed automatically after a certain period.  
For deletion requests, see the [Account and Data Deletion Notice](DELETE.md).
