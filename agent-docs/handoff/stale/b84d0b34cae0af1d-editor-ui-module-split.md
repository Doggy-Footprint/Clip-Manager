# 편집 UI의 `:feature:editor` 모듈 분리

## Goal
사용자 원문: "이 기능의 대부분은 앞으로 이 프로젝트에 추가할 기능들과 별개로 작동할 것이라 생각해. 파일 탐색기 + 편집 + 뷰어에서 편집이 정상으로 되는 것이 다른 쪽에 의존성을 가질 이유는 떠오르는 게 없으니까. 그러니 이걸 module로 분리하는 건에 대해 어떻게 생각해?" → 제시한 Option B(`:feature:editor` 신설)로 진행하기로 결정.

## State
- 브랜치: `feat/edit`
- 커밋: `ed057e83e68711f739746589f8fb154a0904875b` (Phase 4 도구 UI)
- 미커밋 변경(모듈 분리 착수 전 커밋 필요):
  - `M feature/player/src/test/java/com/doggy/clip_manager/feature/player/EditorLogicTest.kt` — test-verifier 감사 결과 반영. 계약 id 기준으로 테스트명 재정렬, 11→18건.
  - `?? feature/player/src/test/java/com/doggy/clip_manager/feature/player/EditorViewModelTest.kt` — 신규 Robolectric 테스트 3건(계약 E4, 결정 G4).
- 검증 상태: `:feature:player:testDebugUnitTest` 전체 통과(`EditorLogicTest` 18, `EditorViewModelTest` 3, `PlayerLogicTest` 21, `PlayerScreenScreenshotTest` 8).
- 에뮬레이터 수동 확인 완료: 내보내기 시 진행률 알림 표시·백그라운드 유지, 완료 시 결과 경로 출력, 출력 파일의 길이와 오버레이 시간축 변환 정상.

## 분리 근거가 되는 사실
- 편집 UI 6개 파일(`EditorPane`, `EditorToolPanel`, `EditorViewModel`, `EditorLogic`, `ImageOverlayEditor`, `OverlayPreviewPlayer`)이 `:core:player`와 `PlayerViewModel`/`PlayerUiState` 심볼을 **하나도 참조하지 않는다.** `OverlayPreviewPlayer`는 media3 `CompositionPlayer`와 `core.editor`만 쓴다. 따라서 `:feature:editor`는 `:feature:player`에 의존하지 않으며 사이클이 생기지 않는다.
- 재생 쪽과의 유일한 접점은 `PlayerScreen.kt:75`의 `onStartEdit: ((durationUs: Long) -> Unit)?` 콜백 파라미터이며, 원시 타입만 쓰므로 모듈이 갈려도 그대로 동작한다.
- 편집 엔진은 이미 `:core:editor`로 분리되어 있고 `:core:model`에만 의존한다(`core/editor/build.gradle.kts:11`). 이번 작업 대상이 아니다.

## Failed Attempts
| attempt | failure evidence | cause |
|---|---|---|
| none | none | none |

## Next Step
1. 미커밋 테스트 변경을 먼저 커밋한다. 모듈 이동과 테스트 변경이 한 커밋에 섞이면 이동 중 회귀를 구분할 수 없다.
2. `settings.gradle.kts`에 `include(":feature:editor")` 추가. `AndroidFeatureConventionPlugin`이 Hilt·Compose·`:core:ui`·`:core:designsystem`·`:core:testing`을 붙여주므로 `build.gradle.kts`의 실질 내용은 `implementation(projects.core.editor)`와 `implementation(libs.media3.transformer)`뿐이다.
3. 위 6개 파일과 그 테스트 2개를 패키지 `com.doggy.clip_manager.feature.editor`로 이동.
4. 편집 전용 문자열·치수 리소스를 `feature/player/src/main/res/values/{strings,dimens}.xml`에서 새 모듈로 옮긴다. `R` 참조가 전부 바뀌므로 이동 누락 시 컴파일 에러로 드러난다.
5. `:app`의 `build.gradle.kts`에 `implementation(projects.feature.editor)` 추가, `ClipApp.kt:25-26`의 import 경로 수정.
6. `:feature:player:build.gradle.kts`에서 `implementation(projects.core.editor)`와 `implementation(libs.media3.transformer)`가 여전히 필요한지 확인해 불필요하면 제거한다.
7. 검증: `./gradlew assembleDebug testDebugUnitTest` 통과, 이어서 에뮬레이터에서 편집 진입 → 텍스트·이미지 오버레이 추가 → 미리보기 반영 → 내보내기 완료까지 재확인.

## Open Questions
- `ImageGridRoute`는 `:feature:browser`에 남긴다. 편집이 이미지 목록을 필요로 하지만 소유는 browser가 하고 `ClipApp`이 배선한다. 이후 "overlay를 explorer로 드롭" 요구가 오면 editor↔browser 콜백이 `ClipApp.kt`에 누적된다. 그 시점에 배선 방식을 다시 볼지 미정.
- 전체화면 편집 모드에서 편집 프리뷰와 재생 뷰어가 상태를 공유해야 하는지 아직 확정되지 않았다. 공유가 필요하다고 드러나면 이번 분리가 배선 부담으로 바뀐다. 이것이 이 결정의 유일한 불확실성이다.
- Phase 4 잔여 수동 확인 항목: `SlowState` 지연·정체 경고 문구를 아직 실제로 띄워보지 못했다. 재현이 `ExpectedTimeModel` 학습 상태에 의존해 불안정하다.
- 요구사항 문서 `agent-docs/requirements/e21af4e007f6b495-video-edit-phase4-tool-ui.md`의 Oracle이 "E1~E4: `EditorLogicTest`"로 되어 있으나, 실제로는 E4 일부와 G4가 `EditorViewModelTest`로 옮겨갔다. 모듈 이동 시 경로까지 함께 갱신해야 한다.

## Contract Snapshot
none
