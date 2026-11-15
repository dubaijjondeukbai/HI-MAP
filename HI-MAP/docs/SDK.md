# HI-MAP — Pleos Connect SDK·API 사용

순정 내비의 주행 이벤트는 NaviHelper로, 차량 위치는 FusedLocation으로, 주행·정차 상태는 AAOS CarUxRestrictionsManager로 받습니다. 접수양식 5번(개발 계획)의 표와 같은 내용입니다.

## 쓰는 SDK와 API

| SDK·버전 | 쓰는 API | 연결되는 기능 | 권한 | 코드 | 상태 |
|---|---|---|---|---|---|
| NaviHelper 3.0.5 | `NaviHelper(context)`, `initialize(onConnected, onFailed)`, `addListener()`, `removeListener()`, `release()`. 콜백 `onRouteStarted(RouteStartInfo)`, `onRoutingCompleted()`, `onReRouteEnded()`, `onDestinationArrived(DestinationArrivedInfo)`, `onTripInfo(TripInfo)`, `onError(NaviErrorCode)` | 순정 내비의 경로 시작·변경·도착을 받습니다. 남은 시간(`onTripInfo`)은 지금 기록만 하고 표시는 다음 단계입니다 | `NAVI_ROUTE` | [PleosNaviBridge.kt](../app/src/pleos/java/com/mydrawingmap/app/vehicle/PleosNaviBridge.kt) | 소스 작성. 에뮬레이터 검증 전 |
| NaviHelper 경로 요청 | `requestRoute(RouteInfo(latitude, longitude, poiId, poiName, poiSubId, address))`, 경로 옵션 `RouteOption.COMFORTABLE`(옵션을 넣는 인자 이름은 확인 중) | 자주 가는 곳을 순정 내비의 목적지로 넣습니다 | `NAVI_ROUTE_SEARCH` | [PleosNaviBridge.kt](../app/src/pleos/java/com/mydrawingmap/app/vehicle/PleosNaviBridge.kt) | 소스 작성. 에뮬레이터 검증 전 |
| FusedLocation 1.1.3 | `registerFusedLocationCallback(FusedLocationListener)`, `unregisterFusedLocationCallback()`. `onChanged(LocationData?)`의 latitude, longitude, altitude, heading, confidence, timestampMillis | 내 차 위치, 그림까지 남은 거리 계산 | `FUSED_LOCATION` | [PleosLocationBridge.kt](../app/src/pleos/java/com/mydrawingmap/app/vehicle/PleosLocationBridge.kt) | 소스 작성. 에뮬레이터 검증 전 |
| FusedLocation 영역 알림 | `addProximityAlert(ProximityAlertRequest(latitude, longitude, radiusMeters, transitionTypes), ProximityAlertListener)`, `removeProximityAlert(alertId)`. 리스너 `onRegistered(alertId)`, `onTransition(event)`, `onError()` | 그림 앞 영역에 들어설 때 예고 | `FUSED_LOCATION` | 아직 없음 | 계획. 지금은 위치 콜백 좌표로 그림 120 m 앞을 직접 계산합니다([TripEngine.kt](../app/src/main/java/com/mydrawingmap/app/core/TripEngine.kt)) |
| AAOS CarUxRestrictionsManager | `registerListener()`, `currentCarUxRestrictions.isRequiresDistractionOptimization` | 주행·정차 상태를 받습니다 | 매니페스트에 `distractionOptimized` 선언 | [CarUxDrivingState.kt](../app/src/main/java/com/mydrawingmap/app/vehicle/CarUxDrivingState.kt) | 소스 작성. 에뮬레이터 검증 전 |
| TextToSpeech 3.3.1 | `TextToSpeech(context, Mode.ON_DEVICE)`, `initialize()`, `speak()`, `release()` | 예고 문장을 차 안에서 합성해 읽습니다. 두 화면으로 나눈 뒤에는 아이 기기가 없는 날의 보조 모드가 됩니다 | `TTS_SERVICE` | [PleosSpeaker.kt](../app/src/pleos/java/com/mydrawingmap/app/vehicle/PleosSpeaker.kt) | 소스 작성. 호출 형태는 문서로만 확인 |
| MapLibre Native Android 11.8.2 + OpenFreeMap | `Style.addImage()`, 아이콘 이미지(`icon-image`), 면 무늬(`fill-pattern`) | 아이 그림을 지도 아이콘과 면 무늬로 넣습니다 | 키·가입 없음. 출처 표시 | [MapController.kt](../app/src/main/java/com/mydrawingmap/app/map/MapController.kt) | 소스 작성. 에뮬레이터 검증 전 |
| 경로 좌표 | TMAP 자동차 경로안내 API → OSRM 공개 서버(시연용) → 직선 순 | 그림 지도의 경로선. 순정 내비의 길과 다르면 벗어남을 감지해 다시 받습니다 | TMAP 무료 1,000건/일, 24시간 넘게 저장 불가 | [RouteProviders.kt](../app/src/main/java/com/mydrawingmap/app/route/RouteProviders.kt) | 소스 작성 |

API와 권한 이름은 Pleos Connect 개발 문서와 Pleos 공식 샘플 앱에서 확인했습니다. Vehicle SDK(Light, HVAC)는 이번 범위에 넣지 않았습니다.

## 의존성과 권한

`app/build.gradle.kts` (pleos 빌드에만 들어갑니다)

```kotlin
add("pleosImplementation", "ai.pleos.playground:NaviHelper:3.0.5")
add("pleosImplementation", "ai.pleos.playground:FusedLocation:1.1.3")
add("pleosImplementation", "ai.pleos.playground:TextToSpeech:3.3.1")
```

`app/src/pleos/AndroidManifest.xml`

```xml
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE" />
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE_SEARCH" />
<uses-permission android:name="pleos.car.permission.FUSED_LOCATION" />
<uses-permission android:name="pleos.car.permission.TTS_SERVICE" />
```

`app/src/main/AndroidManifest.xml` (액티비티 안)

```xml
<meta-data
    android:name="distractionOptimized"
    android:value="true" />
```

## 차량과 맞닿는 부분

차량 SDK와 맞닿는 부분을 인터페이스 넷으로 나눴습니다([Bridges.kt](../app/src/main/java/com/mydrawingmap/app/vehicle/Bridges.kt)). 화면 쪽 코드는 이 인터페이스만 알고, 구현은 빌드 종류에 따라 바뀝니다.

| 인터페이스 | 하는 일 | pleos 빌드 | demo 빌드 |
|---|---|---|---|
| `NaviBridge` | 순정 내비와의 연결. 길이 정해짐·바뀜·도착을 받고, 목적지를 넘깁니다 | `PleosNaviBridge` | `DemoNaviBridge` |
| `LocationBridge` | 차 위치를 받습니다 | `PleosLocationBridge` | `ReplayLocationBridge`(경로를 따라 재생) |
| `Speaker` | 문장을 읽어 줍니다 | `PleosSpeaker` | `AndroidSpeaker`(안드로이드 기본 음성) |
| `DrivingState` | 차가 달리는 중인지 알려 줍니다 | `CarUxDrivingState`(시연 재생을 껐을 때) | `ManualDrivingState`(출발 단추) |

예고 시점과 문장을 정하는 판단 로직은 `core/`에 있고(`TripEngine`, `Stops`, `Sentences`) SDK를 직접 부르지 않습니다. 그래서 차 없이 단위 테스트로 돌릴 수 있습니다([CoreTest.kt](../app/src/test/java/com/mydrawingmap/app/core/CoreTest.kt)).

## 차량 앱이 아이 기기로 보낼 것 (계획)

두 화면의 연결은 아직 만들지 않았습니다. 아래는 2차 발표 전까지 만들 내용입니다.

| 보내는 것 | 어디서 얻나 | 언제 | 아이 화면의 쓰임 |
|---|---|---|---|
| 목적지 | NaviHelper `onRouteStarted` 또는 차량 앱의 '자주 가는 곳' | 길 안내 시작 | 도착 그림, 미리 보기 |
| 경로 좌표 | 순정 내비의 경로(제공 여부 확인 중) 또는 TMAP 경로 | 시작, 경로 변경 | 경로선과 그림 순서 |
| 차량 위치와 방향 | FusedLocation `onChanged` | 주행 중 계속 | 내 차 이동, 그림 앞 예고 |
| 경로 변경 | NaviHelper `onReRouteEnded` | 바뀐 직후 | "길이 바뀌었어요." 뒤 지도 다시 그리기 |
| 남은 시간 | NaviHelper `onTripInfo` | 주행 중 | 남은 시간 표시 |
| 도착 | NaviHelper `onDestinationArrived` | 도착 | "~에 도착했어요." |
| 주행·정차 | CarUxRestrictionsManager | 바뀔 때 | 편집 화면과 따라가는 화면 전환 |

정차 중 차 화면의 QR을 아이 기기로 찍어 짝을 맺습니다. 아이 기기에서 차로 오는 조작은 받지 않습니다. 전송은 차 안 직접 연결(차량 Wi-Fi)을 우선하고, 어려우면 암호화한 중계 서버를 씁니다. 어느 방식이 Pleos Connect 정책에 맞는지는 확인이 필요합니다.

## 구현 상태 (2026-11-15)

| 항목 | 상태 | 위치 |
|---|---|---|
| 아이 화면의 전체 흐름(그리기 → 붙이기 → 목소리 고르기 → 미리 보기 → 주행 → 길 바뀜 → 도착) | 구현(웹 동작 시안). 차량 신호는 재생으로 대신함 | `web-prototype/index.html` |
| 그리기 도구(펜 네 가지, 굵기 30단계, 색 만들기, 스포이트) | 구현(웹 동작 시안). 안드로이드 소스에는 아직 없음 | `web-prototype/index.html` |
| 길 바뀜 처리(지나온 그림 유지, 알린 그림은 다시 말하지 않기) | 구현(웹 동작 시안), 안드로이드 소스 작성 | `app/src/main/java/com/mydrawingmap/app/core/TripEngine.kt` |
| 순정 내비 연동, 차량 위치 수신, 주행 상태 확인 | 소스 작성. 공식 샘플 앱과 호출을 대조함. 에뮬레이터 검증 전 | `app/src/pleos/…/vehicle/`, `app/src/main/…/vehicle/` |
| 지도에 그림 올리기(아이콘, 면 무늬) | 소스 작성. 에뮬레이터 검증 전 | `app/src/main/java/com/mydrawingmap/app/map/MapController.kt` |
| 두 화면의 연결(QR 짝 맺기, 위치·경로 전송) | 아직 만들지 않음 | 없음 |
| 경로 주변 랜드마크 자동 추출, "내 길" 기록 | 아직 만들지 않음. 지금은 경로 위의 빈 자리와 사용자가 찍는 내 장소로 대신함 | 없음 |

지금 `app/`의 안드로이드 앱은 한 기기 안에서 같은 흐름을 보여 주는 형태입니다.

## 확인이 필요한 것과 안 될 때의 대응

| 확인할 것 | 안 될 때의 대응 |
|---|---|
| NaviHelper가 경로 좌표를 주는가 | TMAP 경로로 그리고, 차가 그 길을 벗어나면 남은 길을 다시 받습니다(지금 소스의 방식) |
| 운전자가 순정 내비에서 정한 목적지를 콜백이 알려 주는가 | 차량 앱의 '자주 가는 곳' 단추로 `requestRoute`를 보내 목적지를 맞춥니다(지금 소스의 방식) |
| 차량 앱과 아이 기기를 어떤 방식으로 이을 수 있는가 | 암호화한 중계 서버를 쓰고 위치와 경로를 저장하지 않습니다 |
| 에뮬레이터에서 내비 이벤트가 어디까지 오는가 | 시연 재생 구현으로 같은 흐름을 보여 줍니다(인터페이스를 분리해 둔 이유) |
| 순정 내비가 아닌 내비 앱으로 안내받을 때도 이벤트가 오는가 | 순정 내비 기준으로 먼저 만듭니다 |

## 참고한 문서

- [NaviHelper 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro) · [경로](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/routes) · [이벤트](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners) · [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [FusedLocation 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro) · [위치 받기](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access)
- [TextToSpeech 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)
- [주행 제한 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy) · [주행 안전 설계 기준](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline) · [주행 제한 개발 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- Pleos 공식 샘플 앱: [pleos-sample-navi-app](https://github.com/Pleos-Playground/pleos-sample-navi-app) · [pleos-sample-fusedlocation-app](https://github.com/Pleos-Playground/pleos-sample-fusedlocation-app)
- [MapLibre 그림 아이콘 예제](https://maplibre.org/maplibre-native/android/examples/styling/custom-sprite/) · [OpenFreeMap](https://openfreemap.org) · [TMAP API 이용 조건](https://tmapapi.tmapmobility.com/terms.html)
