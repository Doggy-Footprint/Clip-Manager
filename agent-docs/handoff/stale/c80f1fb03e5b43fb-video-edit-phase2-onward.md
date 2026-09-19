# 동영상 편집 기능: 2단계부터 이어서 진행

## Goal
사용자 원문:

> 동영상 편집 기능을 넣으려고 해.
>
> 1. cut & concat: 앞, 뒤, 중간에서 삭제할 부분을 선택해서 제거하고 남은 부분을 합치기
> 2. clipping: 일부만 잘라내서 저장
> 3. 화면 비율 (crop / stretch), 상하좌우 반전
> 4. 구간 배속 제어: 0.5x ~ 2.0x
> - 오디오 피치 왜곡 방지 포함.
> 5. 구간 자막 / 텍스트 오버레이 / 이미지 오버레이
> 6. 실시간 진행률 표시, 중단 기능, 앱 최소화시 계속 진행 방법
> 7. 해당 효과에 대한 미리보기
> 8. 이 기능들을 넣을 UI. - 편집 기능을 활성화하면 explorer layer가 도구 모음, 이미지/음성 소스 제공 역할을 함. 도구 등은 viewer layer에서 움직일 수 있는 overlay로 조작 및 사용하다가 explorer layer에 밀어넣으면 아이콘으로 변경. viewer 전체화면의 경우 왼쪽에 도구모음을 보여줌 (투명한 배경의 아이콘 묶음, on/off 하면 도구 overlay나옴, 소스는 필요할 경우 explorer layer를 window로 띄워
>
> - 모바일 환경이라 처리속도에 대해서 신경을 써야해. 다만, 작업을 중단할 수는 없어서 "일반적으로 예상되는 시간보다 오래 걸리는 경우"를 탐지할 수 있으면 좋겠어
> - 당연히 정확한 결과물이 나오는지도 테스트에 들어가야 해
> - 구간 처리 작업이 많아서 이어 붙이기 방식의 처리가 가능한지 확인하고 싶음

진행 방식: 단계별로 진행하고, 단계마다 contract-workflow(`.claude/skills/contract-workflow`)로 구현한 뒤 커밋.

## State
- Branch: `emdash/video-edit-equdn`
- Commit: `e12e20f` (1단계 완료)
- 1단계에서 바뀐 파일:
  - `settings.gradle.kts`: `:core:editor` 등록
  - `gradle/libs.versions.toml`: media3 1.11.1, desugar_jdk_libs 2.1.5, androidx.test runner/ext-junit 추가
  - `app/build.gradle.kts`, `core/editor/build.gradle.kts`: core library desugaring 활성화(`java.time`, minSdk 24)
  - `core/editor/src/main/.../core/editor/`: EditSpec, EditPlanner, OutputNaming, SlowDetector, ExpectedTimeModel, CoefficientStore, EditState, EditJob, EditJobs, VideoEditor, EditService, FastMuxEditor, PreciseTransformEditor, MediaProbe
  - `core/editor/src/test`, `core/editor/src/androidTest`: 인스트루먼트 테스트 20개와 unit 테스트. 픽스처는 `scripts/generate-editor-fixtures.sh`로 생성해 assets에 둠
- 1단계에서 확정된 결정:
  - 엔진: Media3 Transformer. PRECISE는 재인코딩, FAST는 MediaExtractor→MediaMuxer 복사
  - 컷 모드: 사용자가 FAST/PRECISE를 토글. 효과가 있으면 PRECISE 강제. FAST의 시작점은 직전 키프레임으로 당김
  - 출력: 원본과 같은 폴더의 `<이름>_edit_<yyyyMMdd_HHmmss>.mp4`. 이름이 겹치면 `_1`, `_2`. 컨테이너는 mp4 고정
  - 백그라운드: Foreground Service(`mediaProcessing`), 알림에 진행률과 중단 액션. Hilt 없이 `EditJobs` 싱글턴
  - 지연 탐지: 추정 총시간 > 예상의 3배이면 SLOWER_THAN_EXPECTED, 30초 동안 진행 없으면 STALLED
  - 예상 계수: 기본값은 에뮬레이터 벤치마크로 정하기로 함. 현재 `EditService`의 값(FAST=150, PRECISE=1500 ms/출력초 @1080p)은 임시값
- I8(이어 붙이기) 벤치마크: 에뮬레이터 API 35 arm64, 픽스처 640x360 10s, logcat `EditorBench`

  | 모드 | SINGLE_COMPOSITION | SEGMENT_CONCAT |
  |---|---|---|
  | PRECISE (8s 출력) | ~1.9–2.0s | ~3.1s |
  | FAST (7s 출력) | ~350ms | ~550ms |

  두 방식 모두 길이, 경계 프레임, 오디오 조건을 통과함. SEGMENT_CONCAT이 약 1.5배 느림

## Failed Attempts
| attempt | failure evidence | cause |
|---|---|---|
| FAST SEGMENT_CONCAT v1: 세그먼트 파일 pts에 누적 offset을 넣은 뒤 concat에서 마지막 샘플 pts로 다시 offset | 출력 7_076_000us (기대 7s ±1프레임) | hypothesis: offset 이중 적용과 마지막 샘플 길이 누락 |
| 세그먼트 파일 pts를 0부터 쓰고 concat offset을 계획된 구간 길이로 변경 | 출력 2_008_000us (첫 세그먼트만 남음) | verified(다음 행): 세그먼트 1의 첫 오디오 샘플 pts가 -8000us |
| concat을 원본 트랙 포맷과 mime 기준 매핑, 트랙별 첫 샘플 기준으로 정규화하게 변경 | 출력 2_005_000us | verified: 비디오 키프레임으로 seek하면 구간 시작 전의 오디오가 포함되어 음수 pts가 됨. 그렇게 쓴 세그먼트 파일에서 샘플을 읽지 못함(logcat DBGMUX 계측). `muxRanges`에서 `sampleTime < range.startUs`인 샘플을 건너뛰어 해결 |
| contract 파일을 `agent-docs/contracts/`에 두고 세션을 넘김 | 새 세션에서 test-verifier가 파일을 찾지 못함 | verified: `.harness/hooks/cleanup.py`가 세션 종료 시 contracts 디렉터리를 삭제함 |
| 같은 worktree에서 다른 세션이 connectedDebugAndroidTest를 동시에 실행 | 테스트 프로세스가 signal 9로 종료, "Process crashed" | verified: 같은 에뮬레이터를 공유하는 instrumentation 실행끼리 서로 종료시킴 |

## Next Step
### 2단계: 화면 비율 · 반전 · 구간 배속 (contract-workflow)
사용자가 확정한 세부 사항:
- 비율: 프리셋(원본/16:9/9:16/1:1/4:3/3:4)과 가로·세로 값을 직접 입력하는 자유 비율
- 모드: crop(사용자가 위치 지정, 기본은 가운데), stretch(비율 무시), fit(레터박스, 검은 여백)
- 적용 범위: 비율은 결과물 전체. 반전(상하/좌우)은 구간별로도 적용 가능
- 배속: 구간별 0.5~2.0x, 0.25 단위(0.5/0.75/1.0/1.25/1.5/1.75/2.0). 오디오 피치 보존

설계 메모(contract 작성 시 확인할 것):
- `EditSpec.hasEffects: Boolean`을 효과 모델(비율, 반전, 구간 배속)로 대체하거나 보완해야 함. `EditService`는 spec을 Intent extra로 직렬화하므로 같이 확장해야 함
- `PreciseTransformEditor.buildComposition`은 구간별 `EditedMediaItem`을 만들므로, 구간별 반전과 배속은 item 단위 `Effects`로 붙일 수 있음
  - 배속: `setSpeed`/`SpeedChangeEffect`, 오디오는 `SonicAudioProcessor`
  - 반전: `ScaleAndRotateTransformation`(scaleX/Y = -1)
  - 비율: `Presentation`/`Crop`
- 구간 배속은 keepRange 안의 하위 구간일 수 있음. EditPlanner에서 keep 구간과 배속 구간의 교차를 `(구간, 속도, 반전)` 세그먼트로 분해해야 함. 출력 길이 = Σ(길이/속도). `ExpectedTimeModel`과 `SlowDetector`에 넘기는 outputDurationUs도 이 값으로 바꿔야 함
- 효과가 있으면 FAST는 PRECISE로 강제(기존 I3)
- 테스트 오라클 후보:
  - 출력 해상도와 비율
  - 반전은 비대칭 픽스처의 픽셀 색상(픽스처 A는 단색이라 새 픽스처 필요)
  - 배속은 출력 길이와 오디오 1kHz FFT 주파수 불변

### 3단계: 구간 자막/텍스트/이미지 오버레이, CompositionPlayer 미리보기
### 4단계: 도구 UI
- 편집 모드에서 explorer는 도구와 소스를 제공
- viewer 위의 드래그 가능한 overlay를 explorer에 드롭하면 아이콘으로 접힘
- 전체화면에서는 왼쪽에 투명 아이콘 묶음을 두고, 소스는 explorer를 floating window로 띄움
- 1단계에서 미뤘던 최소 UI(구간 선택, export 버튼, 진행률과 지연 경고 표시)와 Hilt 연결도 이 단계에서 처리
- 계획 원본: `~/.claude/plans/joyful-napping-clover.md`

## Open Questions
- ADR 제안(사용자 확인 전, 미작성):
  - "Media3 Transformer 편집 엔진": 대안은 FFmpeg avfilter/인코더 추가 빌드(SW 인코딩이 느리고 GPL 부담), NDK MediaCodec 직접 구현(구현량)
  - "구간 이어 붙이기 기본 방식": 벤치마크상 SINGLE_COMPOSITION이 빠름. SEGMENT_CONCAT은 중단 후 재개와 부분 재사용의 여지가 있음. 기본값을 무엇으로 할지 사용자 결정 필요
- 예상 계수 기본값을 정할 벤치마크를 실제 해상도(1080p) 픽스처로 다시 측정할지
- 2단계 비율 변경에서 출력 해상도 기준(원본 짧은 변 유지 등)은 아직 정하지 않음
- `EditService`의 임시 계수(150/1500)를 벤치마크 결과로 바꾸는 시점

## Contract Snapshot
none (1단계 contract `editor-engine.md` v3는 완료 후 삭제함. 최종 내용은 커밋 `e12e20f`의 코드와 테스트 이름(`<fn>_<id>_<level>_...`)으로 추적 가능)
