# FFmpeg 네이티브 플레이어 엔진

Status: Accepted

## Context
MPEG-TS를 seek bar로 탐색할 때 사용자가 느끼는 지연을 최소화하는 것은 핵심 기술 병목이다.
`ffmpeg.so` 엔진을 이용해 다음과 같이 처리한다.
- 드래그 중에는 마지막으로 요청한 위치에서 500ms 넘게 벗어날 때만 scrub seek한다.
- 밀린 seek 요청 중 최신 것 하나만 처리한다.
- keyframe seek을 기본으로 한다.

## Decision
- 재생 엔진은 NDK로 직접 작성한다. FFmpeg 6.0으로 demux하고, 디코딩은 NDK MediaCodec을 먼저 쓰고 실패하면 avcodec으로 넘어간다. MediaCodec이 첫 입력 후 1초 안에 출력(포맷 변경 알림 포함)을 내지 않으면 그 파일은 avcodec으로 전환한다. 이 1초에는 코덱에 입력을 넣거나 출력을 기다린 시간만 포함하고, 일시정지나 demux 대기 시간은 포함하지 않는다. 오디오는 AAudio(API 26+) 또는 AudioTrack으로 재생한다.
- seek 요청 병합과 이전 결과 폐기는 `app/src/main/cpp/player/SeekController.h`의 generation 카운터로 처리한다.

## Alternatives
- Media3 ExoPlayer(TsExtractor + SeekParameters.CLOSEST_SYNC): seek 요청 병합, keyframe 도달 전 패킷 폐기, seek 직후 첫 프레임 즉시 표시를 엔진 내부에서 제어할 수 없어 기각했다.
- FFmpeg를 소스에서 직접 빌드: 빌드 스크립트와 툴체인을 유지해야 해서 기각했다. aar의 .so를 재사용한다.

## Consequences
- Positive: demux, 디코더 flush, 프레임 렌더 시점을 모두 직접 제어할 수 있다.
- Negative: ffmpeg-kit full-gpl 빌드를 쓰므로 앱 배포 시 GPL v3 조건이 적용된다. APK에 ABI당 약 20MB 이상의 FFmpeg 라이브러리가 포함된다.
- Negative: A/V sync, HW/SW fallback, AAudio/AudioTrack 분기를 직접 유지보수해야 한다.
