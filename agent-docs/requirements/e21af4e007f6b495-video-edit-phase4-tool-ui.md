# Video Edit Phase 4 Tool UI

## Goal
Phase 1~3의 편집 엔진을 앱 화면에서 조작할 수 있는 최소 도구 UI를 제공한다.

## Scope
- viewer 내 편집 토글로 편집 모드 진입·이탈(뒤로가기로 이탈).
- 편집 모드에서 explorer layer를 이미지 소스 목록으로 교체.
- 타임라인 범위 슬라이더로 단일 유지 구간(`keepRanges`) 선택과 `CutMode` 선택.
- 텍스트 오버레이 추가와 내용·크기·위치 편집, 이미지 오버레이 추가와 `ImageOverlayEditor` 조작.
- 편집 모드 = 항상 `OverlayPreviewPlayer` 미리보기.
- `EditService`를 통한 내보내기, 진행률·지연 경고·결과 표시.
- 편집 상태는 세션 한정(`OverlayEditSession`), 영속화 없음.

## Out of Scope
- overlay를 explorer로 드롭해 아이콘으로 접는 동작.
- 전체화면 편집 모드와 explorer floating window.
- Phase 2 효과(비율·반전·배속)의 UI 노출.
- 편집 상태의 영속화.

## Decisions
| id | decision |
|---|---|
| G1 | 오버레이 구간은 UI에서 **원본 시간축**으로 작성한다. 미리보기가 컷을 적용하지 않은 원본을 재생하므로 배치한 위치가 그대로 보여야 하기 때문이다. 내보내기 시 출력 시간축으로 변환한다 |
| G2 | 컷이 하나의 오버레이를 여러 출력 구간으로 쪼개면 첫 구간만 원래 id를 유지하고 이후 구간에는 접미사를 붙인다. `OverlaySpec.id`는 유일해야 한다 |
| G3 | 미리보기 갱신은 디바운스한다. 갱신마다 `CompositionPlayer`를 재생성하므로 드래그·타이핑 이벤트마다 갱신할 수 없다 |
| G4 | 새 텍스트 오버레이는 비어 있지 않은 기본 문자열로 시작한다. `EditPlanner.validateOverlays`가 빈 텍스트를 거부한다 |

## Acceptance Criteria
| id | given | when | then |
|---|---|---|---|
| E1 | 길이를 가진 입력 | 구간을 지정하면 | 0..길이로 잘리고 최소 길이가 보장되며 역전 구간도 유효한 구간이 된다 |
| E2 | 원본 시간축 오버레이와 유지 구간 | 내보내면 | 앞선 컷만큼 당겨지고 유지 구간으로 잘리며 교차가 없으면 제거된다 |
| E3 | 여러 유지 구간에 걸친 오버레이 | 내보내면 | 구간마다 별도 오버레이가 되고 id가 서로 다르다 |
| E4 | 선택 구간과 오버레이 목록 | 내보내기를 누르면 | 선택 구간을 유일한 `keepRanges`로 가지는 `EditSpec`이 `EditService`로 전달된다 |
| E5 | 편집 모드 진입 | viewer를 보면 | 원본 재생이 멈추고 `OverlayPreviewPlayer`가 오버레이를 합성해 재생한다 |
| E6 | 내보내기 진행 중 | `EditJobs.current`가 갱신되면 | 진행률과 `SlowState` 경고, 종료 상태가 표시된다 |

## Oracle
- E1~E3, E4(`editorExportSpec`): `feature/editor` JVM 테스트 `EditorLogicTest`.
- E4(`EditorViewModel.export` 배선): `feature/editor` Robolectric 테스트 `EditorViewModelTest`.
- E5, E6: 에뮬레이터 수동 확인.
