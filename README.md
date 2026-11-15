# HI-MAP — 아이가 그리는 지도

운전석 내비는 그대로 두고, 뒷자리 아이의 폰·패드에 아이가 직접 그린 그림으로 꾸민 "아이의 내비"를 띄워 같은 길을 함께 따라가게 하는 Pleos Connect 차량용 앱입니다. 운전자만 보던 길을 탄 사람 모두가 함께 본다는 것이 컨셉입니다.

낯선 이동을 힘들어하는 아이(자폐 스펙트럼 아동 등)가 "지금 어디쯤이고, 다음에 무엇이 나오는지"를 자기 그림으로 미리 볼 수 있게 합니다. 길 안내는 순정 내비가 그대로 맡고, 운전석 화면에는 새로 뜨는 것이 없습니다.

## 구성 

| 부분 | 어디서 도나 | 하는 일 |
|---|---|---|
| 차량 앱 | Pleos Connect (이 저장소의 `app/`) | 순정 내비의 목적지·경로 변경·도착(NaviHelper)과 차량 위치(FusedLocation)를 읽어 아이 기기로 보냅니다. 운전석 화면에는 정차 중의 연결용 QR과 연결 표시만 띄웁니다 |
| 아이 화면 | 아이의 폰·패드 브라우저 (`web-prototype/`) | 그리기, 지도 꾸미기, 미리 보기, 주행 중 그림 지도와 예고. 설치 없이 열리는 웹 화면입니다 |

두 화면의 연결(QR로 짝 맺기, 위치·경로 전송)은 **아직 만들지 않았습니다.** 지금 `app/`의 안드로이드 앱은 한 기기 안에서 같은 흐름(그리기 → 붙이기 → 주행 → 길 바뀜 → 도착)을 보여 주는 형태이고, 차량 쪽 연결부(`vehicle/`)와 화면을 나누는 일은 2차 발표 전까지 합니다. 웹 시안은 왼쪽에 그대로인 운전석 화면, 오른쪽에 아이의 태블릿(휴대폰으로도 전환)을 나란히 보여 줍니다.

## 문서

| 문서 | 내용 |
|---|---|
| [docs/DESIGN.md](docs/DESIGN.md) | 두 화면의 구성, 아이 화면의 기능, 기존 기능·서비스와 다른 점, 차량 특화 요소, 주행 안전 설계, 사용 시나리오 |
| [docs/SDK.md](docs/SDK.md) | Pleos Connect SDK·API 사용표(호출, 콜백, 권한, 코드 위치), 아이 기기로 보낼 내용, 구현 상태, 확인이 필요한 것 |
| [docs/RESEARCH.md](docs/RESEARCH.md) | 문제와 해결 방식의 근거와 출처, 아직 검증되지 않은 것, 선행 사례 |

## 지금 상태 (2026-11-15)

- 전체 흐름(그리기 → 붙이기 → 미리 보기 → 주행 → 길 바뀜 → 도착)은 `web-prototype/index.html`의 웹 동작 시안에서 볼 수 있습니다. 화면 디자인도 이 시안이 최신입니다.
- 안드로이드 앱은 소스와 자동 빌드까지 준비한 단계입니다. `main` 브랜치에 올리면 GitHub Actions가 APK를 만들어 `apk/` 폴더에 넣습니다.
- Pleos Connect 에뮬레이터에서의 실행 확인은 아직 하지 못했고, 2차 발표 전까지 마칩니다. 시안에 있는 펜 네 가지·색 만들기·스포이트·목소리 고르기는 아직 앱에 들어 있지 않습니다.
- 화면 디자인은 Pleos Connect UI Design Guide를 기준으로 잡았습니다. 라이트 테마로 열리고 다크 테마로 바뀝니다. 회색은 가이드의 회색 단계(basic_00~basic_900)를 쓰고, 파랑은 텍스트 버튼의 파랑(#0064FF) 하나만 씁니다. 단추는 Basic button(회색 채움, 가장 중요한 하나만 어두운 채움), 고른 칸은 Toggle button(흰 칸과 그림자), 안내는 Toast(아래 가운데)를 따랐고, 글꼴은 시스템 글꼴(Asta Sans), 그림자는 가이드의 세 단계입니다. 단추·토스트·팝업의 크기와 모서리는 가이드 그림에서 잰 값이며, 글자 대비 4.5:1을 확인했습니다.
- 앱은 차량의 낮·밤 설정을 따릅니다(`values`, `values-night`). 글꼴 파일은 저장소와 APK에 넣지 않았습니다.
- 웹 시안의 왼쪽 운전석 화면(기어, 속도, 다음 회전, 지도)은 차량의 순정 화면을 본떠 그린 것이고, 이 앱 때문에 바뀌는 것이 없습니다.

## 무엇을 하나요

1. **멈춰 있을 때 그리고 붙입니다.** 그림판에 그림을 그려 저장하고, 가는 길가의 자리(지도의 ＋ 표지판)나 집·목적지·내 차에 붙입니다. 건물·공원·물의 무늬도 그림으로 바꿀 수 있습니다.
2. **달리는 동안에는 보기만 합니다.** 그리기·붙이기·지도 손짓이 모두 잠깁니다. 그림 120 m 앞에서 "곧 빨간 편의점이 나와요."처럼 늘 같은 틀의 문장으로 예고하고, 오른쪽에는 다음 그림 한 장만 보여 줍니다.
3. **길이 바뀌면 다시 알려 줍니다.** 순정 내비가 길을 바꾸거나 차가 계획한 길을 벗어나면 남은 길을 다시 받아 "길이 바뀌었어요. 그림 2개를 지나면 학교예요."라고 알려 줍니다. 이미 예고한 그림은 다시 말하지 않습니다.
4. **지도가 없어도 여정 띠는 남습니다.** 화면 아래의 여정 띠가 집에서 목적지까지 지나갈 그림을 한 줄로 보여 줍니다.

## 실행 방법

### 준비물

- Android Studio (Android Gradle Plugin 8.7을 지원하는 버전, Ladybug 2024.2 이상)
- JDK 17 이상 (Android Studio에 들어 있는 것을 쓰면 됩니다)
- Android SDK Platform 35 (컴파일용. 앱의 targetSdk는 34, minSdk는 28입니다)
- 인터넷 연결 (지도 타일과 경로를 받습니다)

### 빌드 종류

| 빌드 | 쓰는 곳 | Pleos Connect SDK |
|---|---|---|
| `demo` | 일반 안드로이드 에뮬레이터·태블릿(가로)에서 흐름을 확인할 때 | 쓰지 않음 (안드로이드 기본 음성, 재생되는 위치) |
| `pleos` | Pleos Connect 에뮬레이터·차량 | NaviHelper, FusedLocation, TextToSpeech 사용 |

두 빌드는 화면과 로직이 같고, 차량과 맞닿는 부분(`vehicle` 패키지의 구현)만 다릅니다.

### Android Studio에서

1. 이 폴더를 열고 Gradle 동기화가 끝나기를 기다립니다.
2. **Build Variants** 창에서 `demoDebug` 또는 `pleosDebug`를 고릅니다.
3. 에뮬레이터를 켜고 Run 합니다.

### 명령줄에서

저장소에는 Gradle Wrapper 설정(`gradle/wrapper/gradle-wrapper.properties`, Gradle 8.9)만 들어 있습니다. `gradlew` 실행 파일이 필요하면 Android Studio의 Gradle 창에서 `wrapper` 작업을 한 번 실행하거나, Gradle이 설치된 곳에서 `gradle wrapper --gradle-version 8.9`를 실행합니다.

```
./gradlew assembleDemoDebug          # demo 빌드
./gradlew assemblePleosRelease       # 제출용 pleos 빌드
./gradlew testDemoDebugUnitTest      # 화면 없이 도는 로직 검사
```

### (선택) TMAP 경로 API 키

키가 없으면 OpenStreetMap 기반 OSRM 공개 시연 서버로 경로를 받고, 그것도 안 되면 곧은 선으로 잇습니다. TMAP 자동차 경로안내를 쓰려면 저장소 최상위의 `local.properties`에 아래 한 줄을 넣습니다. 이 파일은 `.gitignore`에 들어 있어 저장소에 올라가지 않습니다.

```
TMAP_APP_KEY=발급받은_앱_키
```

## 테스트 환경과 필요 설정

| 항목 | 내용 |
|---|---|
| 개발에 사용한 Pleos Connect 에뮬레이터 버전 | 대상은 v2.1.0입니다(기기: Pleos Connect v2.1.0, 시스템 이미지: Pleos Connect v2.1.0 APIs, Android API 34). 2026-11-15 현재 이 에뮬레이터에서의 실행은 아직 확인하지 못했습니다 |
| 에뮬레이터 설정 | Pleos 개발자 문서 기준: Default boot = Cold, RAM 4 GB |
| 화면 규격 | 가로 방향, 앱 영역의 창 두 칸을 쓰는 배치로 만들었습니다. 에뮬레이터의 폼팩터 테스트(Display preset → Test screen size)에서 dp·DPI·px를 확인한 뒤 이 칸에 적습니다 |
| 화면 방향 | 가로 전용. 지도 + 오른쪽 패널(360dp) + 아래 여정 띠 구성이라 너비 1024dp 이상을 권장합니다 |
| 필요 권한 | `INTERNET`, (pleos 빌드) `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `FUSED_LOCATION`, `TTS_SERVICE` |
| 네트워크 | 지도(tiles.openfreemap.org)와 경로(router.project-osrm.org 또는 apis.openapi.sk.com)에 접속할 수 있어야 합니다 |
| 음성 | demo 빌드는 기기의 한국어 음성 합성 데이터가 필요합니다. pleos 빌드는 Pleos TextToSpeech(ON_DEVICE)를 씁니다 |

## 써 보기

1. 앱을 켜면 집에서 학교까지의 길과 여정 띠가 보입니다. 처음 켰을 때는 예시 그림(노란 버스, 빨간 집, 파란 다리, 초록 나무)이 붙어 있습니다.
2. **그리기** 탭에서 그림을 그리고 이름을 적어 저장합니다.
3. **붙이기** 탭에서 그림을 고른 뒤, 지도의 ＋ 표지판이나 목록의 자리를 누릅니다. 가는 길 가까이를 길게 눌러도 그 자리에 붙습니다. 같은 그림을 한 번 더 누르면 떼어집니다.
4. **미리 보기** 탭에서 오늘 지나갈 그림과 예고 문장을 확인합니다.
5. **출발하기**를 누르면 달리는 상태가 됩니다. 꾸미기가 잠기고, 그림 앞에서 예고가 나옵니다. **길 막힘**을 누르면 길이 바뀌는 상황을 볼 수 있습니다.
6. 위쪽의 목적지(학교, 할머니 집)를 누르면 길을 다시 받고, pleos 빌드에서는 순정 내비에도 같은 목적지를 보냅니다.

에뮬레이터는 스스로 달리지 않으므로 처음에는 "시연 재생"(화면의 출발 단추와, 길을 따라 재생되는 위치)으로 켜집니다. pleos 빌드에서는 위쪽의 **시연 재생 켬** 단추를 눌러 끄면 차량 신호(FusedLocation 위치, 차량의 주행 제한 상태)로 바뀝니다.

집과 목적지 좌표는 `app/src/main/assets/demo_trip.json`에서 바꿉니다.

앱을 설치하지 않고 흐름만 보려면 `web-prototype/index.html`을 브라우저로 엽니다. 가상의 마을 지도 위에서 같은 흐름을 볼 수 있는 웹 동작 시안이고, "시연 재생" 단추를 누르면 그리기부터 도착까지 약 1분 동안 이어서 보여 줍니다. 화면 디자인은 이 시안이 최신입니다. 시안에 있는 펜 네 가지·색 만들기·스포이트·목소리 고르기는 아직 앱에 넣지 않았습니다.

## APK 위치

`main` 브랜치에 소스를 올리면 GitHub Actions(`.github/workflows/build.yml`)가 release APK 두 개를 만들어 `apk/` 폴더에 넣습니다. 빌드 결과와 오류는 저장소의 Actions 탭에서 볼 수 있습니다.

| 파일 | 빌드 | 쓰는 곳 |
|---|---|---|
| `apk/아이드로우_HI-MAP_release.apk` | pleos | Pleos Connect 에뮬레이터·차량 (제출용) |
| `apk/아이드로우_HI-MAP_demo_release.apk` | demo | 일반 안드로이드 기기·에뮬레이터(가로)에서 흐름을 볼 때 |

직접 만들 때는 아래 명령을 씁니다.

```
./gradlew assemblePleosRelease
# 결과: app/build/outputs/apk/pleos/release/app-pleos-release.apk
```

release 빌드는 바로 설치해 볼 수 있게 디버그 키로 서명하도록 해 두었습니다. 출시할 때는 전용 키로 바꿉니다.

## Pleos Connect SDK를 쓰는 곳

| SDK | 쓰는 API | 쓰임 | 권한 | 코드 |
|---|---|---|---|---|
| NaviHelper 3.0.5 | `initialize()`, `addListener()`, `removeListener()`, `release()`. 콜백 `onRouteStarted`, `onRoutingCompleted`, `onReRouteEnded`, `onDestinationArrived`, `onError`. `requestRoute(RouteInfo)` | 순정 내비의 길이 정해짐·바뀜·도착 이벤트를 받고, 자주 가는 곳을 순정 내비의 목적지로 넣습니다. 남은 시간(`onTripInfo`)은 지금 기록만 합니다 | `NAVI_ROUTE`, `NAVI_ROUTE_SEARCH` | `app/src/pleos/.../vehicle/PleosNaviBridge.kt` |
| FusedLocation 1.1.3 | `registerFusedLocationCallback()`, `unregisterFusedLocationCallback()`, `onChanged(LocationData?)` | 차량 위치를 받아 그림 지도 위의 내 차와 진행 정도를 맞추고, 그림까지 남은 거리를 계산해 예고합니다 | `FUSED_LOCATION` | `app/src/pleos/.../vehicle/PleosLocationBridge.kt` |
| TextToSpeech 3.3.1 | `TextToSpeech(context, Mode.ON_DEVICE)`, `initialize()`, `speak()`, `release()` | 예고 문장을 차 안에서 합성해 읽습니다. 두 화면으로 나눈 뒤에는 아이 기기가 없는 날의 보조 모드가 됩니다 | `TTS_SERVICE` | `app/src/pleos/.../vehicle/PleosSpeaker.kt` |
| 주행 제한 상태(AAOS CarUxRestrictionsManager) | `registerListener()`, `isRequiresDistractionOptimization` | 달리는 중인지 알아냅니다. 지금 앱은 달리는 중에 꾸미기를 잠급니다 | 매니페스트에 `distractionOptimized` 선언 | `app/src/main/.../vehicle/CarUxDrivingState.kt` |

순정 내비의 지도 자체를 바꾸는 API는 없으므로, 그림 지도는 따로 그립니다(MapLibre Native + OpenFreeMap). API별 자세한 표와 권한 선언, 아이 기기로 보낼 내용, 확인이 필요한 것은 [docs/SDK.md](docs/SDK.md)에 있습니다.

## 코드 구조

```
app/src/main/java/com/mydrawingmap/app/
  MainActivity.kt        화면 하나. 멈춤/달림 상태에 따라 패널을 바꾼다
  core/                  안드로이드에 의존하지 않는 로직 (거리 계산, 경로, 그림 자리 고르기, 예고 문장, 이동 엔진)
  data/                  그림 저장소(차 안 저장소에만 보관), 시연용 출발지·목적지
  draw/                  그림판, 스티커·표지판 그리기, 예시 그림
  map/                   그림 지도 (MapLibre). 그림을 스타일 이미지와 무늬로 넣는다
  route/                 경로 받기 (TMAP → OSRM → 직선)
  ui/                    여정 띠
  vehicle/               차량 연결부의 인터페이스와 시연용 구현, 주행 제한 상태
app/src/demo/…/vehicle/  demo 빌드의 연결부
app/src/pleos/…/vehicle/ pleos 빌드의 연결부 (Pleos Connect SDK 호출)
app/src/test/            core 로직 단위 테스트
web-prototype/           웹 동작 시안 (index.html을 브라우저로 열면 됩니다)
docs/                    설계, SDK 사용, 근거 문서
.github/workflows/       APK 자동 빌드 설정
```

## 주행 안전 설계

아래는 지금 `app/`의 앱(한 기기 형태)이 지키는 것입니다. 두 화면으로 나눈 뒤의 설계(운전석 화면은 그대로, 운전자의 조작은 정차 중의 연결 한 번, 소리는 아이 기기에서만)는 [docs/DESIGN.md](docs/DESIGN.md)에 있습니다.

- 그리기와 붙이기는 멈춰 있을 때만 됩니다. 달리는 중에는 그림판, 목록, 지도 손짓, 목적지 변경이 모두 잠깁니다.
- 달리는 중 화면에는 영상·게임·깜박임이 없습니다. 움직이는 것은 내 차를 천천히 따라가는 지도뿐이고, "다음 그림"과 여정 띠는 그림을 지날 때만 바뀝니다. 남은 거리는 100 m 단위로만 바뀝니다.
- 한 번의 이동에서 예고하는 그림은 많아야 여섯 장이고, 서로 250 m 이상 떨어져 있습니다.
- 도로와 경로선은 아이가 바꿀 수 없습니다. 그림은 길가의 표지판과 건물·공원·물의 무늬에만 들어갑니다.
- 낮·밤 화면이 달리는 중에 바뀌면, 화면이 처음으로 돌아가지 않도록 차가 멈춘 뒤에 바꿉니다.

## 개인정보

아이의 그림, 그림을 붙인 자리, 직접 찍은 장소는 차 안 앱 저장소(`files/drawings`)에만 저장하고 서버로 보내지 않습니다. 밖으로 나가는 정보는 경로를 받을 때 보내는 출발·도착 좌표와 지도 타일 요청뿐입니다. 두 화면으로 나눈 뒤에는 그림을 아이 기기에만 저장하고, 차가 아이 기기로 보낸 위치와 경로는 이동이 끝나면 지우도록 만들 계획입니다.

## 알려진 한계

- 순정 내비가 고른 경로의 모양을 NaviHelper로 받을 수 있는지 확인하지 못해, 그림 지도의 경로는 따로 받습니다(TMAP 또는 OSRM). 두 경로가 다를 수 있고, 다르면 차가 계획한 길에서 벗어난 것을 감지해 남은 길을 다시 받습니다.
- 그림 앞 예고는 지금은 위치 콜백의 좌표로 앱이 직접 거리를 계산합니다. FusedLocation의 영역 진입 알림(`addProximityAlert`)으로 바꾸는 것은 다음 단계입니다.
- 화면 디자인은 웹 동작 시안이 앞서 있습니다. 앱은 색, 단추 모양, 앱 아이콘(Adaptive Icon)만 가이드와 맞췄고, 화면 구성과 크기(단추 44dp, 글자 12~15sp)는 그 이전 것입니다. Pleos Connect 기준 크기(단추 64·80·100dp, 글자 20sp 이상)로 키우는 일과 시안의 구성을 옮기는 일은 에뮬레이터에서 화면을 보면서 하는 다음 단계입니다.
- OSRM 공개 서버는 시연용입니다. 실제 서비스에서는 쓰지 않습니다.
- 지도 스타일이 밝은 색 한 가지입니다. 밤에는 패널만 어두워집니다.
- 뒷좌석 화면 지원, 보호자용 설정, 자주 가는 길 기록("내 길")은 아직 없습니다.

## 쓰는 외부 자료

- 지도 그리기: [MapLibre Native Android](https://maplibre.org/) (BSD 2-Clause)
- 지도 타일·스타일: [OpenFreeMap](https://openfreemap.org) Positron 스타일. 지도 데이터 © [OpenStreetMap](https://www.openstreetmap.org/copyright) 기여자 (지도 화면의 ⓘ 단추에 표시됩니다)
- 경로: [OSRM](https://project-osrm.org/) 공개 시연 서버, (선택) [TMAP](https://tmapapi.tmapmobility.com/) 자동차 경로안내
