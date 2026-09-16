# 에이전트의 스크린샷 판독으로 테스트 대체

## Context
Roborazzi 스크린샷 테스트(`PlayerScreenScreenshotTest`)에서 아이콘 상태(P2)와 흰 배경 가독성(P4)은 코드 assertion으로 검사할 수 없었다.

## Rejected Alternative
테스트 검증 단계에서 메인 에이전트가 기준 이미지를 읽고 계약의 기대 결과와 일치하는지 판정하여 assertion을 대신한다.

## Reason
MMLM의 이미지 판독 비용이 테스트 실행마다 발생하여 테스트 비용이 과도하다.

## Scope
테스트 검증을 대체하는 용도에만 적용한다. 개발 중 UI 확인·디버깅을 위해 에이전트가 스크린샷을 읽는 것은 제한하지 않는다.

## Revisit Condition
유저가 명시적으로 요청할 때.

## Chosen Instead
Roborazzi `recordRoborazziDebug`/`verifyRoborazziDebug`의 픽셀 비교만 사용한다. 기준 이미지의 올바름은 사람이 판단한다. (feature/player/src/test/java/com/doggy/clip_manager/feature/player/PlayerScreenScreenshotTest.kt)
