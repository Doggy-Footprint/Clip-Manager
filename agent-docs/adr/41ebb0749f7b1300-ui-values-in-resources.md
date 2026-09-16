# UI 값 하드코딩 금지

Status: Accepted

## Decision
- UI에 쓰는 색상, 문자열(포맷 문자열 포함), 치수는 코드에 리터럴로 쓰지 않는다. 각 모듈의 `res/values/colors.xml`, `strings.xml`, `dimens.xml`에 정의하고 `colorResource`, `stringResource`, `dimensionResource`로 참조한다.
- 리소스 이름은 모듈 접두사를 붙인다: `core/designsystem`은 `clip_`, feature 모듈은 `feature_<name>_`.
- 로직 상수(seek 임계값, 비디오 확장자 목록, DB 이름 등)는 이 규칙에 포함하지 않는다.
- 테스트 코드의 샘플 데이터는 이 규칙에 포함하지 않는다.

## Alternatives
- Kotlin 상수 파일(`Color.kt`의 `Color(0x...)` 값)과 Composable 안의 `dp` 리터럴: 이 방식에서 리소스 파일 방식으로 옮겼다.

## Consequences
- Positive: 색상, 문구, 간격을 Kotlin 코드를 수정하지 않고 리소스 파일에서 바꿀 수 있고, 한정자(`values-night`, `values-sw600dp`, 언어)로 분기할 수 있다.
- Negative: 색상 스킴 생성이 `@Composable` 컨텍스트를 요구한다.
