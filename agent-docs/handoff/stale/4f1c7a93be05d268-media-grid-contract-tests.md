# MediaStore 미디어 그리드 계약 테스트 — 라운드 한도 도달

## Goal
"tab layer의 더미 아이콘들을 실제 media store에서 목록을 뽑아와서 grid로 만드는 기능으로 만들어.
그리고 향후 기능 추가가 가능한 형태로 만들어 (ex. 특정 path 제외, 특정 path 추가, 추가된 path에서 압축 파일을 검색해서 내부에 해당 파일 (이미지/음성) 등이 있는 경우에 폴더와 같이 표시 등)
태그는 계속해서 더미."
이후: "테스트는 stash 한다. 계약 작성후 /contract-workflow 실행. implementer는 이미 완료된 것으로 간주하고, test-implementer, 이후 test-verifier를 실행한다. 이후 구현의 수정이 필요한 경우 main agent에서 implementer 역할을 수행한다."

## State
- 브랜치 `ui/basic`, 베이스 커밋 `d7da377`. 커밋 없음(전부 워킹 트리).
- 구현(신규): `core/model/.../MediaEntry.kt`, `core/data/.../media/{MediaSource,MediaFilter,MediaRepository,MediaStoreMediaSource}.kt`, `core/data/.../di/MediaModule.kt`, `feature/browser/.../{MediaGridScreen,MediaGridViewModel,MediaThumbnail}.kt`, `feature/player/.../ImageViewerPane.kt`
- 구현(수정): `core/data/.../repository/MediaStoreImageRepository.kt`, `feature/browser/.../ImageGridScreen.kt`, `app/.../ui/{TabLayer,ClipApp}.kt`, `app/build.gradle.kts`, `feature/{browser,player}/src/main/res/values/strings.xml`
- 테스트(계약 기반, test-implementer 작성): `core/data/src/test/.../media/{MediaAggregationTest,DefaultMediaRepositoryTest}.kt`, `feature/browser/src/test/.../{MediaGridViewModelTest,MediaGridScreenScreenshotTest}.kt`, 골든 `feature/browser/src/test/screenshots/MediaGridScreen_{entries,empty,permission}.png`
- `./gradlew :core:data:testDebugUnitTest :feature:browser:testDebugUnitTest` 통과.
- 워크플로 시작 시 손으로 쓴 초기 테스트는 stash에 보관했다가 사용자 지시로 폐기했다. 남아 있는 테스트는 계약 기반 스위트뿐이다.

## Failed Attempts
| attempt | failure evidence | cause |
| 계약 v1 스위트로 결함 검출 | seed 3건(예외 흡수용 runCatching 제거 / 필터 all→any / dedup keep-last) 모두 스위트 green 유지 | 케이스 부재(예외 흡수 미검증, 필터 2개 조합 없음, 중복 테스트가 동일 값 사용) — verified |
| 계약 v2로 C8 작성 | test-implementer가 C8 미작성, 챌린지 보고 | `# Signatures`에 `DefaultMediaRepository` 생성자가 없어 구현 파일을 열지 않고 인스턴스 생성 불가 — verified (v3에서 시그니처 추가로 해소) |
| 계약 v5 스위트로 C17 seed 검출(1차 시도) | `!permissionGranted && uiState is Loading` 변형이 green 유지 | roborazzi가 verify 모드가 아니라 실행 때마다 골든을 덮어써 스크린샷 테스트가 구조적으로 실패 불가였음 — verified (`roborazzi.test.verify=true` 추가로 해소) |
| 계약 v3 스위트로 2차 결함 검출 | seed 3건 모두 green: `reload()`를 no-op으로 만들기 / `MediaAggregation.combine`이 `results[0]`에만 필터 적용 / `DefaultMediaRepository`가 주입된 filters 대신 `emptySet()` 전달 | 세 가지 모두 대응 케이스가 계약에 없음. `reload()`는 시그니처에만 있고 단독 트리거 케이스 없음, 다중 소스 × 비어있지 않은 필터 조합 없음, C8이 `filters = emptySet()`로만 구성됨 — verified |

## Progress (v4/v5 라운드)
- 계약 v4: C14(reload 단독 재조회), C15(다중 소스 전체 필터 적용), C16(주입 필터 반영) 추가. 대응 테스트 작성 후 3개 seed 모두 RED 전환 확인.
- test-verifier(v4) 지적 2건 → 계약 v5: C17(permissionGranted=false + Success), C18(selectedUri가 두 번째 항목) 추가. 골든 `MediaGridScreen_{permission_with_entries,selected_second}.png` 신규 기록.
- roborazzi가 verify 없이 골든을 덮어쓰기만 해 스크린샷 테스트가 실패할 수 없는 상태였다. `gradle.properties`에 `roborazzi.test.verify=true` 추가(기록은 `-Proborazzi.test.record=true`). 이후 C17/C18 seed 모두 RED 확인.
- 현재 전체 스위트 통과.

## Next Step
계약 v5 스위트에 대해 test-verifier를 한 번 더 실행해 C17/C18 추가분과 `roborazzi.test.verify=true` 이후의 스크린샷 커버리지를 감사한다(라운드 한도 초과 상태이므로 사용자 승인 후 진행). 지적이 없으면 이 핸드오프를 stale 처리한다.

## Open Questions
- `MediaStoreMediaSource`의 커서 파싱(DATA/DURATION/BUCKET 컬럼 누락, API 29 미만 경로)은 이번 계약에서 명시적으로 범위 밖. 별도 계약으로 다룰지 미정.
- I3(클릭 시 kind별 절대경로/content URI 전달)은 app 모듈 배선이라 이 스위트 범위 밖으로 반려했고, 수동 확인만 남아 있음.

## Contract Snapshot
`agent-docs/contracts/media-grid.md` v5 (세션 종료 시 삭제됨). 내용 요약이 아니라 전문:

---
version: 5
---

# User Intent
| id | intention | goal to achieve |
| I1 | 탭 레이어의 VIDEOS/AUDIO/IMAGES 탭이 실제 MediaStore 목록을 그리드로 보여준다 | kind별 MediaStore 조회 결과가 그리드에 렌더링된다 |
| I2 | 향후 확장(경로 제외/추가, 압축파일 내부 미디어)을 코드 수정 없이 끼워넣을 수 있다 | 소스/필터가 집합으로 주입되고, 집계 로직이 소스 개수·필터 개수에 무관하게 동작한다 |
| I3 | 그리드 항목 클릭은 기존 뷰어 레이어로 이어진다 | 비디오/오디오는 절대 경로, 이미지는 content URI를 콜백으로 전달한다 |
| I4 | 오디오는 앨범아트 썸네일, 없으면 종류 아이콘으로 표시한다 | 썸네일 로드 실패 시에도 셀이 비지 않는다 |
| I5 | 저장소 권한이 없으면 목록 대신 권한 요청 UI를 보여준다 | 권한 미허용 상태가 그리드와 구분되어 렌더링된다 |

# Paths
Implementation: core/model/src/main/java/com/doggy/clip_manager/core/model/MediaEntry.kt, core/data/src/main/java/com/doggy/clip_manager/core/data/media/, core/data/src/main/java/com/doggy/clip_manager/core/data/di/MediaModule.kt, core/data/src/main/java/com/doggy/clip_manager/core/data/repository/MediaStoreImageRepository.kt, feature/browser/src/main/java/com/doggy/clip_manager/feature/browser/MediaGridScreen.kt, feature/browser/src/main/java/com/doggy/clip_manager/feature/browser/MediaGridViewModel.kt, feature/browser/src/main/java/com/doggy/clip_manager/feature/browser/MediaThumbnail.kt, app/src/main/java/com/doggy/clip_manager/ui/TabLayer.kt, app/src/main/java/com/doggy/clip_manager/ui/ClipApp.kt
Tests: core/data/src/test/java/com/doggy/clip_manager/core/data/media/, feature/browser/src/test/java/com/doggy/clip_manager/feature/browser/MediaGridScreenScreenshotTest.kt, feature/browser/src/test/java/com/doggy/clip_manager/feature/browser/MediaGridViewModelTest.kt, feature/browser/src/test/screenshots/MediaGridScreen_*.png
Test command: ./gradlew :core:data:testDebugUnitTest :feature:browser:testDebugUnitTest --console=plain

# Signatures
package com.doggy.clip_manager.core.model
enum class MediaKind { VIDEO, AUDIO, IMAGE }
data class MediaEntry(val uri: String, val filePath: String?, val displayName: String, val kind: MediaKind, val bucketName: String?, val dateModifiedSeconds: Long, val durationMs: Long?)
data class MediaQuery(val kinds: Set<MediaKind>)

package com.doggy.clip_manager.core.data.media
interface MediaSource { suspend fun query(query: MediaQuery): List<MediaEntry> }
fun interface MediaFilter { fun accepts(entry: MediaEntry): Boolean }
object MediaAggregation { fun combine(results: List<List<MediaEntry>>, filters: Set<MediaFilter>): List<MediaEntry> }
interface MediaRepository { suspend fun query(query: MediaQuery): List<MediaEntry> }
internal class DefaultMediaRepository @Inject constructor(private val sources: Set<@JvmSuppressWildcards MediaSource>, private val filters: Set<@JvmSuppressWildcards MediaFilter>) : MediaRepository

package com.doggy.clip_manager.feature.browser
sealed interface MediaGridUiState { data object Loading; data class Success(val entries: List<MediaEntry>) }
class MediaGridViewModel(mediaRepository: MediaRepository) : ViewModel  // val uiState: StateFlow<MediaGridUiState>; fun setKind(newKind: MediaKind); fun reload()
// setKind는 kind를 바꾸면서 그 자체로 새 조회를 트리거한다. reload()는 kind를 바꾸지 않고 현재 kind로 다시 조회한다.
internal fun MediaGridScreen(uiState: MediaGridUiState, permissionGranted: Boolean, onRequestPermission: () -> Unit, onEntryClick: (MediaEntry) -> Unit, modifier: Modifier = Modifier, selectedUri: String? = null)

# Errors
MediaRepository.query는 예외를 던지지 않는다. 개별 MediaSource.query가 던진 예외는 DefaultMediaRepository 안에서 그 소스의 결과를 빈 목록으로 대체하는 방식으로 흡수되고, 나머지 소스의 결과는 정상적으로 집계된다.

# Cases
| id | level | input / state | expected result |
| C1 | normal | MediaAggregation.combine(두 소스의 결과 리스트, 필터 없음) | 두 리스트가 합쳐지고 dateModifiedSeconds 내림차순, 동률이면 displayName 오름차순(대소문자 무시)으로 정렬된다 |
| C2 | normal | combine(단일 소스, 특정 filePath 접두사를 거부하는 필터 1개) | 거부된 항목만 빠지고 나머지 순서는 유지된다 |
| C3 | normal | MediaGridViewModel(uiState 수집 중), 저장소에 VIDEO 2건 → setKind(VIDEO) 후 reload() | uiState가 Success(저장소가 돌려준 목록)이 된다 |
| C4 | normal | 위 상태에서 setKind(AUDIO) | 저장소에 AUDIO kind를 담은 MediaQuery가 전달되고 uiState가 그 결과로 바뀐다 |
| C5 | normal | MediaGridScreen(Success(VIDEO/AUDIO/IMAGE 각 1건), permissionGranted=true, selectedUri=첫 항목) | 세 항목이 이름과 함께 그리드로 렌더링되고 선택 항목만 강조된다 (골든 이미지) |
| C6 | boundary | combine(빈 리스트들, 필터 없음) / MediaGridScreen(Success(emptyList()), 권한 허용) | 각각 빈 목록 / 빈 상태 안내가 렌더링된다 (골든 이미지) |
| C7 | boundary | combine에 uri가 동일한 항목이 서로 다른 소스에서 들어옴 | 결과에 해당 uri는 한 번만 남는다 |
| C8 | error | DefaultMediaRepository(소스 2개: 하나는 예외를 던지고 하나는 2건을 반환, 필터 없음).query(MediaQuery(setOf(VIDEO))) | 예외가 호출자에게 전파되지 않고, 정상 소스의 2건만 집계된 목록이 반환된다 |
| C9 | edge | MediaGridScreen(Loading, permissionGranted=false) | 목록 대신 권한 요청 UI가 렌더링된다 (골든 이미지) |
| C10 | edge | 썸네일을 로드할 수 없는 항목(Robolectric 환경의 content URI) | 셀이 비지 않고 kind별 아이콘과 파일명이 보인다 (C5/C6 골든에서 함께 관찰) |
| C11 | edge | combine(단일 소스, 모든 항목을 거부하는 필터) | 빈 목록이 반환된다 |
| C12 | normal | combine(단일 소스, 서로 다른 조건의 필터 2개) | 두 필터를 모두 통과한 항목만 남는다(한쪽만 통과한 항목은 제외된다) |
| C13 | edge | combine(소스 A와 소스 B에 uri는 같고 displayName/dateModifiedSeconds가 다른 항목이 각각 존재, results 순서는 [A, B]) | 해당 uri는 한 번만 남고, 남는 항목은 먼저 등장한 소스 A의 항목이다 |
| C14 | normal | setKind(VIDEO)로 초기 조회를 마친 뒤 저장소가 같은 VIDEO kind에 대해 다른 목록을 돌려주도록 바뀐 상태에서 reload()만 호출 | uiState가 바뀐 새 목록으로 갱신된다(reload가 단독으로 재조회를 트리거한다) |
| C15 | normal | combine(소스 2개, 필터 1개 이상) — 두 소스 모두에 필터가 거부할 항목이 들어 있음 | 두 소스 모두에서 거부 항목이 제거된다(첫 소스에만 필터가 적용되지 않는다) |
| C16 | normal | DefaultMediaRepository(소스 2개, 항목 일부를 거부하는 필터 1개 이상).query(...) | 주입된 필터가 적용된 결과가 반환된다(필터가 무시되지 않는다) |
| C17 | edge | MediaGridScreen(Success(항목 3건), permissionGranted=false) | 목록이 아니라 권한 요청 UI가 렌더링된다(로드된 목록이 남아 있어도 권한 미허용이 우선한다) (골든 이미지) |
| C18 | normal | MediaGridScreen(Success(항목 3건), permissionGranted=true, selectedUri=두 번째 항목) | 첫 항목이 아니라 selectedUri가 가리키는 두 번째 항목만 강조된다 (골든 이미지) |

# Version Log
## v5
- test-verifier 지적 2건 반영: (1) `permissionGranted=false`가 `Loading`과만 조합돼 "Success일 때는 그리드를 보여주는" 변형이 통과함 → C17 추가, (2) C5의 `selectedUri`가 첫 항목이라 "항상 index 0을 강조"하는 변형이 골든과 동일 출력을 내 통과함 → C18 추가.

## v4
- 라운드 3차 seed 검증에서 통과해버린 세 변형(`reload()` no-op / `combine`이 `results[0]`에만 필터 적용 / `DefaultMediaRepository`가 주입된 filters 대신 `emptySet()` 전달)에 대응해 C14, C15, C16 추가.
- test-verifier가 지적한 모호성 해소: `setKind`가 자체적으로 조회를 트리거한다는 점을 # Signatures 주석에 명시.

## v3
- test-implementer가 C8을 작성할 수 없다고 보고: # Errors와 C8이 DefaultMediaRepository를 지목하는데 # Signatures에 그 생성자가 없어 구현 파일을 열지 않고는 인스턴스를 만들 수 없었다. 생성자 선언을 # Signatures에 추가.

## v2
- test-verifier가 확인하고 seed로 검증된 세 가지 누락: (1) MediaSource 예외 흡수 동작이 테스트되지 않아 runCatching 제거가 통과함 → # Errors를 관측 가능한 동작으로 고쳐 쓰고 C8을 실제 케이스로 채움, (2) 필터 2개 이상 조합이 없어 all→any 변경이 통과함 → C12 추가, (3) 중복 uri 테스트가 동일 값을 써서 first-seen/last-seen 구분이 불가 → C13 추가.
- I3(클릭 시 kind별 절대경로/content URI 전달)에 대한 케이스 추가 요구는 반려. 계약 대상 표면인 MediaGridScreen은 MediaEntry 전체를 콜백으로 넘기며, kind별 분기는 app 모듈 배선(ClipApp)에 있어 이 스위트의 범위 밖이다. 해당 배선은 수동 확인 대상으로 남긴다.

## v1
- 초기 작성. 구현은 이미 완료된 상태이며 테스트만 계약으로부터 새로 작성한다.
