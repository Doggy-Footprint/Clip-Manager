# Video Edit Phase 3 Overlays

## Goal
동영상 편집 Phase 3에서 구간 자막·텍스트 오버레이·이미지 오버레이를 `core/editor` 엔진과 내보내기 경로에 제공하고, 오버레이 배치를 확인할 수 있는 미리보기와 편집 세션·UI 컴포넌트를 준비한다.

## Scope
- `OverlaySpec`(`TextOverlay`, `ImageOverlay`)와 스타일·변환 검증.
- `EditEffects.overlays`를 통한 내보내기 경로 통합과 출력 시간축 기준 구간 매핑.
- `OverlayEditSession`(세션 한정 오버레이 목록), `OverlayPreviewPlayer`, `ImageOverlayEditor`, `ImageGridScreen`, `ImageRepository`.

## Out of Scope
- 편집 도구 UI와 앱 화면 연결 (Phase 4).
- Phase 2 효과(비율·반전·배속·컷)의 미리보기 반영.
- 오버레이 렌더링 결과의 계측 테스트(실제 픽셀 검증).
- 자막 파일(SRT/VTT) 가져오기.

## Acceptance Criteria
| id | given | when | then |
|---|---|---|---|
| B1 | 구간과 텍스트를 가진 `TextOverlay` | 기본 스타일로 만들면 | 하단 중앙·20pt·불투명 흰색·배경 없음·중앙 정렬이며 이것이 자막 프리셋이다 |
| B2 | 이미지와 정규화 변환을 가진 `ImageOverlay` | 내보내면 | 위치 0..1, 양수 scale, alpha 0..1, 유한 회전각이 프레임에 적용된다 |
| B3 | 빈 id·중복 id·역전 구간·빈 텍스트·범위 밖 또는 비유한 수치 | 검증하면 | `InvalidEffectException`을 동기 발생시킨다 |
| B4 | 출력 길이를 벗어나거나 걸치는 오버레이 구간 | 내보내면 | 출력 길이로 잘리고 빈 교차는 제거되며 타입과 나머지 필드는 보존된다 |
| B5 | 오버레이만 존재하고 FAST가 요청됨 | 편집하면 | PRECISE 경로로 강제된다 |
| B6 | 같은 명세를 두 concat 전략으로 실행 | 편집하면 | 오버레이가 출력 시간축의 같은 구간에 나타난다 |
| B7 | 읽을 수 없거나 디코딩 불가한 오버레이 이미지 | 내보내면 | `InputNotReadableException`을 발생시킨다 |
| B8 | 선택된 이미지 오버레이 | 편집 컴포넌트를 조작하면 | 이동·크기·회전·투명도 변경과 삭제가 가능하다 |
| B9 | 오버레이 목록과 입력 영상 | 미리보기를 시작하면 | 내보내기와 같은 구간 클리핑으로 오버레이만 합성해 재생한다 |

## Decisions
| id | question | user's answer |
|---|---|---|
| E1 | Phase 3 완료 범위 | 엔진·세션·재사용 가능한 UI 컴포넌트까지, 앱 화면 연결은 Phase 4 |
| E2 | 구간 자막 표현 | 별도 타입 없이 `TextOverlay`로 충분 |
| E3 | 미리보기 일치 범위 | Phase 3 오버레이만, Phase 2 효과는 제외 |
| E4 | 검증 방식 | JVM 단위 테스트와 전 모듈 컴파일 |
| E5 | 오버레이 시간축 | 원본이 아닌 출력 시간축 기준 |
| E6 | `ImageSource` 표현 | `core:model`이 JVM 전용 모듈이므로 `android.net.Uri`가 아닌 문자열 |

## Open Questions
none

## Oracle
| id | level | input / state | expected result | derived from |
|---|---|---|---|---|
| P1 | normal | 기본 `TextOverlayStyle`과 기본 `OverlayTransform` | 자막 프리셋 값과 중앙·불투명·무회전 기본값 | B1/B2, invariant |
| P2 | error | 빈/중복 id, 역전 구간, 빈 텍스트, 범위 밖·NaN·Infinity 수치 | `InvalidEffectException` | B3, failure mode |
| P3 | boundary | position 0f와 1f, alpha 0f와 1f | 거부되지 않고 통과 | B3, boundary |
| P4 | normal | 출력 길이에 걸치거나 완전히 포함된 구간 | 잘린 구간과 보존된 타입·필드 | B4, partition |
| P5 | boundary | 구간이 출력 길이에 정확히 접함 | end가 출력 길이와 같으면 유지, start가 출력 길이 이상이면 제거 | B4, boundary |
| P6 | edge | 오버레이만 있는 `EditEffects` | `isPresent`가 true이고 FAST 요청이 PRECISE로 강제됨 | B5, invariant |
| P7 | normal | 세션에 add/update/remove/clear | 목록 반영, 중복·미지 id·잘못된 값은 예외이며 목록 불변 | B8, decision table |
