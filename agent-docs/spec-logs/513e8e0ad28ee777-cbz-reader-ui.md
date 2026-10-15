---
version: 3
run_id: 513e8e0ad28ee777
status: limit
base_commit: 534f51b8348acbffe6534be134dcb801fa06eb7d
max_verifier_invocations: 2
handoff: agent-docs/handoff/efb763c37e982671-cbz-reader-ui.md
---

# User Intent
| id | stakeholder | intention | observable goal |
| --- | --- | --- | --- |
| I1 | reader | CBZ를 몰입형으로 읽기 | 평소 배지만 보이고 탭/스와이프로 넘김 |
| I2 | reader | 원하는 페이지로 빠르게 이동 | 하단 띠 탭 → 썸네일 미리보기 → 탭 시 이동 |
| I3 | reader | 필요할 때만 UI 확인 | 중앙 탭으로 제목/페이지 UI 토글, 진입 띠 음영 |

# Scope
In scope: `ViewerContent.Cbz` 화면의 표시/제스처/미리보기.
Out of scope: PDF·영상·이미지 등 다른 뷰어, 줌, RTL 설정, 애니메이션, 암호 다이얼로그 흐름 변경.

# Paths
Implementation: feature/mainlayer/src/main/java/com/doggy/clip_manager/feature/mainlayer/ViewerScreen.kt, feature/mainlayer/src/main/java/com/doggy/clip_manager/feature/mainlayer/CbzReader.kt
Tests: feature/mainlayer/src/test/java/com/doggy/clip_manager/feature/mainlayer/CbzReaderTest.kt, feature/mainlayer/src/test/java/com/doggy/clip_manager/feature/mainlayer/CbzTapZoneTest.kt, feature/mainlayer/src/test/java/com/doggy/clip_manager/feature/mainlayer/ViewerScreenPasswordTest.kt
Test command: ./gradlew :feature:mainlayer:testDebugUnitTest
Review evidence: RV2 — R1 배지 시각 속성: main이 CbzReader.kt에서 배지 Modifier가 회색 배경과 RoundedCornerShape를 쓰는지 확인, ledger에 기록. RV1 — 썸네일 디코딩 코드 리뷰: `BitmapFactory.Options.inSampleSize` 사용 및 LazyRow item 내부 로드 여부를 main이 확인, 결과를 execution ledger에 기록.
Fuzzing: none — 입력 표면이 좌표 하나로 경계값 분석이 완전 열거함.
Mutation tool: none — 프로젝트에 PIT/Kotlin mutation 도구 미설치; 손 mutation 8–10개로 대체.

# Signatures
internal enum class CbzTapZone { Center, Left, Right, PreviewStrip }
internal fun cbzTapZone(x: Float, y: Float, width: Float, height: Float, stripHeightPx: Float, bottomInsetPx: Float): CbzTapZone
@Composable internal fun CbzReader(title: String, pageCount: Int, loadPage: suspend (index: Int, maxSidePx: Int?) -> Bitmap?, modifier: Modifier = Modifier)
Test tags (public contract for tests): "cbz_page_badge", "cbz_top_bar", "cbz_preview_strip_shade", "cbz_preview", "cbz_thumb_<index>" (0-based), "cbz_reader" (root gesture surface).

# Functional Requirements
| id | requirement | priority | source |
| --- | --- | --- | --- |
| R1 | 배지(tag cbz_page_badge)는 항상 표시되며 텍스트 `현재/전체`(1-based), 회색 배경 둥근 사각형, 왼쪽 위 | must | user |
| R2 | 중앙 정사각형(변 = min(w,h)/3, 화면 중심 기준, 경계 포함) 탭 시 UI(tag cbz_top_bar, 제목 텍스트 포함) 토글. 배지 bounds는 UI on/off에서 동일 | must | user |
| R3 | 하단 띠: y ∈ [height − bottomInsetPx − stripHeightPx, height − bottomInsetPx), stripHeightPx = 24dp. 띠 판정이 중앙/좌/우보다 우선. 탭 시 미리보기(tag cbz_preview) 열림. y ≥ height − bottomInsetPx(제스처 바)는 띠가 아님 | must | user |
| R4 | 미리보기: 가로 스크롤 LazyRow, 썸네일(tag cbz_thumb_i, 높이 96dp). 썸네일 칸은 비트맵 유무와 무관하게 최소 폭 48dp 고정(로드 전/실패 칸도 동일). 썸네일 탭 → 페이지 i로 이동 + 미리보기 닫힘. 열릴 때 현재 페이지로 스크롤. 미리보기 밖 탭 → 닫힘(페이지 변화 없음) | must | user |
| R5 | UI on 상태에서만 띠 음영(tag cbz_preview_strip_shade) 표시 | must | user |
| R6 | 띠·중앙 외 영역: x < width/2 → Left(이전), 그 외 → Right(다음). 첫/끝 페이지에서는 무시 | must | user |
| R7 | 수평 이동거리(포인터 down 위치 → up 위치의 x 차이, touch slop 포함) ≥ 48dp: 왼쪽 이동 → 다음, 오른쪽 이동 → 이전. 경계에서 무시. 48dp 미만은 페이지 변화 없음 | must | user |
| R8 | CBZ 화면에는 이전/다음 버튼과 뒤로가기 IconButton이 없음. 다른 뷰어는 뒤로가기 유지 | must | user |
| R9 | 미리보기 열린 상태에서 중앙/좌/우 탭은 미리보기 닫기만 수행 | must | default A6 |
| R10 | 제목 = 아카이브 파일명에서 마지막 확장자 제거 | must | default A3 |

# Errors
- 페이지 디코딩 실패(loadPage null/예외) — 이미지 없음, 크래시 없음 — 배지·넘김·UI 정상.
- 썸네일 디코딩 실패 — 해당 칸 비어 있음 — 다른 썸네일·탭 이동 정상.
- 엔트리 0개 — "이미지가 없습니다" 텍스트 — CbzReader 미표시.

# Cases
| id | level | input / state | expected result |
| --- | --- | --- | --- |
| C1 | normal | 3페이지, 2쪽, 중앙 탭 | top bar 표시, 제목 보임, 배지 `2/3` 동일 bounds |
| C2 | normal | UI on, 중앙 탭 | top bar 사라짐 |
| C3 | normal | 1쪽, 오른쪽 탭 / 왼쪽 스와이프 | `2/N` |
| C4 | normal | 미리보기 열림, thumb_4 탭 | `5/N`, 미리보기 닫힘 |
| C5 | boundary | 1쪽 왼쪽 탭·오른쪽 스와이프 | `1/N` 유지 |
| C6 | boundary | N쪽 오른쪽 탭·왼쪽 스와이프 | `N/N` 유지 |
| C7 | boundary | 정사각형 각 변 ±1px, 띠 상단 ±1px, 제스처 inset 경계, x=width/2±1px | zone 판정 R2/R3/R6대로 |
| C8 | boundary | 1페이지 아카이브 | `1/1`, 넘김 무시 |
| C9 | boundary | 스와이프 47dp vs 49dp | 변화 없음 vs 넘김 |
| C10 | error | loadPage가 null/throw | 크래시 없음, 배지·넘김 정상 |
| C11 | edge | 가로 화면(w>h) | 정사각형 변 = h/3 |
| C12 | edge | 엔트리 0개 | "이미지가 없습니다" |
| C13 | normal | UI off → 음영 없음, UI on → 음영 있음 | R5 |
| C14 | normal | 미리보기 열림, 중앙 탭 | 미리보기 닫힘, UI 상태·페이지 불변 |

# Quality Applicability
| ISO/IEC 25010:2023 characteristic | applicable | rationale |
| --- | --- | --- |
| Functional suitability | yes | 사용자 요구 동작 |
| Performance efficiency | yes | 썸네일 다수 로드 시 메모리 |
| Compatibility | no | 외부 인터페이스 변경 없음 |
| Interaction capability | yes | 터치 영역 크기 |
| Reliability | yes | 깨진 이미지 |
| Security | no | 암호 흐름 불변, 기존 테스트로 보존 |
| Maintainability | no | 일반 코드 리뷰 수준 |
| Flexibility | no | 단일 화면 |
| Safety | no | 해당 없음 |

# Quality Requirements
| id | characteristic / subcharacteristic | target and context | measure method / inputs / unit | threshold and direction | evidence: automated, review, mutation | source |
| --- | --- | --- | --- | --- | --- | --- |
| Q1 | Functional suitability / completeness | R1–R10 | 통과 obligation / 전체, % | = 100% | automated | user |
| Q2 | Performance / resource utilization | 썸네일 디코딩 | 썸네일 loadPage 호출의 maxSidePx 인자, px | 썸네일 호출은 non-null ≤ 96dp px 상당; 본 페이지는 null | automated (O4) + RV1 | default A5 |
| Q3 | Interaction / operability | 띠 높이 | stripHeightPx, dp | = 24dp | automated (O1 input) | user |
| Q4 | Reliability / fault tolerance | C10 | 크래시 수 | = 0 | automated (O5) | default |

# Verification Obligations
| id | parent requirement/Case ids | variant and target surface | test layer and selection policy | ISO/IEC/IEEE 29119-4 technique | coverage items | coverage target | observation and expected result | evidence procedure |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| O1 | R2,R3,R6,C7,C11 | cbzTapZone 순수 함수 | unit, 전체 열거 | boundary value analysis (3-value) | 세로(1080×1920)·가로(1920×1080) 각각: 정사각형 left/right/top/bottom, 띠 상단, inset 경계, x=w/2 — 각 경계의 on/−1/+1 | 100% | 반환 enum = R2/R3/R6 | CbzTapZoneTest |
| O2 | R2,R3,R4,R9,C1,C2,C4,C14 | CbzReader Compose | integration (Robolectric) | state transition (0-switch) | 전이: Off–center→On, On–center→Off, Off–strip→Preview, On–strip→Preview, Preview–thumb→prior UI state+page, Preview–outside/center→prior UI state | 100% | tag 존재/부재, 배지 텍스트 | CbzReaderTest |
| O3 | R6,R7,C3,C5,C6,C8,C9 | CbzReader 넘김 | integration | boundary value analysis (2-value) | 페이지 {1,N} × {탭좌,탭우,스와이프좌,스와이프우}; pageCount=1; 스와이프 47dp/49dp | 100% | 배지 텍스트 | CbzReaderTest |
| O4 | R1,R5,R8,R10,Q2,C13 | CbzReader/ViewerScreen 표시 | integration | equivalence partitioning | 배지 bounds on==off; 음영 UI off 부재/on 존재; 버튼 부재(SkipPrevious/SkipNext/뒤로가기 click 노드 없음); 제목 "my.book.cbz"→"my.book"; 썸네일 loadPage maxSidePx non-null, 본 페이지 null | 100% | 노드/콜백 인자 | CbzReaderTest |
| O5 | Errors,C10,C12 | CbzReader | integration | error guessing | null 반환, 예외 throw, 0 엔트리(ViewerScreen 경로 또는 기존 동작) | none — experience-based | 크래시 없음, 배지 정상 | CbzReaderTest |
| O7 | R3,R4,R7,R8,Q2,Q3 | CbzReader/ViewerScreen at density 2 (Robolectric qualifiers xhdpi) | integration | boundary value analysis (2-value) | 띠 상단 경계: h−24dp−2px 탭은 미리보기 안 열림, h−24dp+2px 탭은 열림; shade 높이 = 24dp; thumb 높이 = 96dp; thumb 최소폭 ≥ 48dp; 스와이프 47dp/49dp; 썸네일 maxSidePx ≤ 96dp px; 다른 뷰어(Text) ViewerScreen에 click 가능한 뒤로가기 노드 존재 | 100% | 노드 bounds/배지/콜백 | CbzReaderTest |
| O6 | 기존 암호 흐름 | ViewerScreen | integration | equivalence partitioning | 기존 테스트 3개 | 100% | `1/1` 표시 | ViewerScreenPasswordTest (변경 없음) |

# Assumptions and Defaults
| id | decision | evidence and uncertainty | brief v1 — approved / vetoed → <answer> / user answer |
| --- | --- | --- | --- |
| A0 | 배지 `현재/전체`, 뒤로가기 버튼 제거, 탭+스와이프 넘김, 버튼 삭제 | 사용자 답변 | user answer |
| A1 | 뒤로가기 제거는 CBZ만 | 다른 뷰어 영향 최소화 | brief v1 — approved |
| A2 | LTR 읽기 방향 | 일반 관례 | brief v1 — approved |
| A3 | 제목 = 파일명 − 확장자 | | brief v1 — approved |
| A4 | 애니메이션 없음 | | brief v1 — approved |
| A5 | 썸네일 높이 96dp, 하단 오버레이 | | brief v1 — approved |
| A6 | 미리보기 열림 중 다른 탭은 닫기만 | 일반 모달 관례 | contract detail of A5 overlay |

# Traceability
| requirement id | Case ids | obligation ids | evidence procedure |
| --- | --- | --- | --- |
| R1 | C1 | O4 | CbzReaderTest |
| R2 | C1,C2,C7,C11 | O1,O2,O4 | both |
| R3 | C7 | O1,O2 | both |
| R4 | C4 | O2 | CbzReaderTest |
| R5 | C13 | O4 | CbzReaderTest |
| R6 | C3,C5,C6,C7 | O1,O3 | both |
| R7 | C3,C9 | O3 | CbzReaderTest |
| R8 | — | O4 | CbzReaderTest |
| R9 | C14 | O2 | CbzReaderTest |
| R10 | — | O4 | CbzReaderTest |

# Workflow Control
| item | value |
| --- | --- |
| correction batches used | 2 |
| verifier invocations | 2 |
| open finding ids | F4-R |

Audit state:
| obligation id | spec version | evidence references and revision | accepted / open / invalidated / pending | rationale and mutation outcome | dependencies and reopening evidence |
| --- | --- | --- | --- | --- | --- |
| O1,O5,O6 | 2 | verifier round 1 | accepted | — | — |
| O2,O3,O4,O7 | 3 | tests rev working tree; RV1 pass (ViewerScreen.kt inSampleSize, LazyRow item LaunchedEffect) | pending | hand mutations M1–M10 all killed (strip precedence, square size, square inclusivity, w/2 boundary, last-page guard, swipe direction, thumb tap close, preview modal tap, thumb maxSide, title ext); M11–M14 killed (strip px, thumb height, swipe px, back removed all viewers); RV2 pass (Color.Gray, RoundedCornerShape(12.dp) at CbzReader.kt:175) | F1–F4 addressed by O7 |

Execution ledger:
| attempt | finding / failure signature | cause hypothesis | changed approach / new evidence | result / disposition |
| --- | --- | --- | --- | --- |
| 1 | 8 failures: zone squareLeft/Right(4), swipe 49dp(2), scrollsToCurrentPage, loadedLazily | (a) test used wrong square side (expected [180,900] for portrait; spec gives [360,720]) — verified by arithmetic; (b) slop subtracted from accumulated drag — spec ambiguity; (c) empty thumb cell width ~8px so all 300 compose — verified from node bounds | v2: R7 measure defined; R4 thumb min width; test defect to test-implementer, impl defects to implementer | resolved; then D3 (tap fired after swipe) fixed via single gesture handler; 107/107 pass |

# Version Log
## v1
- Initial spec from approved brief v1.
## v2
- R7: swipe distance defined as down→up displacement incl. slop (test-implementer challenge). R4: thumbnail cell min width 48dp (empty cells collapsed → no laziness, Q2). Contract-only.
## v3
- Verifier round 1 findings F1–F4: add O7 (density-2 integration checks for strip/thumb/swipe/back on other viewers). F5: R1 color/shape assigned to review evidence RV2. Contract-only.
