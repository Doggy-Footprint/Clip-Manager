# 동영상 편집 기능: 3단계 완료

## Goal
"부분 완료된 phase 3를 완료해줘."

## State
- Branch: `feat/edit`
- Base commit: `f85c1bb`
- 요구사항: `agent-docs/requirements/f9a6261093f74075-video-edit-phase3-overlays.md`
- 확정 범위는 엔진·세션·재사용 가능한 UI 컴포넌트까지이며, 앱 화면 연결은 4단계로 남긴다.
- Changed: `core/editor`(`OverlaySpec`, `OverlayCompositionFactory`, `EditPlanner`, `EditSpec`, `PreciseTransformEditor`, `VideoEditor`, build), `core/model`(`ImageAsset`), `core/data`(`MediaStoreImageRepository`, `DataModule`), `feature/browser`(`ImageGridScreen`, 리소스, build), `feature/player`(`OverlayPreviewPlayer`, `ImageOverlayEditor`, 리소스, build), 테스트 `OverlaySpecTest`.
- 3단계 범위 밖이던 `app`의 IMAGES 탭 임시 연결은 `f85c1bb` 상태로 되돌렸다.
- 검증: `./gradlew assembleDebug` 성공, `./gradlew testDebugUnitTest` 성공(`OverlaySpecTest` 62건 포함), `git diff --check` 통과.

## Failed Attempts
| attempt | failure evidence | cause |
|---|---|---|
| Contract v1 implementation | implementer reported undefined source/style/transform/preview lifecycle signatures | verified contract gap |
| Contract v2 implementation | implementer reported no owner that converts an image selection into an `ImageOverlay` with id/range or invokes/reverts preview | verified contract gap |
| JVM test compilation | `jlink` absent at the configured VS Code JRE path | verified environment configuration |
| `ImageSource(uri: android.net.Uri)` in `core:model` | `:core:model:compileKotlin` — `Unresolved reference 'android'` | verified: `core:model` is a JVM-only module (`clip.jvm.library`) |
| `androidx.media3.effect.OverlaySettings.Builder()` | `javap` on media3-effect 1.11.1 shows only `StaticOverlaySettings.Builder`; `OverlaySettings` lives in `androidx.media3.common` and is an interface | verified API shape |
| `./gradlew assembleDebug` with `JAVA_HOME` set | `jlink executable /Users/.../\.vscode/.../jre/.../bin/jlink does not exist` | verified: a stale daemon held the VS Code JRE as its JVM; `./gradlew --stop` first resolves it |

## Next Step
4단계(도구 UI)를 `agent-docs/handoff/b5c8e1d3a7f09264-video-edit-phase4-tool-ui.md`의 Open Questions부터 확정하고 진행한다.

## Open Questions
- 오버레이가 실제 GPU에서 의도한 위치·크기·구간으로 그려지는지는 계측 테스트를 범위에서 제외했으므로 검증되지 않았다. 특히 배속 구간에서 `presentationTimeUs`가 배속 적용 전후 어느 쪽인지 확인되지 않았다.
- `CompositionPlayer`와 FFmpeg `NativePlayer` 사이의 surface 소유권 전환 규칙.
- `OverlayEditSession.update`는 전달된 오버레이의 id로 슬롯을 찾아 그 자리에 통째로 덮어쓴다. 따라서 다른 오버레이의 id를 붙여 넘기면 그 오버레이의 내용이 조용히 사라진다. 현재 동작은 `overlayEditSession_R11_characterization_...` 테스트가 고정하고 있다. id를 신원 키로 유지할지, 별도의 대상 키를 받을지는 4단계에서 호출자가 정해지면 결정한다.

## Contract Snapshot
`OverlaySpec`은 `TextOverlay`와 `ImageOverlay`를 가지며 구간은 출력 시간축 기준이다. 자막은 별도 타입 없이 `TextOverlayStyle.Default`(하단 중앙·20pt·불투명 흰색·배경 없음·중앙 정렬) 프리셋으로 표현한다. `EditPlanner.validateOverlays`가 id·구간·스타일·변환을 동기 검증하고 `normalizeOutputOverlays`가 출력 길이로 자른다. `PreciseTransformEditor.buildComposition`이 두 concat 전략 모두에서 구간 매핑을 단독으로 수행한다. `ImageSource.uri`는 `core:model`이 JVM 전용이므로 문자열이다. `OverlayEditSession`은 세션 한정이다. `OverlayPreviewPlayer`는 `CompositionPlayer.setVideoSurface`로 기존 `SurfaceView`를 재사용하며 3단계 오버레이만 합성한다. `ImageGridScreen`과 `ImageOverlayEditor`는 상태 없는 컴포넌트다.
