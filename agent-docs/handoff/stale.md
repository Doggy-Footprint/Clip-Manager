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
---

File: 4f1c7a93be05d268-media-grid-contract-tests.md
Summary: MediaStore 미디어 그리드 계약(v3) 기반 테스트 작성 중 contract-workflow 라운드 한도 도달; reload()/다중소스×필터/주입 필터 반영 3건이 seed로 확인된 미검출 결함
Related Files: agent-docs/contracts/media-grid.md, core/data/src/main/java/com/doggy/clip_manager/core/data/media/MediaFilter.kt, core/data/src/main/java/com/doggy/clip_manager/core/data/media/MediaRepository.kt, feature/browser/src/main/java/com/doggy/clip_manager/feature/browser/MediaGridViewModel.kt, core/data/src/test/java/com/doggy/clip_manager/core/data/media/MediaAggregationTest.kt, core/data/src/test/java/com/doggy/clip_manager/core/data/media/DefaultMediaRepositoryTest.kt, feature/browser/src/test/java/com/doggy/clip_manager/feature/browser/MediaGridViewModelTest.kt, feature/browser/src/test/java/com/doggy/clip_manager/feature/browser/MediaGridScreenScreenshotTest.kt
Related Symbols: MediaAggregation, MediaFilter, MediaSource, MediaRepository, DefaultMediaRepository, MediaStoreMediaSource, MediaGridViewModel, MediaGridScreen, MediaEntry, MediaKind
---
File: 8948e73064d17e84-editor-effects-ui-preview-freeze.md
Summary: Phase 2 effects UI done (preview single-item, export SEGMENT_CONCAT, R1 pass); verifier limit hit on F-AUDIT-2 (updateSpeed invalid-step case missing)
Related Files: core/editor/src/main/java/com/doggy/clip_manager/core/editor/EditedMediaItemFactory.kt, core/editor/src/main/java/com/doggy/clip_manager/core/editor/PreciseTransformEditor.kt, feature/editor/src/main/java/com/doggy/clip_manager/feature/editor/OverlayPreviewPlayer.kt, feature/editor/src/main/java/com/doggy/clip_manager/feature/editor/EditorViewModel.kt, feature/editor/src/main/java/com/doggy/clip_manager/feature/editor/EditorLogic.kt, feature/editor/src/main/java/com/doggy/clip_manager/feature/editor/EditorToolPanel.kt, agent-docs/spec-logs/547fb213daf79144-editor-effects-ui.md, core/editor/src/main/java/com/doggy/clip_manager/core/editor/EditPlanner.kt
Related Symbols: EditedMediaItemFactory, OverlayPreviewPlayer, EditorViewModel, toOutputOverlays, editorExportSpec, frameLayoutOf, RatioPreset, SPEED_STEPS, CompositionPlayer, buildPreview, TimeBasedFlip, ConcatStrategy.SEGMENT_CONCAT
---
File: 4d5556d9c2c3b575-compile-time-extension-points.md
Summary: Planned compile-time extension points (tab, settings section, built-in tab override), settings screen with hi/bye dummy clicker, optional :ext:* module include via local.properties; not started
Related Files: app/src/main/java/com/doggy/clip_manager/ui/TabLayer.kt, app/src/main/java/com/doggy/clip_manager/ui/ClipApp.kt, settings.gradle.kts, app/build.gradle.kts, gradle/libs.versions.toml, core/database/src/main/java/com/doggy/clip_manager/core/database/di/DatabaseModule.kt, build-logic/convention/src/main/kotlin/AndroidRoomConventionPlugin.kt
Related Symbols: MediaTab, TabLayer, ClipApp, MediaGridRoute, ClipDatabase, TabExtension, SettingsSection, BuiltInTabOverride
