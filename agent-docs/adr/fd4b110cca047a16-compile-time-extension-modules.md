# 컴파일 타임 확장 모듈과 Hilt multibinding 기반 커스터마이징

Status: Accepted

## Context
- 사용자별 커스터마이징(탭 추가, 설정 섹션 추가, 내장 탭 화면 교체)을 호스트 저장소에 커밋하지 않고 빌드에 포함할 수단이 필요하다.

## Decision
- 확장 계약은 `:core:extension-api`에 둔다: `TabExtension`, `SettingsSection`, `BuiltInTabOverride`, `ExtensionNavigator`/`LocalExtensionNavigator`.
- 확장은 Gradle 모듈로 작성하고, 각 인터페이스 구현을 Hilt `@IntoSet`으로 `SingletonComponent`에 기여한다. 호스트는 `@Multibinds` 집합으로 받으므로 기여가 없으면 빈 집합이다.
- `local.properties`의 `extensionModules.dir` 아래에서 `build.gradle.kts`가 있는 하위 디렉터리를 `:ext:<name>`으로 include하고, `:app`이 모두 `implementation`한다. 속성이 없으면 확장 없이 빌드된다.
- 정렬: 탭과 설정 섹션은 `(order, id)` 순서로 표시한다. 같은 `BuiltInTab`에 override가 둘 이상이면 실행 시 오류를 낸다.
- 확장이 호스트 화면으로 이동할 때는 `LocalExtensionNavigator`만 쓴다. 이 로컬은 `ClipApp` 안에서만 제공된다.

## Alternatives
- 런타임 플러그인(별도 APK/dex 로딩, 프로세스 간 UI): 코드 로딩에 따른 보안 위험, Play 정책 제약, 프로세스 간 UI 비용 때문에 기각했다.
- `ExtensionNavigator`를 Hilt 싱글톤으로 바인딩하고 요청을 SharedFlow로 전달하는 방식: 탭 상태가 `ClipApp`의 Compose state여서 CompositionLocal을 택했다.

## Consequences
- Positive: 확장 코드가 호스트와 같은 타입 검사와 DI 그래프 안에서 컴파일되고, 확장이 없는 빌드에 영향이 없다.
- Negative: 확장을 추가하거나 제거하려면 앱을 다시 빌드해야 하고, `:core:extension-api`를 바꾸면 모든 확장이 함께 수정되어야 한다.
