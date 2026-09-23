# 편집 UI 효과(비율·반전·배속) 노출: 미리보기 조각 전환 정지로 중단

## Goal
"Phase 2 효과 UI" — 4단계 handoff(`8d9c1d27e3005b7b-video-edit-phase4-minimal-tool-ui-complete.md`) Next Step ③. 사용자 확정: 배속·반전은 구간별, 비율은 프리셋+모드(crop 중심 고정), 미리보기에 컷·비율·반전·배속 모두 반영, 오버레이·효과 구간은 원본 시간축 유지.

## State
- Branch `handoffs`, base commit `2e6f2a3816309b22a9496b342d1f0db2bcfed68e`. 모든 변경은 미커밋.
- 변경 파일: `core/editor/.../EditedMediaItemFactory.kt`(신규; 내보내기용 `buildItems`, 미리보기용 `buildPreview`와 `TimeBasedFlip`), `EditPlanner.kt`(`flipAt`, `speedSegments`, `sourceTimeForOutput`), `PreciseTransformEditor.kt`, `feature/editor/.../EditorLogic.kt`(항상 SEGMENT_CONCAT), `EditorViewModel.kt`, `EditorPane.kt`, `EditorToolPanel.kt`, `OverlayPreviewPlayer.kt`, `strings.xml`. 테스트: `EditorLogicTest.kt`, `EditorEffectsViewModelTest.kt`(신규).
- 테스트 명령은 통과한다: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :feature:editor:testDebugUnitTest :core:editor:testDebugUnitTest`. connectedAndroidTest 뒤에 `androidJdkImage` 오류가 나면 `./gradlew --stop` 후 다시 실행한다.
- R1(spec v5 재실행)은 미리보기와 내보내기 모두 통과했다: 8.433s, 반전은 0–3s만, 2x는 3.03–4.57s, 오버레이는 3.5–4.05s. 기록은 `agent-docs/spec-logs/547fb213daf79144-review.md`에 있다.

## Failed Attempts

| attempt | failure evidence | cause |
|---|---|---|
| 미리보기에서 조각마다 `setDurationUs(조각 출력 길이)` 설정 | 배속으로 keep이 나뉘면 "미리보기를 만들 수 없습니다", `IllegalArgumentException` at `EditedMediaItem.getClippedDuration` | verified: durationUs는 잘리기 전 원본 길이여야 함. 원본 길이를 넘기도록 수정함(ID1) |
| 조각별 효과가 다른 `EditedMediaItem` 여러 개를 한 시퀀스로 `CompositionPlayer`(media3 1.11.1)에서 재생 | 첫 조각 이후 영상 정지, 위치는 진행되어 ENDED. 로그: `videoDisabled period=0` → `videoSize 0x0` → `EGL_BAD_ATTRIBUTE` → 이후 period 1의 video 재활성화 없음. 배속 없이 반전만 있어도 재현 | verified(에뮬레이터): 조각 전환 시 영상 렌더러가 다시 켜지지 않음. hypothesis: CompositionPlayer 제한인지 에뮬레이터 디코더(`c2.goldfish.h264.decoder`) 문제인지 미확정 |
| (재개) 미리보기에서 속도 효과 뒤에 반전 효과를 둠 | `IllegalArgumentException: CompositionPlayer only allows speed changing effects ... placed as first effects` | verified: media3 1.11.1 CompositionPlayer는 속도 효과가 첫 번째 video effect여야 함. 속도 효과를 맨 앞에 두고 반전은 출력 시각을 원본 시각으로 되돌려(`EditPlanner.sourceTimeForOutput`) 판정하도록 수정함 |
| 내보내기 SINGLE_COMPOSITION(구간마다 아이템) | R1 v4: 오버레이 누락(D2), 파일이 5.19s로 잘림(D1) | D2 verified: Transformer가 여러 아이템 Composition에서 첫 아이템이 아닌 아이템의 OverlayEffect를 버림(androidTest 탐침). D1 hypothesis: core 경로에서는 재현 안 됨. F11(SEGMENT_CONCAT)으로 바꾼 뒤 재현되지 않음 |
| 내보내기를 미리보기처럼 아이템 1개로 만들어 Transformer에 넘김(탐침) | `ExportException errorCode=7002`, 25초 동안 출력 샘플 없음 | hypothesis: `setDurationUs(원본 전체 길이)`나 속도 효과 우선 배치가 Transformer와 맞지 않음. 채택하지 않음 |
| R1 v5 1차 | 반전과 2x가 모두 원본 약 5.43s에서 끝남(D3/D4) | verified(재실행에서 재현): 패널을 스크롤하는 스와이프가 행의 범위 슬라이더를 움직여 끝이 약 5.4s로 바뀜. core 경로는 정상(androidTest). 절차 문제로 판정 |
| M5 변이: `updateSpeed`의 SPEED_STEPS 검사 제거 | 테스트 전부 통과 | verified: F2의 update 쪽에 Case·테스트가 없음(F-AUDIT-2). verifier 예산 소진으로 limit 처리 |


## Next Step
(완료) spec v6에 C11b를 추가하고 테스트를 추가했다. M5가 이 테스트에 탐지되고 verifier 3회차가 PASS했다. 남은 일은 사용자 검토 후 커밋하는 것이다.

이전 Next Step: F-AUDIT-2를 해결하는 새 workflow를 연다. C11b(`updateSpeed(idx, SpeedRange(range, 3f))` → false, 목록 그대로)를 Case와 V5에 추가하고, 테스트를 추가한다. M5 변이가 탐지되는지 확인한 뒤 verifier 감사를 받는다. 구현은 이미 올바르게 거부한다(`EditorViewModel.updateSpeed`). 그 뒤 커밋한다.

## Open Questions
- 미리보기 배속 효과(`TimestampAdjustmentShaderProgram.flush`)는 seek하면 `UnsupportedOperationException`을 낸다. 지금은 미리보기 seek가 범위 밖이지만, 스크럽 기능을 넣을 때 문제가 된다.
- 편집 패널을 세로로 스크롤할 때 행의 범위 슬라이더가 움직인다(D3/D4의 원인). spec 범위 밖이다.
- 미리보기 플레이어를 해제할 때 `ExoTimeoutException: Player release timed out`이 로그에만 찍힌다. 이번 변경 전부터 있었고 앱에는 영향이 없다.
- keep 구간이 여러 개일 때 미리보기 아이템 전환은 확인하지 않았다(A5; 지금 UI에서는 keep이 1개뿐이다).

## Spec
`agent-docs/spec-logs/547fb213daf79144-editor-effects-ui.md`, version 6, status complete, run ID `547fb213daf79144`.

## Execution Ledger
- Findings: TD1–TD3 fixed, F-AUDIT-1 resolved(v3), ID1 fixed, ID2 fixed(v4, 단일 아이템 미리보기), D1 재현 안 됨(F11 이후), D2 fixed(v5 F11), D3/D4 절차 문제로 판정, F-AUDIT-2 resolved(v6 C11b).
- Audit(verifier 2회차): V1–V4, V6–V12 accepted, V5 open(F-AUDIT-2). verifier 3회차: V1–V12 모두 accepted.
- Mutations: M1(배속 무시)→V2·V4 탐지, M2(`<=`)→V5 탐지, M3(ceil)→V4 탐지, M4(항상 SINGLE_COMPOSITION)→V12 탐지, M5(updateSpeed 배속 값 검사 제거)→v5에서는 탐지 안 됨, v6 C11b 테스트가 탐지.
- Advisory: M3는 V2로 탐지되지 않는다(C3–C6 입력이 모두 나누어떨어짐). 부분 조각의 floor→ceil 변이는 실행하지 않았다.
- Counters: correction batches 6, verifier invocations 3/3(사용자 승인으로 1회 추가), correction batches 7.
- `.harness/bin/spec_lifecycle.py`와 `workflow_marker.py`가 없어서 lifecycle과 텔레메트리는 실행하지 못했다. spec은 수동으로 보관했다.
