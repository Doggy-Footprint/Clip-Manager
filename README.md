# clip-manager

Android용 파일 탐색기와 비디오 뷰어입니다. FFmpeg 6.0 네이티브(NDK) 플레이어를 쓰며, MPEG-TS seek 지연을 줄이는 데 초점을 둡니다.

## 요구 사항

- Android Studio. 빌드에는 Android Studio에 포함된 JBR(JDK 21)을 씁니다.
- Android SDK Platform 37, NDK 28.2. 없으면 첫 빌드 때 자동으로 설치됩니다.
- 대상 기기의 ABI는 `arm64-v8a` 또는 `x86_64`여야 합니다.
- 호스트 C++ 컴파일러(`c++`)가 필요합니다. 네이티브 테스트에 씁니다.

셸의 `JAVA_HOME`이 `jlink`가 없는 JRE(예: VS Code의 Java 확장 JRE)를 가리키면 빌드가 실패합니다. 이때는 Android Studio JBR을 지정하세요.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

`JAVA_HOME`을 고쳐도 아래 오류가 계속 나올 수 있습니다.

```
Failed to transform core-for-system-modules.jar ...
  > jlink executable /Users/.../.vscode/extensions/redhat.java-.../jre/.../bin/jlink does not exist
```

`gradle/gradle-daemon-jvm.properties`는 데몬 JVM 조건을 버전(21)으로만 지정하므로, Gradle이 호스트에서 찾은 아무 JDK 21이나 고를 수 있고 여기에 VS Code의 JRE가 포함됩니다. 한 번 그 JVM으로 뜬 데몬은 `JAVA_HOME`을 바꿔도 재사용되므로, 데몬을 먼저 내려야 합니다.

```bash
./gradlew --stop
```

매번 명시하려면 자동 탐색을 끄고 JBR만 지정하세요.

```bash
./gradlew <task> \
  -Dorg.gradle.java.installations.auto-detect=false \
  -Dorg.gradle.java.installations.paths="$JAVA_HOME"
```

## git에 없는 파일 준비

다음 파일은 커밋하지 않으므로 직접 준비해야 합니다.

| 경로 | 내용 | 얻는 방법 |
|---|---|---|
| `core/player/src/main/jniLibs/{arm64-v8a,x86_64}/` | `libavcodec.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`, `libswscale.so`, `libc++_shared.so` | ffmpeg-kit `6.0-2.LTS` full-gpl aar의 `jni/<abi>/`에서 꺼냅니다. 로컬에만 보관합니다. |
| `core/player/src/main/cpp/include/` | FFmpeg 6.0 헤더 | `./scripts/fetch-ffmpeg-headers.sh` |
| `keystore.properties`, `release.jks` | 릴리스 서명 정보 | 아래 [릴리스 빌드](#릴리스-빌드) 참고 |
| `.env` | 로컬 환경 변수 | — |

### worktree

`git worktree add`로 worktree를 만들면 `.worktreeinclude`에 적힌 파일이 메인 worktree에서 자동으로 복사됩니다. worktree를 만든 도구가 무엇이든 동작하고, 이미 있는 파일은 덮어쓰지 않습니다.

- 복사 로직은 `.harness/git/post-checkout` 훅에서 `scripts/sync-worktree-files.sh`를 호출하는 구조입니다.
- 훅이 동작하려면 `git config core.hooksPath .harness/git`이 설정되어 있어야 합니다(harness 설치 시 설정됨).
- 수동으로 복사하려면 새 worktree 안에서 `scripts/sync-worktree-files.sh`를 실행하세요.

## 기기 테스트용 에뮬레이터 (AVD)

앱을 설치해서 확인할 때는 1280x800 태블릿 AVD를 씁니다(`agent-docs/adr/c3ff15cc5c14f11f-emulator-device-testing.md`).

1. **cmdline-tools 설치** (`avdmanager`, `sdkmanager`가 없을 때)

   Android Studio의 SDK Manager → SDK Tools에서 "Android SDK Command-line Tools"를 설치합니다.

2. **system image 받기**

   Apple Silicon Mac은 `arm64-v8a`, Intel Mac은 `x86_64` 이미지를 받습니다.

   ```bash
   SDK=~/Library/Android/sdk
   $SDK/cmdline-tools/latest/bin/sdkmanager "emulator" "system-images;android-35;google_apis;arm64-v8a"
   ```

3. **AVD 만들기**

   ```bash
   echo no | $SDK/cmdline-tools/latest/bin/avdmanager create avd \
     -n clip_tablet_1280x800 -k "system-images;android-35;google_apis;arm64-v8a" -d pixel_c
   C=~/.android/avd/clip_tablet_1280x800.avd/config.ini
   sed -i '' -E 's/^hw.lcd.width=.*/hw.lcd.width=1280/; s/^hw.lcd.height=.*/hw.lcd.height=800/; s/^hw.lcd.density=.*/hw.lcd.density=160/; s/^hw.initialOrientation=.*/hw.initialOrientation=landscape/' $C
   ```

4. **AVD 실행**

   등록된 AVD 목록을 확인하고 에뮬레이터를 백그라운드로 실행합니다.

   ```bash
   SDK=~/Library/Android/sdk

   # 등록된 AVD 목록 확인
   $SDK/emulator/emulator -list-avds

   # 에뮬레이터 백그라운드 실행 및 부팅 대기
   $SDK/emulator/emulator -avd clip_tablet_1280x800 &
   adb wait-for-device
   ```

   > [!NOTE]
   > 에뮬레이터의 MediaCodec 디코더(`c2.goldfish.*`)는 물리 기기와 동작이 다릅니다.

## 디버그 빌드 및 설치

### 1. 빌드 및 기기 설치

```bash
# 디버그 APK 빌드 (app/build/outputs/apk/debug/app-debug.apk)
./gradlew :app:assembleDebug

# 연결된 기기/에뮬레이터에 설치
./gradlew :app:installDebug
```

또는 빌드된 APK를 `adb`로 직접 설치할 수 있습니다.

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. 권한 설정 및 테스트 파일 복사

첫 실행 시 "모든 파일 접근"(API 30+) 또는 저장소 읽기(API 29 이하) 권한을 허용해야 합니다. 설정 화면 대신 `adb`로 즉시 허용할 수 있습니다.

```bash
adb shell appops set com.doggy.clip_manager MANAGE_EXTERNAL_STORAGE allow
```

테스트 영상은 [실기기 seek 측정](#실기기-seek-측정)의 방법으로 만들어 `adb push`로 기기에 넣습니다.

```bash
adb shell mkdir -p /sdcard/test && adb push seek_gop2s.ts seek_gop10s.ts /sdcard/test/
```

## 앱 실행 및 디버깅

### 1. 일반 실행 및 종료

```bash
# 앱 실행 (MainActivity)
adb shell am start -n com.doggy.clip_manager/.MainActivity

# 앱 종료 (강제 종료)
adb shell am force-stop com.doggy.clip_manager
```

### 2. 디버깅 실행 방법

#### Android Studio GUI 디버깅
- **디버그 실행**: 대상 기기(AVD)를 선택한 후 **Debug 'app'** (`Shift + F9` 또는 상단 벌레 아이콘)을 실행합니다.
- **C/C++ 네이티브(NDK) 디버깅**: FFmpeg 플레이어 네이티브 C++ 코드(`core:player`) 디버깅이 필요하면 **Run > Edit Configurations... > app > Debugger** 탭에서 **Debug type**을 `Dual (Java + Native)`로 설정합니다. 이렇게 하면 Kotlin UI 코드와 C++ 플레이어 코드 양쪽에 브레이크포인트를 걸 수 있습니다.

#### CLI에서 디버그 모드로 실행 후 디버거 연결 (Attach Debugger)
앱을 시작할 때 디버거 연결 대기 상태로 진입하도록 실행할 수 있습니다.

```bash
# 디버거 대기 모드로 앱 실행 (화면에 'Waiting For Debugger' 대화상자 표시)
adb shell am start -D -n com.doggy.clip_manager/.MainActivity
```

앱이 대기 상태가 되면 Android Studio 상단 메뉴의 **Run > Attach Debugger to Android Process**를 클릭하고 `com.doggy.clip_manager` 프로세스를 선택하여 디버거를 연결합니다.

### 3. 로그 확인 (Logcat)

플레이어 주요 동작 로그를 필터링하여 확인합니다.

```bash
adb logcat -v time -s ClipPlayer
```

주요 로그 줄:
- `seek request`: seek 요청이 들어옴
- `seek executed ... prepare_ms`: seek 실행, keyframe 탐색에 걸린 시간
- `first frame after seek latency_ms`: 요청부터 첫 디코딩 프레임까지 걸린 시간

앱 프로세스 전체 로그를 확인하려면 프로세스 ID(pid)로 필터링합니다.

```bash
adb logcat --pid=$(adb shell pidof -s com.doggy.clip_manager)
```

## 릴리스 빌드

리포지토리 루트에 `keystore.properties`를 두면 릴리스 APK에 서명합니다. 이 파일은 gitignore되어 있습니다.

```properties
storeFile=release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

키스토어가 없으면 새로 만드세요.

```bash
keytool -genkeypair -v -keystore release.jks -alias clip-manager -keyalg RSA -keysize 4096 -validity 10000
```

빌드와 설치:

```bash
./gradlew :app:assembleRelease    # → app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

`keystore.properties`가 없으면 서명되지 않은 `app-release-unsigned.apk`가 만들어집니다. 이 파일은 기기에 설치할 수 없습니다.

## 확장 모듈 (커스터마이징)

앱 저장소를 수정하지 않고 탭 추가, 설정 섹션 추가, 내장 탭(파일/비디오/오디오/이미지) 화면 교체를 빌드에 포함할 수 있습니다. 배경은 ADR `agent-docs/adr/fd4b110cca047a16-compile-time-extension-modules.md`를 보세요.

### 1. 연결

`local.properties`에 확장 모듈들이 들어 있는 디렉터리를 지정합니다. 그 아래에서 `build.gradle.kts`가 있는 하위 디렉터리마다 `:ext:<디렉터리명>` 모듈로 include되고 `:app`에 포함됩니다. 속성을 지우면 확장 없이 빌드됩니다.

```properties
extensionModules.dir=/path/to/extensions
```

```
/path/to/extensions/
└── mytab/
    ├── build.gradle.kts
    └── src/main/java/...
```

### 2. 모듈 빌드 스크립트

```kotlin
plugins {
    alias(libs.plugins.clip.android.feature)
}

android {
    namespace = "com.example.mytab"
}

dependencies {
    implementation(projects.core.extensionApi)
}
```

### 3. 확장 지점 구현과 등록

| 인터페이스 | 용도 |
|---|---|
| `TabExtension` | 탭 레일에 탭 추가. 선택하면 `Content`가 explorer 영역에 표시됨 |
| `SettingsSection` | 설정 화면에 섹션 추가 |
| `BuiltInTabOverride` | 내장 탭 하나의 explorer 화면 교체. `default(Modifier)`로 원래 화면을 함께 배치할 수 있음 |
| `LocalExtensionNavigator` | 확장 Composable 안에서 `openSettings(sectionId)`로 설정 화면으로 이동 |

```kotlin
class MyTab @Inject constructor() : TabExtension {
    override val id = "mytab"
    override val icon = Icons.Default.Star
    override val order = 0

    @Composable override fun label() = stringResource(R.string.mytab_label)

    @Composable override fun Content(modifier: Modifier) {
        val navigator = LocalExtensionNavigator.current
        Button(onClick = { navigator.openSettings("mytab-settings") }, modifier = modifier) {
            Text(stringResource(R.string.mytab_open_settings))
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MyTabModule {
    @Binds @IntoSet abstract fun tab(impl: MyTab): TabExtension
}
```

`SettingsSection`, `BuiltInTabOverride`도 같은 방식으로 `@Binds @IntoSet` 등록합니다.

### 4. 규칙

- 탭과 설정 섹션은 `order`가 작은 순, 같으면 `id` 순으로 표시됩니다.
- 같은 내장 탭에 `BuiltInTabOverride`가 둘 이상 등록되면 앱 실행 시 오류가 납니다.
- `openSettings(sectionId)`의 `sectionId`는 `SettingsSection.id`이며, 해당 섹션까지 스크롤합니다. 없는 id면 설정 화면만 엽니다.
- 확장을 추가하거나 제거하면 앱을 다시 빌드해야 합니다.

## 테스트

### 자동 테스트

```bash
./gradlew testDebugUnitTest       # JVM + Robolectric: 모든 모듈 단위 테스트
./gradlew verifyRoborazziDebug    # 스크린샷 비교 (기준 갱신: recordRoborazziDebug)
./scripts/test-native.sh          # 호스트 C++: SeekController
```

### 실기기 seek 측정

네이티브 플레이어 동작(HW/SW 디코딩, A/V sync, seek latency)은 자동 테스트가 없어서 실기기에서 확인합니다.

1. **테스트 영상 만들기**

   화면에 1/100초 단위 카운터가 표시되는 영상입니다.

   ```bash
   for g in 2 10; do
     ffmpeg -f lavfi -i "testsrc=size=1920x1080:rate=30:duration=600:decimals=2" \
            -f lavfi -i "sine=frequency=880:beep_factor=4:sample_rate=48000:duration=600" \
            -c:v libx264 -preset veryfast -b:v 6M -g $((g*30)) -keyint_min $((g*30)) -sc_threshold 0 -pix_fmt yuv420p \
            -c:a aac -b:a 128k -ac 2 -f mpegts seek_gop${g}s.ts
   done
   ```

   keyframe 간격이 2초인 파일과 10초인 파일이 만들어집니다.

2. **기기에 복사**

   ```bash
   adb shell mkdir -p /sdcard/test && adb push seek_gop2s.ts seek_gop10s.ts /sdcard/test/
   ```

3. **seek 동작 확인**

   앱에서 파일을 열고 seek bar를 탭하거나 드래그합니다. 화면 카운터가 목표 위치 **이전**의 keyframe 값을 보여 주는지, seek했는데 프레임이 나오지 않는 경우가 없는지 확인합니다. 예: GOP 10초 파일에서 438초를 탭하면 430.00이 보여야 합니다.

4. **녹화로 정량 측정 (선택)**

   ```bash
   adb shell screenrecord --bit-rate 20000000 --time-limit 180 /sdcard/test/rec.mp4
   ```

   녹화에서 seek bar thumb가 움직인 프레임부터 카운터가 바뀐 프레임까지의 시간을 비교합니다.
