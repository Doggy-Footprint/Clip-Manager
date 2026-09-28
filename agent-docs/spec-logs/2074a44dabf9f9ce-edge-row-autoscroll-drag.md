---
version: 2
run_id: 2074a44dabf9f9ce
status: complete
base_commit: 5f3c3c80aec5f7fe2cbf47ab292c949904691fe9
max_verifier_invocations: 2
handoff: none
---

# User Intent
| id | stakeholder | intention | observable goal |
|---|---|---|---|
| I1 | 사용자 | 패널 가장자리에 잘린 오버레이 행도 long-press 드래그로 도구함에 넣고 싶다 | 잘린 행 long-press 시 행이 완전히 보이도록 자동 스크롤된 뒤 드래그가 시작되어 도구함에 들어간다 |

# Scope
In scope: 편집 도구 패널(`EditorToolPanel`) 오버레이 행의 long-press 드래그 시작 동작(F-VR1-EDGE-01, handoff `29be29e194db5768`).
Out of scope: 도구함 아이콘 드래그로 꺼내기, 드래그 중 가장자리 자동 스크롤, floating 패널 레이아웃 변경, 짧은 탭/스크롤 동작 변경.

# Paths
Implementation: feature/editor/src/main
Tests: feature/editor/src/test, agent-docs/spec-logs/2074a44dabf9f9ce-review.md
Test command: ./gradlew :feature:editor:testDebugUnitTest :core:editor:testDebugUnitTest assembleDebug
Review evidence: 에뮬레이터 `clip_tablet_1280x800` 수동 절차(adb input + screencap + uiautomator dump), 결과는 `agent-docs/spec-logs/2074a44dabf9f9ce-review.md`

# Signatures
기존 `OverlayRow`, `overlayRowDragBlock` 내부 변경만; 공개 시그니처 변경 없음.

# Functional Requirements
| id | requirement | priority | source |
|---|---|---|---|
| R1 | long-press 타임아웃 도달 시 행이 스크롤 뷰포트에 일부만 보이면, 행 전체가 보이도록 스크롤한 뒤 드래그 전송을 시작한다 | must | 사용자 결정 |
| R2 | 행이 이미 완전히 보이면 스크롤 없이 기존처럼 드래그를 시작한다 | must | 기존 F4 |
| R3 | 타임아웃 전에 떼면(짧은 탭) 드래그·스크롤 없이 행의 버튼 동작(선택/삭제)이 수행된다 | must | 기존 F4 |
| R4 | 타임아웃 전 touch slop 이상 이동하면 long-press로 판정하지 않고 목록 스크롤에 맡긴다 | must | 원인 (c) 대응 |

# Errors
- 드래그 시작 전 포인터가 떼어지거나 제스처 취소 — 드래그 섀도 없음 — 도구함·오버레이 상태 변화 없음.

# Cases
| id | level | input / state | expected result |
|---|---|---|---|
| C1 | edge | 하단에 잘린 행(y≈709~736)에서 long-press 후 도구함으로 드래그 | 행이 완전히 보이게 스크롤, 도구함에 들어감 |
| C1b | edge | 스크롤 뷰포트 상단(y=508)에 잘린 행에서 long-press 후 도구함으로 드래그 | 행이 완전히 보이게 스크롤, 도구함에 들어감 |
| C2 | normal | 완전히 보이는 행 long-press 드래그 | 스크롤 없음, 도구함에 들어감 |
| C3 | normal | 짧은 탭 | 선택만, 드래그 없음 |
| B0 | boundary | none — 타임아웃(400ms) 직전/직후 해제의 경계 정밀도는 플랫폼 withTimeout(longPressTimeoutMillis)에 위임; adb 입력 누름 시간 오차 230~1600ms로 측정 불가(A2) | - |
| C4 | error | long-press 전 세로로 끌기 | 목록 스크롤, 드래그 없음 |

# Quality Applicability
| ISO/IEC 25010:2023 characteristic | applicable | rationale |
|---|---|---|
| Functional suitability | yes | R1–R4 |
| Performance efficiency | no | 스크롤 1회, 측정 의미 없음 |
| Compatibility | no | 외부 연동 없음 |
| Interaction capability | yes | 간헐 실패 제거가 목적 |
| Reliability | yes | 반복 성공률 |
| Security | no | 해당 없음 |
| Maintainability | no | 국소 변경 |
| Flexibility | no | 해당 없음 |
| Safety | no | 해당 없음 |

# Quality Requirements
| id | characteristic / subcharacteristic | target and context | measure method / inputs / unit | threshold and direction | evidence: automated, review, mutation | source |
|---|---|---|---|---|---|---|
| Q1 | Reliability / faultlessness | C1 반복 | 성공 횟수/시도 횟수, `adb shell input swipe 840 736 250 740 1500` 계열 | 10/10 (≥) | review | 사용자 승인 |
| Q2 | Interaction capability / user error protection | C3·C4 회귀 | 각 3회 시도 중 오동작 횟수 | 0 (≤) | review | 사용자 승인 |

# Verification Obligations
| id | parent requirement/Case ids | variant and target surface | test layer and selection policy | ISO/IEC/IEEE 29119-4 technique | coverage items | coverage target | observation and expected result | evidence procedure |
|---|---|---|---|---|---|---|---|---|
| V1 | R1,R2,R3,R4 / C1–C4 | 실제 앱 편집 화면 | end-to-end, 에뮬레이터 | equivalence partitioning | {하단 잘린 행, 상단 잘린 행, 완전 노출 행, 짧은 탭, 이동} | 100% | screencap으로 스크롤·도구함 아이콘·선택 확인 | review 문서 |
| V2 | Q1 | C1 | end-to-end, 10회 | scenario | {C1×10} | 100% | 10/10 도구함 진입 | review 문서 |
| V3 | 회귀 | 기존 단위 테스트 + 빌드 | unit | none — 기존 suite 회귀 | 기존 suite | 100% 통과 | Test command green | Test command |

# Assumptions and Defaults
| id | decision | evidence and uncertainty | user approval or explicit delegation |
|---|---|---|---|
| A2 | long-press 타임아웃 경계 정밀도는 범위 밖, C3는 normal | adb 누름 시간 측정값 230~1600ms 편차(review.md) | 사용자 승인(v2) |
| A1 | 플랫폼 드래그앤드롭은 Robolectric에서 신뢰성 있게 재현 불가하므로 신규 자동 테스트 없이 에뮬레이터 review evidence로 검증 | handoff V-R1도 review로 수행 | 사용자 승인 |

# Traceability
| requirement id | Case ids | obligation ids | evidence procedure |
|---|---|---|---|
| R1 | C1,C1b | V1,V2 | review |
| R2 | C2 | V1 | review |
| R3 | C3 | V1 | review |
| R4 | C4 | V1 | review |

# Workflow Control
| item | value |
|---|---|
| correction batches used | 1 |
| verifier invocations | 2 |
| open finding ids | none |

Audit state:
| obligation id | spec version | evidence references and revision | accepted / open / invalidated / pending | rationale and mutation outcome | dependencies and reopening evidence |
|---|---|---|---|---|---|
| V1 | 2 | review.md V1 표 + 상단 변형 표 + C4b | accepted (verifier 2) | M1(스크롤 제거) C1b로 검출, M2(slop 무시) C4b로 검출 | EditorToolPanel.kt |
| V2 | 1 | review.md V2 표 | accepted (verifier 1) | - | EditorToolPanel.kt |
| V3 | 1 | Test command green | accepted (verifier 1) | - | - |

Execution ledger:
| attempt | finding / failure signature | cause hypothesis | changed approach / new evidence | result / disposition |
|---|---|---|---|---|
| 1 | lifecycle/telemetry 스크립트(.harness/bin) 부재 | 워크트리에 harness 미설치 | 생략하고 진행 | 기록만 |
| 2 | verifier 1: EG1(C3 경계 미측정), SC1(상단 잘림 미정) | 스펙 커버리지 공백 | 상단 5회 에뮬레이터 검증, adb 누름 시간 측정 → v2 개정 | verifier 2 PASS |
| 3 | M2 후보: 기존 C4(300ms swipe)는 slop 무시 결함을 구분 못 함 | swipe가 타임아웃 전에 종료 | C4b(슬롭 초과 후 0.9초 유지) 추가 | M1·M2 모두 검출, 원복 후 Test command green |

# Version Log
## v1
- 초기 작성. handoff 29be29e194db5768의 F-VR1-EDGE-01, 사용자 결정(자동 스크롤).
## v2
- verifier 1 SC1: 상단 잘린 행(C1b)을 R1 범위로 명시, V1 커버리지 항목에 추가. EG1: C3 level boundary→normal, 경계 정밀도는 A2로 범위 밖(사용자 승인).
