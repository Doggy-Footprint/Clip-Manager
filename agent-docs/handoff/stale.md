# Stale Index Archive
<!-- harness:stale-index-archive -->

File: c80f1fb03e5b43fb-video-edit-phase2-onward.md
Summary: Video editing phase 1 (core/editor engine) done; phases 2-4 (aspect/flip/speed, overlays+preview, tool UI) with confirmed decisions and failed attempts
Related Files: core/editor/src/main/java/com/doggy/clip_manager/core/editor/PreciseTransformEditor.kt, core/editor/src/main/java/com/doggy/clip_manager/core/editor/FastMuxEditor.kt, core/editor/src/main/java/com/doggy/clip_manager/core/editor/EditSpec.kt, core/editor/src/main/java/com/doggy/clip_manager/core/editor/EditService.kt, app/src/main/java/com/doggy/clip_manager/ui/ClipApp.kt
Related Symbols: EditSpec, EditPlanner, VideoEditor, EditService, PreciseTransformEditor, FastMuxEditor, SlowDetector, ExpectedTimeModel
---
File: 4a1d9c7e3b6f2058-video-edit-phase2-effects-complete.md
Summary: Phase 2 aspect layout, interval flip, pitch-preserving speed, and validation completed in f85c1bb
Related Files: core/editor/src/main/java/com/doggy/clip_manager/core/editor/EditSpec.kt, core/editor/src/main/java/com/doggy/clip_manager/core/editor/EditPlanner.kt, core/editor/src/main/java/com/doggy/clip_manager/core/editor/PreciseTransformEditor.kt, agent-docs/requirements/e26e8e199edeebb3-video-edit-phase2-effects.md
Related Symbols: EditSpec, EditPlanner, PreciseTransformEditor, VideoEditor, EditService
---
File: 7e2b4f8a1c609d35-video-edit-phase3-overlays-preview.md
Summary: Pending Phase 3 interval subtitle/text/image overlays and CompositionPlayer preview
Related Files: core/editor/src/main/java/com/doggy/clip_manager/core/editor/PreciseTransformEditor.kt
Related Symbols: PreciseTransformEditor
---
File: d39a7e4c61b0f258-video-edit-phase3-overlay-wiring.md
Summary: Phase 3 implementation is partial; app-level overlay creation and preview lifecycle remain undefined
Related Files: app/src/main/java/com/doggy/clip_manager/ui/ClipApp.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/OverlayPreviewPlayer.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/ImageOverlayEditor.kt
Related Symbols: OverlayEditSession, OverlayPreviewPlayer, ImageOverlayEditor
---
File: e4088f09a4569101-video-edit-phase3-overlays-complete.md
Summary: Phase 3 overlay engine, validation, export mapping, session and stateless preview/editor components completed and verified by JVM tests and a full debug build
Related Files: core/editor/src/main/java/com/doggy/clip_manager/core/editor/OverlaySpec.kt, core/editor/src/main/java/com/doggy/clip_manager/core/editor/OverlayCompositionFactory.kt, core/editor/src/main/java/com/doggy/clip_manager/core/editor/PreciseTransformEditor.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/OverlayPreviewPlayer.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/ImageOverlayEditor.kt, feature/browser/src/main/java/com/doggy/clip_manager/feature/browser/ImageGridScreen.kt, agent-docs/requirements/f9a6261093f74075-video-edit-phase3-overlays.md
Related Symbols: OverlaySpec, TextOverlay, ImageOverlay, OverlayTransform, OverlayEditSession, OverlayCompositionFactory, EditPlanner, PreciseTransformEditor, OverlayPreviewPlayer, ImageOverlayEditor, ImageGridScreen, MediaStoreImageRepository
---
File: 9c41d7f2a05e8b63-video-edit-phase3-closeout.md
Summary: Phase 3 closed out: overlay Intent serialization for the service export path, two production overlay bugs fixed, instrumented render tests and a debug verification screen
Related Files: core/editor/src/main/java/com/doggy/clip_manager/core/editor/EditService.kt, core/editor/src/main/java/com/doggy/clip_manager/core/editor/OverlayCompositionFactory.kt, core/editor/src/main/java/com/doggy/clip_manager/core/editor/OverlaySpec.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/OverlayPreviewPlayer.kt, app/src/debug/java/com/doggy/clip_manager/debug/OverlayDebugActivity.kt, core/editor/src/androidTest/java/com/doggy/clip_manager/core/editor/OverlayRenderTest.kt, core/editor/src/test/java/com/doggy/clip_manager/core/editor/EditServiceIntentTest.kt
Related Symbols: EditService, OverlayCompositionFactory, OverlayEditSession, OverlayPreviewPlayer, OverlayDebugActivity, OverlayRenderTest, EditServiceIntentTest
---
File: b5c8e1d3a7f09264-video-edit-phase4-tool-ui.md
Summary: Pending Phase 4 editor tool UI and explorer integration; lists the Phase 3 components awaiting app-level wiring
Related Files: app/src/main/java/com/doggy/clip_manager/ui/ClipApp.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/OverlayPreviewPlayer.kt, feature/browser/src/main/java/com/doggy/clip_manager/feature/browser/ImageGridScreen.kt
Related Symbols: ClipApp, OverlayEditSession, OverlayPreviewPlayer, ImageOverlayEditor, ImageGridScreen
---

File: b84d0b34cae0af1d-editor-ui-module-split.md
Summary: Decision and plan to extract the editor UI from :feature:player into a new :feature:editor module; engine already isolated in :core:editor
Related Files: settings.gradle.kts, feature/player/build.gradle.kts, feature/player/src/main/java/com/doggy/clip_manager/feature/player/EditorPane.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/EditorToolPanel.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/EditorViewModel.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/EditorLogic.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/ImageOverlayEditor.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/OverlayPreviewPlayer.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/PlayerScreen.kt, app/src/main/java/com/doggy/clip_manager/ui/ClipApp.kt, feature/browser/src/main/java/com/doggy/clip_manager/feature/browser/ImageGridRoute.kt
Related Symbols: EditorPane, EditorToolPanel, EditorViewModel, EditorLogic, ImageOverlayEditor, OverlayPreviewPlayer, PlayerPane, ClipApp, ImageGridRoute, AndroidFeatureConventionPlugin
---
