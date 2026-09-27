# 동영상 편집 기능: 4단계 최소 도구 UI 완료

## Goal
"phase 3 closeout handoff의 Open Questions 마무리 + phase 4 진행하자."

## State
- Branch: `feat/edit`
- Base commit: `953ba08`
- 요구사항: `agent-docs/requirements/e21af4e007f6b495-video-edit-phase4-tool-ui.md`
- 사용자 확정 사항: 최소 UI만 구현(드래그 접힘·floating window 제외), 편집 상태는 세션 한정, viewer 내 편집 토글로 진입, 편집 모드 = 항상 미리보기, 노출 기능은 구간 자르기·텍스트 오버레이·이미지 오버레이, 구간 입력은 타임라인 범위 슬라이더, 텍스트 렌더 계측 추가.
- Changed:
  - 신규 `feature/player`: `EditorLogic.kt`(구간 클램프, 원본→출력 시간축 오버레이 변환, `EditSpec` 구성), `EditorViewModel.kt`, `EditorPane.kt`(미리보기 surface + `ImageOverlayEditor`), `EditorToolPanel.kt`.
  - 신규 `feature/browser`: `ImageGridRoute.kt`(`ImageGridViewModel` + 권한).
  - 수정: `PlayerScreen.kt`(편집 버튼, 화면 이탈 시 일시정지, `formatTime` 공개), `ClipApp.kt`(편집 모드에서 viewer/explorer 교체), `ClipIcons`(Edit), `app/build.gradle.kts`, `feature/player` strings/dimens.
  - 삭제: `app/src/debug`(`OverlayDebugActivity`)와 그 debug 의존성.
  - 테스트: 신규 `EditorLogicTest`(11건), `OverlayRenderTest`에 텍스트 앵커·글자 크기 계측 2건 추가, `FixtureAssets.frameAt` 추가.
- 검증: `./gradlew assembleDebug testDebugUnitTest` 성공, `:core:editor:connectedDebugAndroidTest` 49건 성공(에뮬레이터 `clip_tablet_1280x800`, API 35), 에뮬레이터 수동 확인(편집 진입 → 이미지·텍스트 오버레이 추가 → 미리보기 반영 → 내보내기 완료, 내보낸 파일에 두 오버레이 렌더 확인, 구간 0:02~0:10 지정 시 10초 입력이 7.3초로 출력), `git diff --check` 통과.

## Failed Attempts
| attempt | failure evidence | cause |
|---|---|---|
| 빈 문자열로 새 텍스트 오버레이 생성 | `InvalidEffectException: overlay text must not be blank`로 앱 크래시 | verified: `OverlayEditSession.add`가 `EditPlanner.validateOverlays`를 호출하고 빈 텍스트를 거부한다. 기본 문자열을 호출자가 넘겨야 한다 |
| 오버레이 변경마다 `OverlayPreviewPlayer.show` 호출 | 드래그·타이핑 이벤트마다 `CompositionPlayer` 재생성 | verified: `show`가 `release()` 후 새 플레이어를 만든다. 디바운스가 필요하다 |
| `EditorViewModel.setCutMode` | `Platform declaration clash`로 컴파일 실패 | verified: `var cutMode`의 setter와 JVM 시그니처가 같다. `chooseCutMode`로 개명 |
| `val explorer = @Composable { ... return@Composable }` | 라벨 미해결 | verified: `@Composable`은 애노테이션이라 람다 라벨이 되지 않는다. if/else로 대체 |

## Next Step
남은 4단계 범위를 사용자와 확정 후 진행한다: overlay를 explorer로 드롭해 아이콘으로 접는 동작, 전체화면 편집 모드와 explorer floating window(`SYSTEM_ALERT_WINDOW` 제약 조사 필요), Phase 2 효과(비율·반전·배속)의 UI 노출.

## Open Questions
- 편집 상태 영속화는 여전히 미도입이다. 프로세스 종료 시 오버레이가 사라진다.
- 미리보기는 컷·배속·비율을 반영하지 않는다. 구간을 자른 상태에서 오버레이 위치는 미리보기와 출력이 시간축만 다르고, 그 변환은 `toOutputOverlays`가 담당한다. 미리보기에 컷을 반영할지는 미정.
- 텍스트 오버레이의 색상·배경·정렬은 UI에 노출되지 않았다(엔진은 지원).

## Contract Snapshot
none
