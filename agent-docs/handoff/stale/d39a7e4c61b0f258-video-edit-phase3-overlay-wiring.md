# Video edit Phase 3 overlay wiring

## Goal
"contract workflow 작성하고 구현 시작해."

## State
- Branch: `feat/edit`
- Base commit: `f85c1bb`
- Changed implementation: overlay spec/planning/export, MediaStore image repository/grid, CompositionPlayer preview, image overlay controls, and module dependencies.
- Changed tests: `core/editor/src/test/java/com/doggy/clip_manager/core/editor/OverlaySpecTest.kt`.
- `git diff --check` passed.
- Gradle compilation could not begin because the configured VS Code JRE has no `jlink`, and no local Java runtime was found.

## Failed Attempts
| attempt | failure evidence | cause |
|---|---|---|
| Contract v1 implementation | implementer reported undefined source/style/transform/preview lifecycle signatures | verified contract gap |
| Contract v2 implementation | implementer reported no owner that converts an image selection into an `ImageOverlay` with id/range or invokes/reverts preview | verified contract gap |
| JVM test compilation | `jlink` absent at the configured VS Code JRE path | verified environment configuration |

## Next Step
Define the app-level edit state and interaction: where an image-grid selection receives its generated id and output-time start/end, which control starts preview, and which control returns from preview. Amend the contract, wire `ClipApp`/editor state to `OverlayEditSession`, then run `./gradlew testDebugUnitTest` with a valid JDK.

## Open Questions
- Which Phase 3 UI captures start/end output times and creates the `ImageOverlay` after MediaStore selection?
- Which app action enters CompositionPlayer preview and which action restores the normal viewer?

## Contract Snapshot
v3 defined `TextOverlay` and `ImageOverlay` under `OverlaySpec`; text defaults are bottom-center 20pt system font, white, transparent background, and centered. Image transforms are normalized position 0..1, positive scale, alpha 0..1, and finite rotation degrees. Ranges clip to output duration and empty intersections are dropped. `MediaStoreImageRepository` supplies image grid assets; unreadable images yield `InputNotReadableException`. `OverlayEditSession` is session-only. `OverlayPreviewPlayer` attaches a CompositionPlayer to a `PlayerView` and renders Phase 3 overlays only. `ImageOverlayEditor` presents selected-image white bounds with delete, rotation, resize, and opacity controls. The missing caller/state wiring prevents construction of image overlays and preview lifecycle integration.
