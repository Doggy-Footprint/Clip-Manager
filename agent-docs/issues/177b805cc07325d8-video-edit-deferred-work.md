# 동영상 편집에서 미뤄진 작업

## Context
동영상 편집 Phase 2~4 요구사항 문서가 범위 밖으로 남긴 항목 중 이후 spec(`agent-docs/spec-logs/547fb213daf79144-editor-effects-ui.md`, `agent-docs/spec-logs/ad27e5f2d8c95388-editor-toolbox-fullscreen-style-restore.md`)에서도 다뤄지지 않은 것들이다.

## Remaining
- **자막 파일(SRT/VTT) 가져오기**: 구간 자막은 `TextOverlay`로 표현하며 파일 가져오기 경로는 없다.
- **1080p 성능 벤치마크와 기본 예상시간 계수 갱신**: Phase 2 효과(비율·반전·배속) 추가 후 측정되지 않았다.
- **앱 종료·재설치 후 편집 상태 복원**: `SavedStateHandle` 기반 프로세스 복원만 있고 Room 저장은 범위 밖으로 남았다.

## Next Step
다루기로 결정하면 항목별로 workflow-approach spec을 새로 작성한다.

## Related
- `core/editor/src/main/java/com/doggy/clip_manager/core/editor/OverlaySpec.kt`
- `feature/editor/src/main/java/com/doggy/clip_manager/feature/editor/EditorPersistence.kt`
