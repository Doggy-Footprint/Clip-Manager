# MediaStoreMediaSource 커서 파싱이 검증되지 않음

## Context
`MediaStoreMediaSource`는 `ContentResolver` 쿼리 결과 `Cursor`를 `MediaEntry`로 변환하는 유일한 지점이다.
미디어 그리드 계약(`media-grid` v5)은 집계/필터/상태 로직만 다뤘고, 이 클래스는 `MediaSource` 인터페이스
뒤에서 fake로 대체되어 한 번도 실행되지 않았다. 프레임워크 경계라 테스트하려면 Robolectric
`ShadowContentResolver`에 `MatrixCursor`를 주입하는 별도 스위트가 필요하고, 이는 해당 계약과 성격이 달라
범위 밖으로 남겼다.

## Untested Branches
- **API 29 경계**: `BUCKET_DISPLAY_NAME`/`DURATION`은 API 29부터만 `MediaColumns`에 존재한다. 모르는 컬럼을
  projection에 넣으면 provider가 null을 주지 않고 예외를 던지므로 `SDK_INT >= Q`로 분기하고 있으나, 이
  분기 자체가 실행된 적이 없다. API 29 미만 기기에서만 드러나는 경로다.
- **`DATA` 컬럼 null**: scoped storage에서 절대 경로는 신뢰할 수 없고 null일 수 있다. 이때 `filePath = null`로
  넘어가며, `ClipApp`의 `entry.filePath?.let { selectedPath = it }`가 무반응이 되어 강조만 이동하고 재생은
  바뀌지 않는다(별도 확인 대상).
- **`DISPLAY_NAME` null**: `.orEmpty()`로 빈 문자열이 되어 이름 없는 셀이 렌더링된다.
- **IMAGE에 DURATION을 요청하지 않는 분기**.
- **`contentResolver.query()`가 null 반환**(권한 없음/provider 실패): `.orEmpty()`로 빈 목록이 된다.
  `DefaultMediaRepository`의 예외 흡수(계약 C8)와는 다른 경로이므로 C8이 이를 대신 검증하지 않는다.

## Next Step
다루기로 결정하면 `ShadowContentResolver` + `MatrixCursor` 기반 계약을 새로 작성한다. 위 5개 분기가 그대로
케이스가 된다.

## Related
- `core/data/src/main/java/com/doggy/clip_manager/core/data/media/MediaStoreMediaSource.kt`
- 이전 핸드오프: `agent-docs/handoff/stale/4f1c7a93be05d268-media-grid-contract-tests.md`
