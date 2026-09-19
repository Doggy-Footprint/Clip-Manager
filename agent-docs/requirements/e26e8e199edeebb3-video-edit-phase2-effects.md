# Video Edit Phase 2 Effects

## Goal
동영상 편집 Phase 2에서 화면 비율(crop/stretch/fit), 구간별 상하·좌우 반전, 0.5x~2.0x 구간 배속과 오디오 피치 보존을 `core/editor` 엔진과 정확성 테스트로 제공한다.

## Scope
- `EditSpec`에서 전체 프레임 레이아웃과 원본 시간축 기준 구간 효과를 표현한다.
- Media3 Transformer로 효과를 내보내며 두 concat 전략을 지원한다.
- 출력 크기·픽셀 배치·길이·오디오 주파수를 자동 검증한다.

## Out of Scope
- 편집 UI와 효과 미리보기
- 1080p 성능 벤치마크와 기본 예상시간 계수 갱신
- 자막, 텍스트, 이미지 오버레이

## Acceptance Criteria
| id | given | when | then |
|---|---|---|---|
| A1 | 원본 또는 프리셋/자유 비율과 crop/stretch/fit 모드 | 편집을 시작하면 | 원본 면적에 가장 가까운 정확한 목표 비율의 짝수 해상도로 출력되며 fit 여백은 검정색이다 |
| A2 | 0~1 정규화 crop 중심점과 반전 효과 | 함께 편집하면 | 반전 전 원본 좌표에서 선택한 영역을 crop한 뒤 요청 축을 반전한다 |
| A3 | 원본 시간축의 반전 구간들이 겹침 | 편집하면 | 각 축을 XOR로 합성하고 keepRange와 교차하는 부분에만 적용한다 |
| A4 | 원본 시간축의 비중첩 배속 구간과 허용 속도 | 편집하면 | 각 보존 구간이 지정 속도로 출력되고 총 길이는 구간 길이/속도의 합이다 |
| A5 | 1kHz 오디오가 있는 입력 | 0.5x~2.0x 배속하면 | 출력 오디오의 FFT 주 피크가 980~1020Hz에 있다 |
| A6 | 오디오가 없는 입력 | 배속하면 | 영상만 요청 속도로 정상 출력된다 |
| A7 | 어떤 효과든 존재하고 FAST가 요청됨 | 편집하면 | PRECISE 경로로 강제되며 두 concat 전략에서 같은 효과 결과를 낸다 |
| A8 | 잘못된 비율·crop 좌표·속도·효과 범위 또는 겹친 배속 | 작업을 시작하면 | `InvalidEffectException`을 동기 발생시키고 출력·임시 파일을 만들지 않는다 |
| A9 | 효과가 입력 길이 또는 keepRange 밖에 걸침 | 편집하면 | 입력 길이로 자르고 keepRange와 교차하는 부분만 적용한다 |

## Decisions
| id | question | user's answer |
|---|---|---|
| D1 | Phase 2 범위 | 엔진/API와 정확성 테스트만 포함 |
| D2 | 출력 해상도 | 원본 픽셀 면적에 가장 가까운 목표 비율의 짝수 크기 |
| D3 | crop 위치 | 0~1 정규화 중심점, 기본 중앙 |
| D4 | 배속 중첩 | 입력 오류로 거부 |
| D5 | 반전 중첩 | 축별 XOR |
| D6 | 효과 시간축과 범위 | 원본 시간축, 입력과 keepRange의 유효 교차만 적용 |
| D7 | 잘못된 효과 | 시작 전 동기 오류 |
| D8 | 피치 허용치 | 1kHz 기준 ±2% |
| D9 | concat 지원 | 두 전략 모두 보장 |
| D10 | 무음 입력 | 영상만 정상 배속 |
| D11 | crop과 반전 순서 | 반전 전 원본을 crop한 뒤 반전 |

## Open Questions
none

## Oracle
| id | level | input / state | expected result | derived from |
|---|---|---|---|---|
| O1 | normal | 16:9 crop, stretch, fit 각각 | 정확한 짝수 해상도와 모드별 픽셀 배치; fit은 검정 여백 | A1, partition |
| O2 | normal | 비대칭 영상의 가로·세로·동시 반전 구간 | 해당 구간 픽셀이 요청 축으로 이동 | A2/A3, decision table |
| O3 | normal | 0.5/1.0/2.0x 구간과 1kHz 오디오 | 길이는 각 구간/속도의 합이고 주 피크는 980~1020Hz | A4/A5, partition |
| O4 | boundary | crop 중심 0과 1, 속도 0.5와 2.0, 효과가 keep 경계와 접함 | 유효 경계까지 정확히 적용 | A2/A4/A9, boundary |
| O5 | error | 0 이하 비율, 좌표 범위 밖, 허용 집합 밖 속도, 겹친 배속 | 동기 `InvalidEffectException`; 새 파일 없음 | A8, failure mode |
| O6 | edge | 같은 축 반전 두 개가 겹침 | 겹친 부분은 원래 방향 | A3, decision table |
| O7 | edge | 효과가 삭제된 구간에만 존재 | 출력에는 효과가 없지만 PRECISE 강제 | A7/A9, invariant |
| O8 | edge | 무음 영상 배속 | 오디오 트랙 없이 변경된 길이의 영상 출력 | A6, absent optional state |
| O9 | edge | 같은 명세를 두 concat 전략으로 실행 | 해상도·길이·대표 픽셀·피치가 동일 허용범위 | A7, relation between two runs |
