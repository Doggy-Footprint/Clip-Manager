# CBZ reader UI — verifier budget exhausted

## Goal
"cbz로 실행한 뷰어에 다음 기능을 넣어줘. 아래 guester bar 영역을 빼고 24px 정도의 영역을 터치하면 미리보기 화면 띄워주기. 미리보기는 좌우 스크롤이 가능하며 터치하면 바로 이동. 뷰어 왼쪽 위에 회색 배경의 round square로 지금 페이지/남은 페이지 표시. 화면 중앙 영역 터치 (가로 세로 중 짧은 길이의 1/3 길이의 정사각형 영역)시 UI 표시: 지금은 페이지와 제목 정도만. 페이지/남은 페이지는 위의 UI와 같은 위치로 자연스럽게 이어지게. 그리고 UI 표시 상태에서는 미리보기 진입 영역도 음영으로 표시"

## State
Branch master, base 534f51b8348acbffe6534be134dcb801fa06eb7d, uncommitted. Changed: feature/mainlayer/.../ViewerScreen.kt (M), CbzReader.kt (new), CbzReaderTest.kt (new), CbzTapZoneTest.kt (new). Suite 118/0.

## Failed Attempts
| attempt | failure evidence | cause |
| --- | --- | --- |
| swipe via detectHorizontalDragGestures accumulated delta | 49dp swipe did not turn page | touch slop subtracted from accumulated delta (verified) |
| separate detectTapGestures + drag handler | 47dp swipe changed page | tap detector fired on pointer-up after unconsumed drag (verified; fixed by single awaitEachGesture handler) |
| thumb cell wrapContentWidth | 300/300 thumbs composed | empty cells ~8px wide (verified) |

## Next Step
Close F4-R: strengthen `o7_viewerScreen_textSource_hasClickableBackNode` to identify the back control (e.g. give the back IconButton a contentDescription or testTag in ViewerScreen.kt, assert it, click it, and assert onClose was invoked). Then run a fresh workflow audit in a new run.

## Open Questions
- Should the back IconButton get a contentDescription (accessibility) — implies a ViewerScreen.kt change.

## Spec
agent-docs/spec-logs/513e8e0ad28ee777-cbz-reader-ui.md, v3, status limit, run 513e8e0ad28ee777.

## Execution Ledger
- Findings: verifier r1 F1–F4 (evidence gaps) → O7 added, closed in r2; F5 → RV2 review pass. Verifier r2 F4-R open (blocking): text-viewer back check only asserts any clickable node, never clicks/identifies it. Advisories A-1..A-4 (keyword-based no-controls check, inset only unit-tested, maxSidePx lower bound, scroll test density 1).
- Hand mutations: M1–M14 all killed (exit code; assertion mapping per verifier r2).
- Fuzz: none (declared). Mutation tool: none (declared).
- Correction batches 2, verifier invocations 2/2.
