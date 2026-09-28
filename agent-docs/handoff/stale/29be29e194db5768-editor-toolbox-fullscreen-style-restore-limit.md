# 편집기 도구함·전체화면·텍스트 스타일·상태 복원: verifier 한도 도달

## Goal
"핸드오프 진행하자." / "나 잘거라서 혹시 R2까지 안 끝나면 한 번 더 진행해"

## State
- Branch: `feat/file-manager`, base commit `65f86cc`, 미커밋 작업 트리.
- Changed: `feature/editor/src/main`(EditorViewModel, EditorPersistence(신규), EditorLogic, EditorPane, EditorToolPanel, EditorToolboxStrip(신규), TextOverlayStyleOptions(신규), res), `app/src/main`(ClipApp.kt, res/values/dimens·strings·colors(신규)), `feature/editor/src/test`(EditorToolboxViewModelTest, EditorStateRestoreTest, EditorStateRestoreErrorTest, EditorTextStyleTest, SavedStateHandleTestSupport 신규; 기존 두 테스트는 생성자만 변경).
- Test command `./gradlew :feature:editor:testDebugUnitTest :core:editor:testDebugUnitTest assembleDebug` 통과(JAVA_HOME = Android Studio JBR).
- 리뷰 기록: `agent-docs/spec-logs/ad27e5f2d8c95388-review.md`.
- 기존 handoff `8d9c1d27e3005b7b`의 남은 범위(도구함, 전체화면·floating, 텍스트 스타일, 영속화)를 이 run이 구현했다. 해당 handoff는 stale로 옮겼다.

## Failed Attempts
| attempt | failure evidence | cause |
|---|---|---|
| 오버레이 행에 기본 `dragAndDropSource`(long-press 기본 감지) | 에뮬레이터에서 드래그가 시작되지 않고 목록만 스크롤 | verified by fix: 자식 TextButton이 Main pass에서 Down을 소비. Initial pass 감지로 교체 |
| floating 패널에 `pointerInput { detectDragGestures }` | 패널 내부 어디서도 드래그 이동 안 됨(구현자 관찰) | verified by fix: 자식 제스처가 Down 소비. Initial pass로 교체 |
| 패널 오프셋 클램프를 드래그 핸들러에서만 수행 | 세로 회전 후 패널이 창 밖 | verified: 크기 변경 시 재클램프 없음 |
| 저장 필드를 각각 독립 디코드 | 손상된 selectedOverlayId가 그대로 복원(D1), durationUs 손상 시 flips가 (0,0)으로 클램프(D2) | verified: 교차 필드 검증 부재. 복원 후 보정 패스 추가 |
| 테스트: SavedStateHandle 원시 값을 `==`로 비교 | 배열 값이 동일해도 "변경"으로 판정 | verified: 배열은 참조 비교. `Arrays.deepEquals` 사용 |
| 패널 하단 가장자리 행에서 long-press 드래그 | 3회 중 1회(첫 시도) 도구함에 들어가지 않음, 이전 리뷰에서도 1회 동일 | hypothesis: 가장자리에서 verticalScroll과 경합, 또는 해당 상호작용 직후 첫 제스처만 실패 |

## Next Step
F-VR1-EDGE-01 해결: 패널 하단 가장자리에 걸친 오버레이 행의 long-press 드래그가 간헐적으로 도구함에 들어가지 않는 원인을 확인한다. 재현: 에뮬레이터 `clip_tablet_1280x800`, 편집 화면에서 오버레이 행이 y≈709~736(패널 하단)에 오도록 스크롤한 뒤 `adb shell input swipe 840 736 250 740 1500`. 원인 수정 또는 사용자 결정 후 새 workflow run(새 verifier 예산)으로 V-R1만 재감사한다.

## Open Questions
- 가장자리 행 드래그가 F4의 보장 범위인가, 아니면 자동 스크롤·가장자리 제외 같은 정책이 필요한가(사용자 결정).
- 도구함 아이콘을 드래그로 꺼내는 동작은 범위 밖으로 남겼다.
- ADR 후보 없음(되돌리기 비용이 낮은 UI·저장 방식 결정).

## Spec
`agent-docs/spec-logs/ad27e5f2d8c95388-editor-toolbox-fullscreen-style-restore.md`, version 3, status `limit`, run ID `ad27e5f2d8c95388`.

## Execution Ledger
- Findings: F-V3-01(verifier 1, 해결), F-V3-02·F-SPEC-01(verifier 2, 해결), F-VR1-EDGE-01(verifier 3, open, blocking).
- 구현 결함: D1·D2(복원 교차 필드), D3(드래그 시작), D4(회전 클램프), D5(토글 가림·대비) 모두 해결·재확인.
- Mutations: M1 enum 잘못된 기본값, M2 flips 쓰레기 목록, M3 stow 시 선택 유지, M4 fullscreen 미저장 — 모두 의도한 단언이 검출, restore 완료(`seed.py status`: none).
- Obligations: V1·V2·V3·V4·V-R2·V-R3·V-R4 accepted, V-R1 open.
- Counters: correction batches 12, verifier invocations 3/3(사용자가 2→3 승인).
