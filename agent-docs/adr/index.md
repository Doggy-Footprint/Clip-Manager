File: 53e73c46f4984a4c-ffmpeg-native-player.md
Summary: FFmpeg 네이티브(NDK) 플레이어 엔진 채택, ffmpeg-kit aar의 .so 재사용, 키프레임 인덱스 seek 병합
Related Files: core/player/src/main/cpp/CMakeLists.txt, core/player/src/main/cpp/player/Player.cpp, core/player/src/main/cpp/player/SeekController.h, core/player/src/main/java/com/doggy/clip_manager/core/player/NativePlayer.kt, core/player/src/main/java/com/doggy/clip_manager/core/player/ScrubThrottle.kt, scripts/fetch-ffmpeg-headers.sh, core/player/src/main/jniLibs, build-logic/convention/src/main/kotlin/AndroidLibraryNdkConventionPlugin.kt
Related Symbols: clip::Player, clip::SeekController, NativePlayer, ScrubThrottle, SeekMode
---
File: e5d20995f1647023-hilt-di.md
Summary: Hilt 의존성 주입 채택
Related Files: build-logic/convention, app/src/main/java/com/doggy/clip_manager/ClipManagerApplication.kt, core/data, core/database
Related Symbols: ClipManagerApplication, DataModule, DatabaseModule
---
File: 4c31127c8ad17a51-room-database.md
Summary: Room 로컬 데이터베이스 채택
Related Files: core/database, core/database/schemas
Related Symbols: ClipDatabase, RecentPlaybackDao, RecentPlaybackEntity
---
File: c2cd0adce6a4bcea-multi-module.md
Summary: NiA식 app/core/feature 멀티모듈과 convention plugin 채택, NiA 포크 기각
Related Files: settings.gradle.kts, build-logic/convention, core, feature
Related Symbols: AndroidApplicationConventionPlugin, AndroidLibraryConventionPlugin, AndroidFeatureConventionPlugin
---
File: 87ecd598db2d116a-roborazzi-screenshot-test.md
Summary: Roborazzi 스크린샷 테스트 채택
Related Files: build-logic/convention, core/designsystem/src/test, feature/browser/src/test
Related Symbols: RoborazziConventionPlugin
---
File: 41ebb0749f7b1300-ui-values-in-resources.md
Summary: UI 색상·문자열·치수 하드코딩 금지, 모듈별 리소스 파일로 정의
Related Files: core/designsystem/src/main/res/values/colors.xml, core/designsystem/src/main/java/com/doggy/clip_manager/core/designsystem/theme/Theme.kt, feature/player/src/main/res/values, feature/browser/src/main/res/values
Related Symbols: ClipTheme, EmptyState, PlayerScreenRoute, BrowserScreen
---
File: c3ff15cc5c14f11f-emulator-device-testing.md
Summary: 기기 테스트는 1280x800 태블릿 AVD에서 수행
Related Files: README.md
Related Symbols: none
---
File: 9d4b2e71a0c83f56-three-layer-single-screen.md
Summary: NavHost를 제거하고 tab/explorer/viewer 3-layer를 한 화면에 적응형으로 배치
Related Files: app/src/main/java/com/doggy/clip_manager/ui/ClipApp.kt, app/src/main/java/com/doggy/clip_manager/ui/TabLayer.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/PlayerViewModel.kt, feature/player/src/main/java/com/doggy/clip_manager/feature/player/PlayerScreen.kt, feature/browser/src/main/java/com/doggy/clip_manager/feature/browser/BrowserScreen.kt
Related Symbols: ClipApp, TabLayer, PlayerPane, PlayerViewModel.open, BrowserScreenRoute
