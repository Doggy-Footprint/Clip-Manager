# Room 로컬 데이터베이스

Status: Accepted

## Decision
- 로컬 관계형 데이터는 Room으로 저장한다. DB, Entity, DAO는 `core/database`에 둔다.
- 스키마는 `core/database/schemas`로 export한다.

## Alternatives
- SQLiteOpenHelper 직접 사용: 쿼리 컴파일 타임 검증과 Flow 관찰을 직접 구현해야 해서 기각했다.
- DataStore: 파일-태그 같은 다대다 관계와 쿼리를 표현할 수 없어 기각했다.

## Consequences
- Positive: DAO 쿼리가 컴파일 타임에 검증되고 Flow로 관찰할 수 있다.
- Negative: 스키마 변경마다 migration을 작성해야 한다.
