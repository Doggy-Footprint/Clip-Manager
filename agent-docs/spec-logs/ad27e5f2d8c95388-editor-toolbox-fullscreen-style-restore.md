---
version: 3
run_id: ad27e5f2d8c95388
status: limit
base_commit: 65f86cc8e0a7e04825ad9ada854c95ef3b33ae17
max_verifier_invocations: 3
handoff: agent-docs/handoff/29be29e194db5768-editor-toolbox-fullscreen-style-restore-limit.md
---

# User Intent
| id | stakeholder | intention | observable goal |
|---|---|---|---|
| I1 | 사용자 | 편집을 마친 오버레이를 도구함에 넣어 둔다 | 오버레이를 side layer로 드래그하면 하단 도구함 줄에 아이콘으로 들어가고, 미리보기와 출력에는 남으며, 아이콘을 탭하면 다시 편집할 수 있다 |
| I2 | 사용자 | 넓은 화면에서 편집한다 | 편집 중 전체화면으로 전환할 수 있고, side layer 내용(이미지 그리드와 도구함)을 토글할 수 있는 이동형 floating 패널로 쓸 수 있다 |
| I3 | 사용자 | 텍스트 오버레이를 꾸민다 | 프리셋 팔레트로 글자색과 배경을 고르고, 가운데/왼쪽 정렬을 바꾸면 미리보기와 출력에 반영된다 |
| I4 | 사용자 | 시스템이 프로세스를 종료해도 편집이 사라지지 않는다 | 프로세스 종료 후 복원 시 편집 상태가 그대로 돌아온다 |

# Scope
In scope: 오버레이 도구함(넣기·꺼내기), 편집 전체화면 모드, 앱 내 floating 패널(토글, 드래그 이동), 텍스트 글자색·배경·정렬 UI, `SavedStateHandle` 기반 편집 상태 복원.
Out of scope: `SYSTEM_ALERT_WINDOW` 시스템 오버레이 창, 앱을 직접 종료하거나 다시 설치한 뒤의 복원(Room 저장), 자유 색상 선택, 이미지 오버레이 스타일 변경, 도구함 아이콘을 드래그로 꺼내기, 편집 이외 화면(플레이어 전체화면 등)의 동작 변경, 엔진(`core/editor`) 변경.

# Paths
Implementation: feature/editor/src/main, feature/sidelayer/src/main, app/src/main
Tests: feature/editor/src/test
Test command: ./gradlew :feature:editor:testDebugUnitTest :core:editor:testDebugUnitTest assembleDebug
Review evidence: R1 — 에뮬레이터 `clip_tablet_1280x800`(API 35) 수동 절차(아래 Verification Obligations의 V-R 행). 결과는 `agent-docs/spec-logs/ad27e5f2d8c95388-review.md`에 기록한다.

# Signatures
```
EditorViewModel @Inject constructor(savedStateHandle: SavedStateHandle)
  val stowedOverlayIds: Set<String>                 (읽기 전용 상태, 기본값 빈 집합)
  fun stow(id: String)                              // 없는 id는 무시. 선택 중이면 선택 해제
  fun unstow(id: String)                            // 도구함에서 빼고 그 오버레이를 선택
  var fullscreen: Boolean                           (private set, 기본값 false)
  fun toggleFullscreen()
  // 기존 멤버(begin/end/select/update/remove/add*, 효과 멤버, exportSpec)는 시그니처 유지
internal val TEXT_PALETTE: List<Long>               // 8개 ARGB, 모두 불투명(alpha 0xff), 흰색·검정 포함, 중복 없음
internal fun backgroundOf(colorArgb: Long): Long    // 같은 RGB에 alpha 0x80
```
`remove(id)`는 도구함에서도 그 id를 뺀다. `begin`이 다른 파일로 바뀌면 도구함도 비운다. `exportSpec()`은 도구함 여부와 무관하게 모든 오버레이를 싣는다.

# Functional Requirements
| id | requirement | priority | source |
|---|---|---|---|
| F1 | `stow(id)`는 오버레이를 도구함에 넣는다. 도구함 오버레이는 `overlays`와 `exportSpec()`에 그대로 남는다 | must | I1 |
| F2 | 도구함 오버레이는 미리보기에서 편집 핸들(이미지 핸들)이 그려지지 않고 선택할 수 없다(`select(id)`가 무시됨) | must | I1 |
| F3 | `unstow(id)`는 도구함에서 빼고 `selectedOverlayId = id`로 만든다 | must | I1 |
| F4 | UI: 편집 패널의 오버레이 행을 길게 눌러 드래그해 side layer(또는 floating 패널)의 하단 도구함 줄에 놓으면 `stow`된다. 도구함 줄은 도구함 오버레이마다 종류 아이콘(텍스트/이미지)을 보이고, 탭하면 `unstow`된다 | must | I1 |
| F5 | 편집 패널에 전체화면 토글이 있다. 전체화면이면 tab layer와 side layer를 숨기고 편집 화면이 창 전체를 쓴다. 뒤로 가기는 먼저 전체화면을 해제한다 | must | I2 |
| F6 | 전체화면 편집 중에는 floating 패널 토글 버튼이 있다. 패널 내용은 편집 중 side layer와 같다(이미지 그리드 + 하단 도구함). 패널은 드래그로 옮길 수 있고, 창 밖으로 완전히 나가지 않는다 | must | I2 |
| F7 | 선택한 텍스트 오버레이 컨트롤에 글자색 팔레트(`TEXT_PALETTE`), 배경(없음 + `TEXT_PALETTE` 각 색의 `backgroundOf`), 정렬 토글(가운데/왼쪽 = `centerAligned`)이 있다. 고른 값이 `TextOverlayStyle`에 들어가 미리보기와 `exportSpec()`에 반영된다 | must | I3 |
| F8 | 편집 상태(editing, path, durationUs, selection, cutMode, ratioPreset, frameMode, flips, speeds, overlays 전체 내용, stowedOverlayIds, selectedOverlayId, fullscreen)는 `SavedStateHandle`에 저장되어, 같은 저장 값으로 만든 새 `EditorViewModel`이 동일한 상태와 동일한 `exportSpec()`을 가진다 | must | I4 |
| F9 | floating 패널의 열림 여부와 위치는 구성 변경(회전)과 프로세스 복원 후 유지된다(`rememberSaveable`) | should | I2, I4 |

# Errors
- 저장 값이 없거나 해석할 수 없는 값(키 누락, 손상된 인코딩) — 예외 없이 해당 필드는 기본값으로 시작 — 앱은 편집 전 상태로 동작한다.
- 복원된 durationUs > 0 인데 저장된 selection이 손상됨 — 예외 없이 selection은 같은 path·durationUs로 `begin`했을 때의 기본 구간 — 편집 가능 상태.
- 복원된 selectedOverlayId/stowedOverlayIds가 복원된 overlays에 없는 id를 가리킴 — 해당 id 제거(선택은 null) — 나머지 상태 유지.
- 없는 id로 `stow`/`unstow`/`select` 호출 — 무시, 예외 없음 — 상태 변화 없음.
- 텍스트 스타일 변경이 `OverlayEditSession`에서 거부됨 — 기존 `update` 동작대로 무시 — 이전 스타일 유지.

# Cases
| id | level | input / state | expected result |
|---|---|---|---|
| C1 | normal | 텍스트·이미지 오버레이 추가 후 이미지 `stow` | `stowedOverlayIds`={이미지 id}, `overlays`와 `exportSpec().overlays`에 둘 다 존재 |
| C2 | normal | 선택된 오버레이를 `stow` | `selectedOverlayId == null` |
| C3 | normal | 도구함 오버레이 `select(id)` | 선택 변화 없음 |
| C4 | normal | `unstow(id)` | 도구함에서 빠지고 `selectedOverlayId == id` |
| C5 | edge | 도구함 오버레이 `remove(id)` | `overlays`와 도구함 모두에서 사라짐 |
| C6 | edge | 도구함이 있는 상태에서 다른 path로 `begin` | 도구함 빈 집합 |
| C7 | error | 없는 id로 `stow`/`unstow` | 상태 불변, 예외 없음 |
| C8 | normal | 모든 F8 필드를 기본값이 아닌 값으로 만든 뒤 저장 값으로 새 VM 생성 | 모든 필드와 `exportSpec()`이 원래 VM과 같음 |
| C9 | boundary | 빈 `SavedStateHandle`로 생성 | 모든 필드가 기존 기본값(editing=false, path=null, 빈 목록 등) |
| C10 | error | 오버레이 저장 키에 해석할 수 없는 문자열 | 예외 없이 오버레이 빈 목록으로 시작 |
| C11 | normal | 텍스트 오버레이 스타일을 팔레트 색, 배경, 왼쪽 정렬로 `update` | `exportSpec()` 텍스트 오버레이 style의 colorArgb/backgroundArgb/centerAligned가 그 값 |
| C12 | boundary | `TEXT_PALETTE`, `backgroundOf` | 8개, 모두 alpha 0xff, 중복 없음, 0xffffffff와 0xff000000 포함; `backgroundOf(c)`는 `(c and 0xffffff) or 0x80000000` |
| C13 | normal | `toggleFullscreen()` 두 번, 그리고 저장 복원 | true → false, 복원 시 값 유지 |
| C14 | normal | 저장 값이 `SavedStateHandle`에 들어갈 수 있는 타입만인지 | `SavedStateHandle.set`이 예외를 던지지 않음(C8 과정에서 확인) |

# Quality Applicability
| ISO/IEC 25010:2023 characteristic | applicable | rationale |
|---|---|---|
| Functional suitability | yes | F1–F9 |
| Performance efficiency | no | 저장 값 크기는 오버레이 수에 비례하나 편집 세션 규모가 작음. 미리보기 재구성 성능은 이전 run에서 범위 밖으로 결정됨 |
| Compatibility | no | 외부 시스템 연동 변경 없음 |
| Interaction capability | yes | 드래그 놓기, 도구함, 전체화면·floating 패널의 조작 가능성 |
| Reliability | yes | 프로세스 복원(I4)과 손상 값에 대한 내결함성 |
| Security | no | 새 권한이나 외부 입력 없음(`SYSTEM_ALERT_WINDOW` 미사용) |
| Maintainability | no | 기존 모듈 경계 유지 외 별도 목표 없음 |
| Flexibility | no | 화면 크기별 배치는 기존 ADR 동작 유지, 새 목표 없음 |
| Safety | no | 해당 없음 |

# Quality Requirements
| id | characteristic / subcharacteristic | target and context | measure method / inputs / unit | threshold and direction | evidence: automated, review, mutation | source |
|---|---|---|---|---|---|---|
| Q1 | Reliability / Recoverability | 프로세스 종료 후 복원 시 F8 필드 | 복원된 필드 수 / F8 필드 수(13), 단위 비율 | = 1.0 | automated C8; review V-R4; mutation M1 | I4 |
| Q2 | Reliability / Fault tolerance | 손상·누락 저장 값 | 예외 발생 건수, 단위 건 | = 0 | automated C9, C10 | I4 |
| Q3 | Interaction capability / Operability | 에뮬레이터에서 도구함 넣기·꺼내기, 전체화면, 패널 토글·이동, 스타일 변경 | 절차 단계 중 기대 결과와 다른 단계 수, 단위 건 | = 0 | review V-R1–V-R3 | I1–I3 |

# Verification Obligations
| id | parent requirement/Case ids | variant and target surface | test layer and selection policy | ISO/IEC/IEEE 29119-4 technique | coverage items | coverage target | observation and expected result | evidence procedure |
|---|---|---|---|---|---|---|---|---|
| V1 | F1–F3, C1–C7 | `EditorViewModel` 공개 API | unit(Robolectric), 모든 상태 전이 | state transition | 전이: 추가→도구함, 선택됨→도구함, 도구함→select 무시, 도구함→꺼냄, 도구함→삭제, 도구함→다른 path begin, 없는 id stow, 없는 id unstow (8개) | 100% | Cases 기대값 | Test command |
| V2 | F8, C8, C13, C14, Q1 | `SavedStateHandle` 왕복 | unit, F8 13개 필드 각각 | equivalence partitioning | F8 필드 13개 각각 기본값 아님 파티션 + 오버레이 종류 2개(text 스타일 전 필드, image transform 전 필드) | 100% | 새 VM의 필드와 `exportSpec()`이 원본과 같음. 원본 VM의 저장 값만 복사한 새 `SavedStateHandle` 사용(같은 인스턴스 재사용 금지) | Test command |
| V3 | C9, C10, Errors, Q2 | 빈/손상 저장 값 | unit | error guessing | 빈 handle, 오버레이 키 손상, 목록 키(flips/speeds) 손상, enum 키 알 수 없는 이름, selection 키 손상(begin 기본 구간), dangling 선택/도구함 id 제거(도구함 2개 이상 중 일부만 dangling일 때 나머지 유지 포함) | none — experience-based | 예외 없음, 해당 필드 기본값 | Test command |
| V4 | F7, C11, C12 | `TEXT_PALETTE`, `backgroundOf`, `update`→`exportSpec` | unit | boundary value analysis (2-value) + equivalence partitioning | 배경 null/non-null, 정렬 true/false, 팔레트 첫/마지막 색, alpha 경계(0x80 결과) | 100% | C11, C12 기대값 | Test command |
| V-R1 | F4, Q3 | 에뮬레이터 | review | scenario | 오버레이 행 드래그→도구함 아이콘 표시·미리보기 핸들 사라짐·미리보기 렌더 유지 → 아이콘 탭→핸들 복귀 → 내보낸 파일에 도구함 오버레이 렌더 | 100% | 각 단계 기대대로 | R1 |
| V-R2 | F5, F6, F9, Q3 | 에뮬레이터 | review | scenario | 전체화면 진입 → 패널 토글 → 패널 드래그(가장자리 밖으로 밀기 시도) → 패널에서 이미지 추가 및 도구함 탭 → 회전 후 패널 상태 유지 → 뒤로 가기로 전체화면 해제 | 100% | 각 단계 기대대로 | R1 |
| V-R3 | F7, Q3 | 에뮬레이터 | review | scenario | 글자색·배경·왼쪽 정렬 변경 → 미리보기 반영 → 내보낸 파일 프레임 반영 | 100% | 각 단계 기대대로 | R1 |
| V-R4 | F8, F9, Q1 | 에뮬레이터 | review | scenario | 개발자 옵션 "활동 유지 안 함" 또는 `adb shell am kill` 후 복귀 → 편집 상태·전체화면·패널 복원 | 100% | 복원됨 | R1 |

Structure-based coverage: 사용하지 않음 — 이 프로젝트 테스트 명령에 커버리지 도구가 없고, 명세 기반 항목으로 공개 동작을 모두 덮는다.

# Assumptions and Defaults
| id | decision | evidence and uncertainty | user approval or explicit delegation |
|---|---|---|---|
| A1 | floating 패널은 앱 내부 Compose 패널, 권한 없음 | `SYSTEM_ALERT_WINDOW`는 다른 앱 위 창에만 필요 | 사용자 선택 "앱 내 패널" |
| A2 | 도구함 = 편집 핸들만 숨김, 출력 포함, 탭으로 복귀, 하단 가로 줄 | 사용자 답변 | 사용자 확인 |
| A3 | 드래그 시작점은 편집 패널의 오버레이 행 길게 누르기 | 미리보기 핸들 드래그는 이미 이동/크기 조절에 쓰여 충돌 | 승인 필요(이 spec 승인) |
| A4 | 저장은 `SavedStateHandle`만 | 사용자 선택 "프로세스 복원만" | 사용자 선택 |
| A5 | 팔레트 8색 불투명, 배경은 같은 색 alpha 0x80 | 사용자 선택 "프리셋 팔레트"(설명에 반투명 배경 포함) | 승인 필요(이 spec 승인) |
| A6 | 편집 전체화면 상태는 `EditorViewModel.fullscreen`에 두고, 플레이어의 기존 `isFullscreen`과 분리 | 편집 종료 시 플레이어 전체화면에 영향 없게 함 | 승인 필요(이 spec 승인) |

# Traceability
| requirement id | Case ids | obligation ids | evidence procedure |
|---|---|---|---|
| F1–F3 | C1–C7 | V1 | Test command |
| F4 | — | V-R1 | R1 |
| F5, F6 | C13 | V2, V-R2 | Test command, R1 |
| F7 | C11, C12 | V4, V-R3 | Test command, R1 |
| F8 | C8–C10, C13, C14 | V2, V3, V-R4 | Test command, R1 |
| F9 | — | V-R2, V-R4 | R1 |
| Q1 | C8 | V2, V-R4 | Test command, R1 |
| Q2 | C9, C10 | V3 | Test command |
| Q3 | — | V-R1–V-R3 | R1 |

# Workflow Control
| item | value |
|---|---|
| correction batches used | 6 |
| verifier invocations | 3 |
| open finding ids | F-VR1-EDGE-01 |

Audit state:
| obligation id | spec version | evidence references and revision | accepted / open / invalidated / pending | rationale and mutation outcome | dependencies and reopening evidence |
|---|---|---|---|---|---|
| V1, V2, V4 | 1 | EditorToolboxViewModelTest, EditorStateRestoreTest, EditorTextStyleTest | accepted (verifier 1); 구현 init 변경(D1/D2)으로 V2 재확인 대상 | M3, M4 detected | 구현 복원 로직 변경 → V2 invalidated, V1/V4 변경 없음 |
| V3 | 2 | EditorStateRestoreErrorTest (전 키·전 필드 재작성) | pending (verifier 2) | M1, M2 detected | F-V3-01 수정 |
| V-R1–V-R4 | 3 | agent-docs/spec-logs/ad27e5f2d8c95388-review.md | pending (verifier 3) | D3·D4·D5 수정 후 재확인 통과 | — |

Execution ledger:
| attempt | finding / failure signature | cause hypothesis | changed approach / new evidence | result / disposition |
|---|---|---|---|---|
| 1 | test defect: C9/V2 field05 assumed cutMode default FAST | verified: 기존 기본값 PRECISE | 기본값 목록 제공, 전 필드 점검 | 수정됨 |
| 3 | V3 C10 F-V3-01 (verifier 1): String 키만 손상, overlays만 관찰 | verified | 전 키·전 필드 오라클·vacuous guard로 재작성 | 구현 결함 D1 검출 |
| 4 | D1: 손상된 selectedOverlayId가 그대로 복원 | verified | 복원 후 교차 필드 보정 추가 | 해결, 새 실패: selection 손상 시 0~0.1초 — spec 모호 → 사용자 결정으로 v2 |
| 5 | V3: path에 임의 문자열 주입 시 original/default 요구 | verified: 임의 문자열은 유효한 path, 해석 불가 값 아님 | 주입한 문자열 자체도 허용 | 수정됨 |
| 6 | D2: durationUs 손상 시 flips가 (0,0)으로 클램프 | verified | 복원 시 duration에 맞지 않는 flip/speed 항목 제거 | Test command green |
| M | mutation M1 enum 잘못된 기본값 / M2 flips 쓰레기 목록 / M3 stow 선택 유지 / M4 fullscreen 미저장 | — | seed.py backup→주입→restore | 4건 모두 의도한 단언이 검출(C10 ratioPreset·flips, C2, V2 field13). restore 완료 |
| 7 | verifier 2: F-V3-02 도구함 1개 fixture로는 부분 제거와 전체 초기화 구분 불가; F-SPEC-01 V3 coverage items 미갱신 | verified | spec v3 문구 갱신, 테스트 fixture 복수 stow | 진행 중 |
| 8 | V-R1: 에뮬레이터에서 오버레이 행 long-press 드래그가 시작되지 않음(목록 스크롤만 됨, adb motionevent DOWN→1.2s→MOVE→UP) | hypothesis: 자식 TextButton/verticalScroll이 제스처를 소비 | 구현자에게 D3로 전달 | 진행 중 |
| 9 | D3 해결: 행 자식 TextButton이 Main pass에서 Down 소비 → Initial pass long-press 감지로 교체 | verified by fix(에뮬레이터) | EditorToolPanel.kt | 해결 |
| 10 | case10b: raw 배열 값을 참조로 비교, 선택 없음 가정 오류 | verified | deepEquals 비교, 원래 선택값 비교 | 클래스 통과 |
| 11 | R1 review: D4 회전 후 floating 패널이 창 밖(오프셋 재클램프 없음), D5 패널이 자기 토글 버튼을 덮고 토글 아이콘 대비 낮음 | verified(에뮬레이터 스크린샷) | 구현자에게 전달 | 진행 중 |
| 12 | D4/D5 수정(재클램프, 기본 오프셋, 토글 z-order·대비), 패널 드래그 Initial pass 전환 | verified(에뮬레이터 재확인) | ClipApp.kt, dimens, colors | 해결; Test command green |
| 13 | verifier 3: F-VR1-EDGE-01 패널 하단 가장자리 행 드래그 간헐 실패 | 재현 3회 중 1회 실패(hypothesis) | 예산 소진 → limit handoff | open |
| 2 | test compile failure: `overlays`를 List로 사용 | verified: 기존 시그니처 StateFlow | `.value` 사용으로 전체 스윕 | 컴파일 성공, Test command green |

# Version Log
## v1
- 초안. handoff `8d9c1d27e3005b7b` 남은 범위와 사용자 결정(앱 내 패널, 도구함=탭 복귀·하단 줄, 프리셋 팔레트, 프로세스 복원만)에서 도출.
- 사용자 전체 승인(A3, A5, A6 포함), status active.
## v2
- Errors에 두 행 추가: selection 손상 시 `begin` 기본 구간(사용자 결정), 복원된 선택/도구함 id가 없는 오버레이를 가리키면 제거. 근거: V3 재작성 테스트가 드러낸 D1과 selection 기본값 모호성.
- 사용자 지시로 max_verifier_invocations 2 → 3 (2차 감사로 끝나지 않으면 한 번 더).
## v3
- V3 coverage items에 v2 Errors 두 행을 명시(F-SPEC-01). 동작 변경 없음: 이미 승인된 Errors 행의 문구 반영.
## v3 (종료)
- verifier 3/3 소진, F-VR1-EDGE-01 open으로 status limit, handoff 작성 후 spec-logs로 보관.
