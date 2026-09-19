# 동영상 편집 기능: 3단계 마무리

## Goal
"phase 3 마무리를 위한 작업 계획을 세워."

## State
- Branch: `feat/edit`
- Base commit: `3bea6c4`
- 요구사항: `agent-docs/requirements/f9a6261093f74075-video-edit-phase3-overlays.md` (B10~B12, 결정 F1~F4, Oracle P8~P11 추가)
- Changed: `core/editor`(`EditService` 오버레이 Intent 직렬화, `OverlayCompositionFactory.decode` 수정, `OverlaySpec` 계약 주석, build), `feature/player`(`OverlayPreviewPlayer`에 `setDurationUs`), `app`(`src/debug` 검증 화면, build), 테스트 `EditServiceIntentTest`(신규 18건), `OverlayRenderTest`(신규 4건), `OverlaySpecTest`(테스트 1건 개명).
- 검증: `./gradlew assembleDebug testDebugUnitTest` 성공, `:core:editor:connectedDebugAndroidTest` 47건 성공(에뮬레이터 `clip_tablet_1280x800`, API 35), 에뮬레이터 수동 확인 완료(미리보기 구간 표시/비표시, 서비스 경로 내보내기 결과의 2초 마젠타·5초 노랑 픽셀), `git diff --check` 통과.

## Failed Attempts
| attempt | failure evidence | cause |
|---|---|---|
| `File.toURI()`로 오버레이 이미지 URI 구성 | `InputNotReadableException: cannot open overlay image: file:/data/...` | verified: `file:/`(슬래시 1개)는 `ContentResolver`가 열지 못한다. `Uri.fromFile`을 써야 한다 |
| 이미지 오버레이 내보내기(수정 전) | `InputNotReadableException`이 항상 발생 | verified: `OverlayCompositionFactory.decode`가 `openInputStream(...)?.use(block) ?: throw`였고, 경계 측정용 `inJustDecodeBounds` 디코딩은 정상적으로 `null`을 반환하므로 elvis가 항상 발동했다. 이미지 오버레이는 한 번도 동작한 적이 없다 |
| `OverlayPreviewPlayer.show`(수정 전) | `IllegalStateException` at `EditedMediaItem.getPresentationDurationUs` | verified: `CompositionPlayer`는 재생 전 각 항목의 표시 길이를 요구하므로 `EditedMediaItem.Builder.setDurationUs`가 필수다 |
| 오버레이 왕복 테스트를 androidTest에 두기 | 기기 미연결로 계약 루프가 느려짐 | verified: Robolectric 단위 테스트로 옮겨 해결(선례 `core/database`) |

## Next Step
4단계(도구 UI)를 `agent-docs/handoff/b5c8e1d3a7f09264-video-edit-phase4-tool-ui.md`의 남은 Open Questions부터 확정하고 진행한다. `app/src/debug`의 `OverlayDebugActivity`는 4단계 UI가 붙으면 제거 대상이다.

## Open Questions
- 배속 구간의 `presentationTimeUs` 기준은 `OverlayRenderTest.overlay_B11_edge_outputRangeIsNotShiftedByASpeedSegment`로 출력 시간축임이 실측 확인되었다. 텍스트 오버레이의 렌더링 결과(글꼴 크기·앵커)는 여전히 계측되지 않았다.
- `OverlayEditSession`은 세션 한정이라 프로세스 종료 시 오버레이가 사라진다. 편집 상태의 저장 시점은 4단계에서 정한다.

## Contract Snapshot
`agent-docs/contracts/edit-service-overlay-intent.md` v1로 `EditService`의 오버레이 Intent 직렬화를 구현했고, 세션 종료와 함께 삭제했다. 계약은 왕복 동등성(텍스트·이미지 전 필드, `backgroundArgb`의 null과 0 구분 포함)과 손상된 extra·중복 id·역전 구간에 대한 `decodeStartSpec`의 `null` 반환을 고정했다. `OverlaySpec.id`는 불변 신원 키이며(F1), 미리보기는 `NativePlayer`와 surface 소유권을 주고받지 않고 전용 `SurfaceView`를 쓴다(F2).
