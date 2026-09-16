File: 53e73c46f4984a4c-ffmpeg-native-player.md
Summary: FFmpeg 네이티브(NDK) 플레이어 엔진 채택, ffmpeg-kit aar의 .so 재사용, 키프레임 인덱스 seek 병합
Related Files: app/src/main/cpp/CMakeLists.txt, app/src/main/cpp/player/Player.cpp, app/src/main/cpp/player/SeekController.h, app/src/main/java/com/doggy/clip_manager/player/NativePlayer.kt, app/src/main/java/com/doggy/clip_manager/player/ScrubThrottle.kt, scripts/fetch-ffmpeg-headers.sh, app/src/main/jniLibs, app/build.gradle.kts
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
