# 동영상 편집 기능: 4단계 이어서 진행

## Goal
편집 기능을 조작하는 도구 UI를 제공한다.

## State
- Branch: `feat/edit`
- Base commit: `f85c1bb` (2단계 완료)
- 현재 4단계 구현은 시작되지 않았다.
- 3단계는 엔진·세션·UI 컴포넌트까지 완료되었고 앱 화면 연결만 남았다. 요구사항은 `agent-docs/requirements/f9a6261093f74075-video-edit-phase3-overlays.md`에 있다.
- 붙일 대상: `OverlayEditSession`(오버레이 목록 보유), `ImageGridScreen(images, onImageSelected)`(상태 없음, 호출자가 `ImageRepository`로 목록을 읽는다), `ImageOverlayEditor(overlay, onTransformChange, onDelete)`, `OverlayPreviewPlayer.show(inputPath, durationUs, overlays, surface, size)` / `release()`.
- 이전 합의:
  - 편집 모드에서 explorer layer는 도구와 소스를 제공한다.
  - viewer 위의 드래그 가능한 overlay를 explorer에 드롭하면 아이콘으로 접힌다.
  - viewer 전체화면에서는 왼쪽에 투명 아이콘 묶음을 두고, 소스는 explorer를 floating window로 띄운다.
  - 최소 UI(구간 선택, export 버튼, 진행률과 지연 경고 표시)와 Hilt 연결을 포함한다.

## Failed Attempts
| attempt | failure evidence | cause |
|---|---|---|
| none | none | none |

## Next Step
4단계 UI의 화면 흐름·overlay 동작·floating window 제약과 위 Open Questions를 사용자와 확정한 뒤 contract-workflow로 구현한다.

## Open Questions
- 도구 UI의 화면 흐름과 편집 상태를 저장하거나 폐기하는 시점
- overlay 드래그·접힘·복원 동작의 세부 규칙
- explorer floating window의 플랫폼 제약 및 권한 처리
- 어떤 UI가 `ImageAsset` 선택을 받아 `ImageOverlay`의 id와 출력 시간축 구간을 만드는가
- 어떤 조작이 `OverlayPreviewPlayer.show`를 호출하고 어떤 조작이 `release` 후 일반 뷰어로 복귀하는가
- `PlayerScreen`은 FFmpeg `NativePlayer` + 자체 `SurfaceView`를 쓴다. 미리보기는 `CompositionPlayer.setVideoSurface`로 같은 surface를 재사용하도록 설계했으나, 두 플레이어의 surface 소유권 전환 규칙이 정해지지 않았다.
- 오버레이 렌더링이 실제 GPU에서 의도한 위치·크기로 그려지는지는 3단계에서 검증되지 않았다. 미리보기를 화면에 붙일 때 처음 확인된다.

## Contract Snapshot
none
