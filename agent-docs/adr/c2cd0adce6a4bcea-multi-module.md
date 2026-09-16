# NiA식 멀티모듈 구조

Status: Accepted

## Decision
- Now in Android의 구조를 따라 `app`, `core/*`, `feature/*` 모듈로 나눈다.
- 공통 빌드 설정은 `build-logic/convention`의 convention plugin으로 둔다.
- NiA 코드는 포크하지 않고 구조와 패턴만 가져온다.

## Alternatives
- NiA 리포 포크: 뉴스 도메인·네트워크·동기화·flavor·Firebase 제거 작업과 AGP/NDK 설정 충돌 해결 비용이 새로 구성하는 비용보다 커서 기각했다.
- 단일 `app` 모듈 유지: feature 간 의존 경계가 강제되지 않아 기각했다.

## Consequences
- Positive: feature 모듈 간 직접 의존이 불가능하고, 모듈 단위 증분 빌드가 가능하다.
- Negative: 새 모듈마다 build 파일과 settings 등록이 필요하다.
