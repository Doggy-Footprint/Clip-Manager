# 동영상 편집 기능: 3단계 이어서 진행

## Goal
구간 자막·텍스트 오버레이·이미지 오버레이와 효과 미리보기를 제공한다.

## State
- Branch: `feat/edit`
- Base commit: `f85c1bb` (2단계 완료)
- 현재 3단계 구현은 시작되지 않았다.
- 선행 엔진은 `core/editor`에 있으며, 2단계 효과 요구사항과 검증은 `agent-docs/requirements/e26e8e199edeebb3-video-edit-phase2-effects.md`에 있다.

## Failed Attempts
| attempt | failure evidence | cause |
|---|---|---|
| none | none | none |

## Next Step
사용자와 오버레이의 시간축·위치·스타일·자산 저장 방식 및 미리보기 범위를 확정한 뒤 contract-workflow로 구현한다.

## Open Questions
- 자막·텍스트·이미지 오버레이의 시간축, 위치, 스타일, 자산 입력 및 저장 형식
- `CompositionPlayer` 미리보기의 지원 효과 범위와 내보내기 결과와의 일치 기준

## Contract Snapshot
none
