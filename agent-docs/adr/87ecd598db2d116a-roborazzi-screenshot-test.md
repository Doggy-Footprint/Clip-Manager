# Roborazzi 스크린샷 테스트

Status: Accepted

## Decision
- Compose 화면 스크린샷 테스트는 Roborazzi(Robolectric 위에서 JVM 실행)로 한다.
- 기준 이미지는 `recordRoborazziDebug`로 만들고 `verifyRoborazziDebug`로 검증한다.

## Alternatives
- Compose Preview Screenshot Testing(AGP 플러그인): 실험 단계 도구라서 기각했다.

## Consequences
- Positive: 기기나 에뮬레이터 없이 Gradle 태스크로 UI 회귀를 검사한다.
- Negative: Robolectric 렌더링이라 SurfaceView와 네이티브 코드를 쓰는 화면(PlayerScreen)은 검사할 수 없다.
