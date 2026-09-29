---
version: 6
run_id: 357407171e6fd8b7
status: complete
base_commit: bc4079b30f4e4451529054c9c249248b17633673
max_verifier_invocations: 2
handoff: none
---

# User Intent
| id | stakeholder | intention | observable goal |
|---|---|---|---|
| I1 | 사용자 | 파일·미디어를 다중 선택해 관리 | 파일·비디오·오디오·이미지 탭에서 선택, 복사, 이동, ZIP 압축, 삭제, 복원 |
| I2 | 사용자 | 파일을 앱 안에서 확인 | 동영상·이미지·음성·PDF·TXT·CBZ 내장 뷰어 |
| I3 | 사용자 | 압축 파일을 폴더처럼 사용 | ZIP·CBZ·7z·tar.xz·tar.gz·tar 탐색·추출, 일반 압축 내부 편집 |

# Scope
In scope: 파일·비디오·오디오·이미지 탭, 공유 저장소 파일과 경로 없는 MediaStore URI 항목, 압축 내부 항목, 숨김 휴지통(7일), 내장 뷰어, 정상 쓰기 실패 시 원본 보존.
Out of scope: 강제 종료·백그라운드 중단 복구(기존 이슈 `agent-docs/issues/2f78d8c1b4e6a903-disk-io-interruption-recovery.md`에서 추적, 이번 실행은 이슈가 존재함만 확인), 플러그인 탭, 암호 압축 내부 편집, 지원하지 않는 압축 방식 변환, 화면 밖 항목 선택.

# Paths
Implementation: app/src/main, app/build.gradle.kts, feature/sidelayer/src/main, feature/sidelayer/build.gradle.kts, feature/mainlayer/src/main, feature/mainlayer/build.gradle.kts, core/data/src/main, core/data/build.gradle.kts, core/model/src/main, core/ui/src/main, core/designsystem/src/main, gradle/libs.versions.toml
Tests: app/src/test, feature/sidelayer/src/test, feature/mainlayer/src/test, core/data/src/test, core/testing/src/main, agent-docs/spec-logs/357407171e6fd8b7-review.md
Test command: ./gradlew :core:data:testDebugUnitTest :feature:sidelayer:testDebugUnitTest :feature:mainlayer:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
Review evidence: 에뮬레이터 `clip_tablet_1280x800`(API 35) 절차 — 네 탭, 여섯 뷰어, 경로 없는 URI 항목의 읽기·쓰기 동의·거부·삭제; 결과를 `agent-docs/spec-logs/357407171e6fd8b7-review.md`에 기록.

# Signatures
`core.data.operation` 패키지:
`sealed interface FileOperationSource { data class Local(val file: File); data class Content(val uri: Uri, val name: String); data class Archived(val archive: File, val path: String, val password: CharArray? = null) }`
`sealed interface OperationDestination { data class Directory(val directory: File); data class ArchiveDirectory(val archive: File, val path: String) }`
`enum class ConflictPolicy { ASK, SKIP, AUTO_RENAME, REPLACE }`
`data class OperationOutcome(val source: FileOperationSource, val destinationName: String? = null, val error: String? = null, val conflict: Boolean = false, val skipped: Boolean = false) { val succeeded: Boolean /* = error == null && !conflict && !skipped */ }`. 충돌에서 SKIP 처리된 항목은 `skipped = true`, 결과 메시지는 완료 수와 `건너뜀` 수를 구분 표시.
`open class FileOperationRepository @Inject constructor(context: Context, archives: ArchiveRepository)`: `suspend fun copyOrMove(sources: List<FileOperationSource>, destination: OperationDestination, conflictPolicy: ConflictPolicy = ConflictPolicy.ASK, move: Boolean = false): List<OperationOutcome>`; `suspend fun zip(sources: List<FileOperationSource>, target: File): Result<File>`; `protected open fun verify(source: FileOperationSource, destination: File): Boolean` (검증 실패 주입 지점). ASK 충돌은 `conflict = true`로 반환되고 해당 원본은 변경되지 않는다.
`data class ArchiveEntry(val path: String, val isDirectory: Boolean, val size: Long)`; `data class ArchiveListing(val entries: List<ArchiveEntry>, val readOnly: Boolean, val failureReason: ArchiveFailure?)`; `enum class ArchiveFailure { PASSWORD_REQUIRED, WRONG_PASSWORD, CORRUPT, UNSUPPORTED_METHOD, UNSAFE_ENTRY }`.
`sealed interface ArchiveEdit { Rename(path, newName); Copy(path, target); Move(path, target); Delete(path); Paste(targetDir, name, bytes: ByteArray); CreateZip(paths: List<String>, target: String) }`.
`open class ArchiveRepository @Inject constructor()`: `suspend fun list(archive: File, path: String = "", password: CharArray? = null): ArchiveListing` — `path` 바로 아래 자식만 반환, `ArchiveEntry.path`는 압축 내 전체 경로(예: `dir`), 명시 항목 없는 중간 폴더도 디렉터리 항목으로 합성; `suspend fun open(archive: File, path: String, password: CharArray? = null): ByteArray`; `suspend fun extract(archive: File, path: String, destination: File, password: CharArray? = null): Result<List<File>>`; `suspend fun edit(archive: File, edits: List<ArchiveEdit>): Result<Unit>`; `protected open fun beforeReplace(completed: File, original: File)` (교체 직전 실패 주입 지점); `open suspend fun checksum(archive: File, path: String, password: CharArray? = null): Long?` — ZIP·CBZ·7z는 저장된 CRC32, tar 계열은 항목 스트림으로 계산한 CRC32, 항목 없음은 null. 실패 Result의 예외는 `ArchiveException(val failure: ArchiveFailure)`.
`data class TrashEntry(val id: String, val name: String, val originalParent: String, val archiveOrigin: String?, val deletedAtMillis: Long)`; `class TrashRepository @Inject constructor(context: Context, archives: ArchiveRepository, clock: () -> Long)`: `suspend fun list(): List<TrashEntry>`; `suspend fun delete(sources: List<FileOperationSource>): List<OperationOutcome>`; `suspend fun restore(id: String): Result<File>`; `suspend fun empty(): Result<Unit>`; `suspend fun expire(): Int` (나이 ≥ 7일 = 604_800_000 ms 삭제).
`feature.sidelayer`: `@HiltViewModel class OperationViewModel @Inject constructor(FileOperationRepository, ArchiveRepository, TrashRepository)` 상태 `selectionMode: Boolean`, `selected: List<FileOperationSource>`, `staged: List<FileOperationSource>`, `stagedMove: Boolean`, `conflict: FileOperationSource?`, `message: String?`, `busy: Boolean`, `trashEntries: List<TrashEntry>`, `showTrash: Boolean` (각각 StateFlow); 메서드 `enterSelection()`, `clearSelection()`, `toggle(source)`, `leaveScreen()`, `stage(move: Boolean)`, `cancelStage()`, `paste(destination)`, `resolveConflict(policy, applyRemaining: Boolean)`, `cancelConflict()`, `delete(sources)`, `zip(sources, target: File)`, `openTrash()`, `closeTrash()`, `restore(id)`, `emptyTrash()`; 상태 `consentRequest: StateFlow<IntentSender?>`(URI 쓰기·삭제에 시스템 동의 필요 시 non-null, API 30+ `MediaStore.createWriteRequest/createDeleteRequest`, API 29 `RecoverableSecurityException`), 메서드 `onConsentResult(granted: Boolean)` — 동의 시 대기 작업 재시도, 거부 시 실패 표시·원본 유지, 어느 경우든 `consentRequest`는 null로.
UI 컴포저블: `BrowserScreen`은 기존 필수 인자와 `modifier`, `selectedPath`, `compact`를 유지하고 `onEntryLongClick: (FileEntry) -> Unit = {}`, `operations: OperationViewModel? = null`를 추가. `MediaGridScreen`은 기존 필수 인자와 `modifier`, `selectedUri`를 유지하고 `onEntryLongClick: (MediaEntry) -> Unit = {}`, `operations: OperationViewModel? = null`를 추가. `operations`가 null이면 기존 동작 그대로. `TabLayer`는 기존 인자에 `onOpenTrash: () -> Unit = {}`를 추가하고 `휴지통` 항목을 `설정` 바로 위에 둔다. `TrashDialog(entries: List<TrashEntry>, onRestore: (String) -> Unit, onEmpty: () -> Unit, onDismiss: () -> Unit)` (feature.sidelayer). 뷰어: `ViewerScreen(source: FileOperationSource, onClose: () -> Unit, modifier: Modifier = Modifier)` (feature.mainlayer) — 형식은 확장자/MIME으로 판별.
UI 관찰 라벨(contentDescription 또는 텍스트): `선택`, `선택 해제 (N)`, `복사`, `이동`, `ZIP`, `삭제`, `붙여넣기`, `이름 충돌`, `건너뛰기`, `자동 이름 변경`, `덮어쓰기`, `취소`, `남은 충돌에 적용`, `휴지통`, `복원`, `비우기`, `암호`.

v6 추가 시그니처: `OperationViewModel.refreshTick: StateFlow<Int>` — 변경 작업(붙여넣기, 삭제, ZIP, 복원, 압축 내부 편집) 완료마다 1 증가; `OperationViewModel.consumeMessage()` — 표시한 `message`를 null로.
`BrowserScreenRoute(onOpenVideo, modifier, selectedPath, compact, onOpenViewer: (FileOperationSource) -> Unit = {}, viewModel: BrowserViewModel = hiltViewModel(), operations: OperationViewModel = hiltViewModel())`와 `MediaGridRoute(kind, onEntrySelected, modifier, selectedUri, onEntryLongClick, treatAsPathless: Boolean = false, viewModel: MediaGridViewModel = hiltViewModel(), operations: OperationViewModel = hiltViewModel())`는 `refreshTick` 변화 시 현재 목록을 다시 읽고, `message`가 non-null이면 그 텍스트를 Snackbar로 표시한 뒤 `consumeMessage()`.
`BrowserViewModel`: `passwordRequest: StateFlow<File?>`, `passwordError: StateFlow<Boolean>`(직전 입력이 WRONG_PASSWORD), `submitArchivePassword(password: CharArray)`, `cancelArchivePassword()`. 암호 다이얼로그(압축 폴더 열기, CBZ 뷰어 공통) 라벨: 제목 `암호`, 입력 필드, `확인`, `취소`, 틀린 암호 시 `암호가 올바르지 않습니다`.

# Functional Requirements
| id | requirement | priority | source |
|---|---|---|---|
| R1 | 네 탭 상단에 `선택` 버튼. 비선택 모드 탭은 열기, 선택 모드 탭은 토글. 상단에 선택 수·해제, 하단 고정 바에 복사·이동·ZIP·삭제, 더보기에 정보. 항목 삼점 메뉴와 길게 누르기도 같은 작업 제공 — 파일 목록·미디어 그리드 모두 길게 누르기는 해당 항목의 삼점 메뉴(항목 작업 메뉴)를 연다(선택 모드 진입 아님) | must | 계획, v3 사용자 결정 |
| R2 | 선택은 현재 화면 항목만, 화면(폴더·탭) 이탈 시 해제 | must | 전제 |
| R3 | 복사·이동은 지정 후 대상 일반 폴더 또는 편집 가능한 압축 내부 폴더로 탐색해 `붙여넣기`로 완료 | must | 계획 |
| R4 | 이동은 복사 완료 후 크기·읽기 가능·(제공 시) 체크섬 검증 통과 시에만 원본 삭제 | must | 계획 |
| R5 | 이름 충돌은 건별 질의. `건너뛰기`·`자동 이름 변경`만 `남은 충돌에 적용` 가능 | must | 계획 |
| R6 | 동영상·이미지·음성·PDF·TXT·CBZ를 앱 안에서 열기(경로 없는 URI 항목 포함). CBZ 짧은 탭은 만화 뷰어(이름순 페이지), 폴더 탐색은 항목 메뉴 `폴더로 열기` | must | 계획, v4 사용자 결정 |
| R7 | ZIP·CBZ·7z·tar.xz·tar.gz·tar를 폴더처럼 탐색·추출 | must | 계획 |
| R8 | 암호 압축은 암호 입력 후 탐색·보기·추출, 내부 편집 차단 | must | 계획 |
| R9 | 일반 압축 내부 이름 변경·복사·이동·삭제·붙여넣기·ZIP 생성, 원래 형식으로 임시 결과 완성 후 교체 | must | 계획 |
| R10 | 지원하지 않는 방식·손상 파일은 이유 표시, 원본 유지 | must | 계획 |
| R11 | 삭제 항목은 공유 저장소 숨김 휴지통에 7일 보관. 설정 아이콘 위 휴지통 아이콘에서 복원·비우기 | must | 계획 |
| R12 | 일반 파일은 원래 폴더로, 압축 내부 삭제 항목은 압축 파일 폴더에 일반 파일로 복원. 이름 충돌 시 `_restore` 접미사 | must | 계획 |
| R14 | 복사·이동·삭제·ZIP·복원 등 변경 작업 완료 후 현재 목록을 새로 고치고 선택을 해제 | must | v4 기기 검증 결함 D7 |
| R15 | 디버그 빌드 설정에 `경로 없는 미디어로 취급` 토글: 켜면 미디어 탭 항목의 filePath를 null로 취급(기기 검증용, 릴리스 빌드에는 없음) | must | v4 사용자 결정 |
| R16 | 작업 결과·실패 메시지(`OperationViewModel.message`)를 Snackbar로 표시 | must | v6 검증 발견 D10 |
| R17 | 암호 압축 폴더 열기·암호 CBZ 뷰어 열기는 `암호` 다이얼로그로 입력받고, 틀리면 `암호가 올바르지 않습니다` 표시 후 재입력, 맞으면 목록/페이지 표시, `취소`는 원래 화면 유지 | must | v6 사용자 결정 (F3) |
| R13 | 경로 없는 미디어 항목은 URI 기반 작업과 필요한 Android 권한/동의 요청으로 처리 | must | 계획 |

# Errors
- 대상 없음·권한 거부·쓰기 실패·검증 불일치 — 실패 항목과 이유 메시지 — 해당 원본·다른 원본·대상 기존 상태 유지.
- 충돌 대화상자 취소 — 메시지 없음/대화상자 닫힘 — 덮어쓰기·원본 삭제 없음, 남은 항목 미처리.
- 잘못된 암호·손상·지원하지 않는 방식 — 각기 다른 `ArchiveFailure` 이유 표시 — 압축 파일 바이트 불변, 부분 추출물 없음.
- 절대 경로·`..`·위험 링크 항목 — `UNSAFE_ENTRY` — 대상 밖 출력 없음.
- 복원 대상 불가 — 실패 메시지 — 휴지통 항목·메타데이터 유지.
- URI 소실 또는 사용자 동의 거부 — 실패 메시지 — 완료로 처리하지 않고 원본 유지.

# Cases
| id | level | input / state | expected result |
|---|---|---|---|
| C1 | normal | 선택 → 두 항목 탭 → 해제 또는 폴더 이동 | 개수 2, 이후 0·선택 모드 종료 |
| C2 | normal | 두 파일 복사/이동 지정 → 폴더 탐색 → 붙여넣기 | 대상에 동일 바이트, 이동이면 원본 제거 |
| C3 | boundary | 여러 항목이 대상과 이름 충돌 | 건별 질의, 건너뛰기/자동 이름 변경만 남은 충돌에 적용, 덮어쓰기는 1건만 |
| C4 | error | 이동 중 검증 실패 주입 | 원본 유지, 실패 표시 |
| C5 | normal | 로컬·URI 전용 미디어, PDF, TXT, CBZ | 해당 내장 뷰어로 열림 |
| C6 | normal | 여섯 형식 각각 | 목록 이름과 추출 바이트 일치 |
| C7 | error | 잘못된 암호, 손상, 지원하지 않는 방식 | 구별된 이유, 원본 불변 |
| C8 | edge | `../`, 절대 경로, 외부 심볼릭 링크 항목 | 거부, 대상 밖 파일 없음 |
| C9 | normal | 일반 압축 각 형식에 편집 후 재열기 | 변경 반영, 형식 동일 |
| C10 | error | 교체 직전 실패 주입 | 원본 바이트 유지, 임시 파일 정리 |
| C11 | boundary | 휴지통 나이 7일−1ms, 7일, 7일+1ms | 첫째 유지, 나머지 만료 |
| C12 | normal | 일반/압축 출신 복원, 충돌 포함 | 원래 폴더 또는 압축 폴더, `_restore` 이름 |
| C13 | error | 권한 거부 또는 없는 URI | 실패 표시, 원본 유지 |
| C14 | edge | 빈 선택, 빈 압축, 없는 대상 | 작업 비활성 또는 설명 오류, 변경 없음 |
| C16 | normal | 선택 2개 삭제 완료 | 목록에서 사라지고 `선택` 표시로 복귀 |
| C17 | normal/error | 암호 ZIP 폴더 열기: 틀린 암호 → 맞는 암호; 암호 CBZ 뷰어 | 다이얼로그, 오류 문구, 이후 항목 표시 |
| C18 | error | 없는 URI 붙여넣기, 건너뜀 포함 붙여넣기 | 실패/`건너뜀` 메시지가 Snackbar로 보임 |
| C15 | edge | 암호 압축 내부 편집 시도·붙여넣기 대상 지정 | 편집 작업 비활성, 압축 불변 |

# Quality Applicability
| ISO/IEC 25010:2023 characteristic | applicable | rationale |
|---|---|---|
| Functional suitability | yes | 요청한 모든 작업 흐름 |
| Performance efficiency | yes | 압축·미디어 I/O가 UI를 막을 수 있음 |
| Compatibility | yes | 경로·URI 원본 저장소 동작 차이 (기기 검증은 API 35만) |
| Interaction capability | yes | 선택·충돌·실패 피드백 |
| Reliability | yes | 이동·압축·휴지통 실패 시 데이터 보존 |
| Security | yes | 압축 경로와 URI 권한이 신뢰 경계를 넘음 |
| Maintainability | yes | 로컬·URI·압축 백엔드가 같은 작업 의미를 공유 |
| Flexibility | no | 요청된 이식·적응 목표 없음 |
| Safety | yes | 파괴적 작업의 원본 보존 |

# Quality Requirements
| id | characteristic / subcharacteristic | target and context | measure method / inputs / unit | threshold and direction | evidence: automated, review, mutation | source |
|---|---|---|---|---|---|---|
| Q1 | Functional suitability / completeness | R1–R13 선언 변형 | 통과 의무 수 / 선언 의무 수, % | 100% (≥) | automated + review | 사용자 승인 (2026-09-29) |
| Q2 | Performance efficiency / time behaviour | UI에서 호출되는 복사·압축·휴지통·뷰어 I/O | 메인 스레드에서 실행되는 I/O 호출 수(코드 리뷰) | 0 (≤) | review | 사용자 승인 (2026-09-29) |
| Q3 | Compatibility / interoperability | API 35 에뮬레이터 × 경로·URI 원본 | 성공 기기 시나리오 / 2 | 2/2 (≥) | review | 사용자 승인 (2026-09-29) |
| Q4 | Interaction capability / user error protection | 선택·충돌·암호·파괴 작업 | 상태와 메시지를 단언한 UI 시나리오 / 6 | 6/6 (≥) | automated | 사용자 승인 (2026-09-29) |
| Q5 | Reliability / fault tolerance | 복사·검증·압축 쓰기 실패 주입 | 원본 보존 시나리오 / 3 | 3/3 (≥) | automated + mutation | 사용자 승인 (2026-09-29) |
| Q6 | Security / integrity | 위험 압축 경로 3종 + 비인가 URI 변경 | 거부된 위험 사례 / 4 | 4/4 (≥) | automated + review + mutation | 사용자 승인 (2026-09-29) |
| Q7 | Maintainability / modularity | 로컬·URI·압축 작업 | 공유 작업 계약 수(리뷰) | 1 (=) | review | 사용자 승인 (2026-09-29) |
| Q8 | Safety / fail safe | 이동·복원·압축 변경 실패 | 원본 바이트 보존 사례 / 3 | 3/3 (≥) | automated + mutation | 사용자 승인 (2026-09-29) |

# Verification Obligations
| id | parent requirement/Case ids | variant and target surface | test layer and selection policy | ISO/IEC/IEEE 29119-4 technique | coverage items | coverage target | observation and expected result | evidence procedure |
|---|---|---|---|---|---|---|---|---|
| V1 | R1,R2,R14,C1,C14,C16,Q4 | BrowserScreen·MediaGridScreen(VIDEO,AUDIO,IMAGE) | Robolectric Compose UI, 탭별 1회 | state transition | 선택 진입, 토글, 해제, 화면 이탈, 빈 선택 비활성, 길게 누르기 메뉴, 삭제 후 목록 갱신·선택 해제(항목 7은 `BrowserScreenRoute`/`MediaGridRoute`를 실제 ViewModel과 함께 구성해 관찰, 테스트가 목록을 직접 갱신하면 안 됨) | 7/7 | 라벨·개수·하단 바 활성 | Test command |
| V2 | R3,R4,C2,C4,C13,Q5,Q8 | FileOperationRepository(Local, Content via Robolectric ContentResolver, Archived) | 단위/통합 | decision table | 복사 성공, 이동 성공, 검증 실패, 권한 거부/없는 URI, 압축 원본 CRC 불일치 시 원본 유지, 건너뜀 결과 skipped=true | 6/6 | 대상 바이트·원본 유지 | Test command |
| V3 | R5,R13,C3,C13,Q4 | OperationViewModel 충돌·동의 흐름 | 단위 | state transition | 건별 질의, 건너뛰기 적용, 자동 이름 변경 적용, 덮어쓰기 비유지, 취소, 동의 허용 후 재시도, 동의 거부 시 실패·원본 유지 | 7/7 | 정책 지속 범위 | Test command |
| V4 | R6,R15,C5,Q3 | 여섯 뷰어(CBZ 탭=만화 뷰어) + URI 전용(R15 토글로 재현) | 기기 리뷰 | classification tree | 동영상, 이미지, 음성, PDF, TXT, CBZ, URI 전용 | 7/7 | 앱 내 표시 | review 기록 |
| V5 | R7,C6 | ArchiveRepository.list/extract | 통합(JVM, 테스트가 생성한 압축) | equivalence partitioning | ZIP, CBZ, 7z, tar.xz, tar.gz, tar | 6/6 | 이름·바이트 | Test command |
| V6 | R8,R10,C7,C15 | 암호 ZIP·CBZ·7z, 손상, 미지원 방식 | 통합 | equivalence partitioning | ZIP 성공, CBZ 성공, 7z 성공, 각 잘못된 암호(3), 손상, 미지원 방식, 암호 압축 편집 거부 | 9/9 | readOnly·이유·원본 불변 | Test command |
| V7 | R9,C9,C10,Q5,Q8 | ArchiveRepository.edit, 여섯 형식 | 통합 | scenario | 형식별 rename, copy, move, delete, paste, createZip + 교체 전 실패 1 | 37/37 | 재열기 내용·형식, 실패 시 바이트 유지 | Test command |
| V8 | R7,C8,Q6 | extract/list | 통합 | error guessing | `..` 경로, 절대 경로, 외부 링크 | none — experience-based (3건 모두 실행) | 대상 밖 파일 없음 | Test command |
| V9 | R11,R12,C11,C12,Q8 | TrashRepository | 단위(가짜 clock) | boundary value analysis (3-value) | 7d−1ms, 7d, 7d+1ms, 일반 복원, 압축 출신 복원, `_restore` 충돌, 복원 대상 불가 | 7/7 | 유지·만료·경로·이름·항목 유지 | Test command |
| V10 | R13,R15,C13,Q3,Q6 | URI 전용 MediaStore 항목(R15 토글로 재현) | 기기 리뷰 | decision table | 기기: 읽기, 없는 URI (2/2). 쓰기 동의·쓰기 거부·삭제 동의는 기기 해당 없음(A13), 증거는 V3 동의 허용·거부 | 2/2 기기 + V3 | 시스템 동의 요청·결과 | review 기록 |
| V11 | R11 | 휴지통 아이콘 위치·다이얼로그 | Robolectric UI | scenario | 아이콘이 설정 위, 복원, 비우기 | 3/3 | 표시와 호출 | Test command |
| V12 | Q2,Q7 | I/O 진입점, 백엔드 계약 | 리뷰 | scenario | 복사, 압축, 휴지통, 뷰어, 공유 계약 | 5/5 | Dispatchers.IO 사용, 단일 계약 | review 기록 |

| V13 | R8,R17,C17,Q4 | `BrowserScreenRoute` 암호 ZIP, `ViewerScreen` 암호 CBZ | Robolectric UI + 기기 1회(암호 ZIP 폴더 열기) | state transition | 폴더 열기 시 `암호` 표시, 틀린 암호 → `암호가 올바르지 않습니다`, 맞는 암호 → 항목 표시, `취소` → 원래 목록, CBZ 뷰어 `암호` 후 페이지 표시 | 5/5 + 기기 1 | 라벨·목록 | Test command + review |
| V14 | R16,C18,Q4 | `BrowserScreenRoute`(붙여넣기) | Robolectric UI | equivalence partitioning | 실패 메시지(없는 원본), 건너뜀 메시지 | 2/2 | Snackbar 텍스트 표시 | Test command |

구조 기반 커버리지는 사용하지 않는다 — 사용자 결정(현재 하네스 범위 밖).

# Assumptions and Defaults
| id | decision | evidence and uncertainty | user approval or explicit delegation |
|---|---|---|---|
| A1 | CBZ는 ZIP 컨테이너로 취급, 뷰어는 이미지 항목을 이름순 표시 | CBZ는 ZIP 기반 만화 형식 | 사용자 승인 (2026-09-29) |
| A2 | ZIP/CBZ는 Zip4j, 7z·tar 계열은 Commons Compress + XZ for Java | 계획 문서 인용 라이브러리 | 사용자 승인 (2026-09-29) |
| A3 | 휴지통은 `<외부 저장소 루트>/.clip_trash/`, 메타데이터는 같은 폴더의 JSON | 기존 루트는 `Environment.getExternalStorageDirectory()` | 사용자 승인 (2026-09-29) |
| A4 | 그대로 다시 쓸 수 없는 항목이 있는 압축 편집은 교체 전 실패(`UNSUPPORTED_METHOD`) | Commons Compress 쓰기 제한 | 사용자 승인 (2026-09-29) |
| A5 | 7z 암호 압축은 AES 읽기만, 암호 ZIP 편집 불가 | R8 | 사용자 승인 (2026-09-29) |
| A6 | PDF는 `android.graphics.pdf.PdfRenderer`(API 21+), TXT는 UTF-8 우선 표시 | 외부 라이브러리 불필요 | 사용자 승인 (2026-09-29) |
| A7 | 이동 검증: 로컬 원본은 원본·대상 CRC32 계산 비교, 압축 원본은 `ArchiveRepository.checksum` 값과 대상 CRC32 비교, URI 원본은 크기·읽기만 | v2 사용자 결정 | 사용자 승인 (2026-09-29, v2) |
| A9 | 충돌 `건너뛰기` 항목은 `skipped`로 성공과 구분 | v2 사용자 결정 | 사용자 승인 (2026-09-29) |
| A11 | 압축 목록은 한 단계씩(바로 아래 자식) 반환. tar 계열 재읽기 비용은 호환성 지원이라 수용 | v3 사용자 결정, tar는 주 사용 형식 아님 | 사용자 승인 (2026-09-29) |
| A12 | 경로 없는 URI 항목은 API 35에서 자연 생성이 어려워 R15 디버그 토글로 재현 | 기기 검증에서 MediaStore가 DATA를 채움 | 사용자 승인 (2026-09-29) |
| A13 | 앱은 MANAGE_EXTERNAL_STORAGE 필수(미허용 시 설정 화면으로 이동)라 MediaStore 쓰기·삭제 시스템 동의가 기기에서 발생하지 않음. 동의 흐름은 Robolectric V3로 검증 | API 35 에뮬레이터 관찰: 권한 거부 시 앱이 All files access 화면으로 이동, 권한 허용 시 동의 없이 삭제 | 사용자 승인 (2026-09-29) |
| A10 | 암호 7z 테스트 fixture는 `7zz`(Homebrew sevenzip)로 생성해 `core/data/src/test/resources`에 커밋 | JVM 라이브러리로 암호 7z 생성 불가 | 사용자 승인 (2026-09-29) |
| A8 | 붙여넣기 대기 중 탭 전환은 대기 유지, 선택만 해제 | R2는 선택만 규정 | 사용자 승인 (2026-09-29) |

# Traceability
| requirement id | Case ids | obligation ids | evidence procedure |
|---|---|---|---|
| R1,R2 | C1,C14 | V1 | Test command |
| R3,R4 | C2,C4 | V2 | Test command |
| R5 | C3 | V3 | Test command |
| R6 | C5 | V4 | review |
| R7 | C6,C8 | V5,V8 | Test command |
| R8,R10 | C7,C15 | V6 | Test command |
| R9 | C9,C10 | V7 | Test command |
| R11,R12 | C11,C12 | V9,V11 | Test command |
| R13 | C13 | V2,V10 | Test command + review |
| R16 | C18 | V14 | Test command |
| R17 | C17 | V13 | Test command + review |
| Q1–Q8 | C1–C18 | V1–V14 | Test command + review |

# Workflow Control
| item | value |
|---|---|
| correction batches used | 5 |
| verifier invocations | 2 |
| open finding ids | none |

Audit state (one entry per obligation; retain prior decisions in the execution ledger):
| obligation id | spec version | evidence references and revision | accepted / open / invalidated / pending | rationale and mutation outcome | dependencies and reopening evidence |
|---|---|---|---|---|---|
| V1 | 6 | *SelectionTest (1–6), BrowserScreenRouteDeleteTest, MediaGridRouteDeleteTest (7) | accepted | 검증자 2회차; M4 검출 | Route reload/선택 해제 변경 시 재개방 |
| V2,V3,V5,V6,V7,V8,V9,V11 | 5–6 | core/data operation tests, OperationViewModelTest, TrashDialogTest, TabLayerTrashTest | accepted | 검증자 1·2회차; M1·M2·M3 검출 | core/data 변경 시 재개방 |
| V4,V10,V12 | 6 | review.md Results + addendum | accepted | 기기 API 35, V10은 A13 범위 | 뷰어·URI 경로 변경 시 재개방 |
| V13 | 6 | BrowserScreenRoutePasswordTest, ViewerScreenPasswordTest, review addendum | accepted | 검증자 2회차 | 암호 다이얼로그 변경 시 재개방 |
| V14 | 6 | BrowserScreenRoutePasteMessageTest | accepted | M5 검출 | 메시지 UI 변경 시 재개방 |

Execution ledger (append attempts; preserve failed approaches):
| attempt | finding / failure signature | cause hypothesis | changed approach / new evidence | result / disposition |
|---|---|---|---|---|
| 1 | 구현자: 데이터 계층·ViewModel·휴지통만 완료, 선택 UI·뷰어·압축 탐색 UI·동의 UI 미구현; 테스트자: V1·V11 컴포저블 시그니처 부재, 암호 7z fixture 생성 불가 | spec v1 시그니처 공백·작업량 | v2에서 컴포저블·동의·checksum·skipped 시그니처 추가, 7zz fixture 승인 | 재디스패치 |
| 2 | Test command 실패: core:data 11건(V5·V6 목록 기대가 재귀 경로, 암호 7z 판정, 미지원 방식→CORRUPT), sidelayer 기존 BrowserViewModelTest 생성자 컴파일 오류 | list 의미 spec 공백; 7z 암호·방식 판별 구현 결함; 기존 테스트가 생성자 변경 미반영 | v3: list 한 단계, 길게 누르기=항목 메뉴, checksum open. 구현·테스트 수정 배치 1 | 부분 해결: 재실행 5건 실패 |
| 6 | 배치 4·5: D10 Snackbar, D11 암호 다이얼로그, D12 CBZ 뷰어 ViewModel 전달; 테스트 V1 항목 7 Route 수준(API 29 Robolectric: API 35에서 `Environment.isExternalStorageManager` 예외), V13 5/5, V14 2/2. Test command exit 0. 기기: 암호 ZIP 흐름·붙여넣기 실패 Snackbar pass. 변이 M4 Route reload 제거 → delete_V1_viaBrowserScreenRoute 검출; M5 Snackbar 제거 → V14 2건 검출 | - | - | 검증자 2회차 pass, open finding 없음 → complete |
| 5 | 검증자 1회차: retry. V2·V3·V4·V5–V9·V11·V12 accepted, V1(F1 삭제 후 갱신 증거를 테스트가 대신 수행), V10(F2 붙여넣기 실패 메시지 미관찰), F3 R8 암호 입력 UI 시그니처·의무 부재(spec challenge). 변이 M1 휴지통 `>=`→`>`: expire_V9_boundaryAges 실패로 검출; M2 verify 항상 true: archivedSourceMoveChecksumMismatch 검출; M3 `..` 허용: extract_V8_dotDotEntry 검출 | F2 조사 중 `OperationViewModel.message`가 UI 어디에도 렌더링되지 않음 확인(verified, grep) | F1: refreshTick 시그니처 명시 후 Route 수준 테스트; D10 메시지 표시; F3 사용자 결정 대기 | 대기 |
| 4 | 기기 검증(API 35): mp3 뷰어 '파일을 열 수 없습니다'(파일·음성 탭), 삭제 후 목록·선택 미갱신, CBZ 탭이 폴더로 열림, URI 전용 항목 재현 불가 | 동영상 플레이어 재사용이 오디오 전용 스트림 미지원(hypothesis); 변경 후 reload 누락(hypothesis) | v4: R6 CBZ 뷰어, R14 갱신, R15 디버그 토글; 배치 3 | 진행 |
| 3 | Test command: V6 미지원 방식 여전히 CORRUPT; V1 BrowserScreen/MediaGrid 테스트 4건 `has already set content` | JDK `ZipFile`이 CEN의 잘못된 method에서 예외를 던져 사전 검사가 삼켜짐(구현 결함, verified by 코드 읽기); 한 테스트에서 setContent 2회 호출(테스트 결함) | 배치 2: 중앙 디렉터리 직접 파싱으로 method 판별; 테스트는 테스트당 setContent 1회 | 진행 |

# Version Log
## v6
- 검증자 1회차 발견 반영: F1(V1 항목 7 증거를 Route 수준으로), D10/R16 메시지 Snackbar, 사용자 결정 (a) F3 → R17 암호 다이얼로그 요구·시그니처·V13, V14 추가. `refreshTick`/`consumeMessage`/Route 시그니처 명시.
## v5
- 사용자 결정 (a): V10 기기 커버리지를 읽기·없는 URI 2항목으로 축소, 동의 3항목은 A13으로 기기 해당 없음·V3로 대체. 근거: 에뮬레이터 관찰.
## v4
- 기기 검증 결과와 사용자 결정: CBZ 탭=만화 뷰어·`폴더로 열기` 메뉴(R6), 변경 후 목록 갱신·선택 해제(R14, C16, V1 7/7), 디버그 토글로 URI 전용 재현(R15, A12). V1 커버리지 항목 추가는 검증 정책 변경으로 사용자 재확인 필요.
## v3
- 사용자 결정: 길게 누르기(목록·그리드)=항목 삼점 메뉴(R1), `list`는 바로 아래 자식만(A11). `checksum`을 테스트 주입 지점으로 `open` 지정(동작 변경 없음). 근거: 첫 Test command 실행과 역할 보고의 해석 차이.
## v2
- 사용자 결정: 압축 원본 CRC32 비교(`checksum` 추가, A7 변경), `skipped` 결과(A9), URI 동의 흐름(`consentRequest`/`onConsentResult`), 암호 7z fixture를 7zz로 생성(A10). V1·V11용 컴포저블 시그니처 확정. V2·V3 커버리지 항목 확장. 근거: 1차 구현·테스트 보고의 시그니처 공백.
## v1
- 사용자 계획과 현재 저장소 구조로 새로 작성. 이전 중단 실행 `878368af3a25f02c`는 참고만 함.
- 승인: A1–A8, Q1–Q8, 단일 실행, 구조 기반 커버리지 미사용, 기기 검증은 API 35 AVD만.
