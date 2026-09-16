# 기기 테스트는 태블릿 에뮬레이터에서 수행

Status: Accepted

## Decision
- 앱을 기기에 설치해서 확인하는 테스트는 물리 기기가 아닌 Android 에뮬레이터(AVD)에서 한다.
- 기준 AVD는 해상도 1280x800 태블릿이다.

## Alternatives
- USB로 연결한 물리 기기에서 확인: 에뮬레이터 방식으로 대체했다.

## Consequences
- Positive: 물리 기기 연결 여부와 관계없이 같은 화면 크기와 OS 이미지로 확인할 수 있다.
- Negative: 에뮬레이터의 HW 디코더는 물리 기기와 달라서, MediaCodec 경로와 seek latency 측정 결과는 실제 기기 성능을 대표하지 않는다.
