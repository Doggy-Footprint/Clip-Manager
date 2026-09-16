# 3-layer 단일 화면 구조

Status: Accepted

## Context
- 앱은 태그 목록, 파일 탐색, 뷰어를 함께 다룬다.
- 기존 구조는 `NavigationSuiteScaffold`(파일/태그) + `NavHost`였고, 파일 탐색 화면에서 동영상을 고르면 `PlayerRoute(path)`로 이동해 전체 화면으로 재생했다.

## Decision
- `NavHost`와 route(`BrowserRoute`, `PlayerRoute`, `TagsRoute`, `TopLevelDestination`)를 제거하고, `ClipApp` 한 화면에 tab layer, explorer layer, viewer layer를 함께 배치한다.
- 선택한 파일 경로는 `ClipApp`의 상태로 두고 `PlayerPane(path)`에 넘긴다. `PlayerViewModel`은 `open(path)`로 재생할 파일을 바꾸고, 이전 `NativePlayer`는 release한다.
- 화면 크기에 따라 배치를 나눈다:
  - 세로(maxHeight > maxWidth): 아이콘 탭 · [explorer / viewer]를 위아래로 배치
  - 가로이고 높이 480dp 미만: 아이콘 탭 · 얇은 explorer · viewer
  - 그 밖의 가로: 라벨 탭 · explorer · viewer
- 뷰어 전체화면은 `ClipApp` 상태로 tab layer와 explorer layer를 숨겨 구현한다.

## Alternatives
- NavHost를 유지하고 route마다 화면을 전체 전환: 탐색과 재생을 동시에 볼 수 없어 채택하지 않았다.
- `hiltViewModel(key = path)`로 파일마다 PlayerViewModel을 따로 생성: 이전 ViewModel이 ViewModelStore가 정리될 때까지 남아 여러 NativePlayer가 동시에 살아 있으므로 채택하지 않았다.

## Consequences
- Positive: 파일을 탐색하면서 동시에 재생할 수 있고, 다른 파일을 고르면 화면 이동 없이 viewer의 파일만 바뀐다.
- Negative: 뒤로 가기 동작과 딥링크를 navigation 라이브러리 없이 직접 처리해야 한다.
