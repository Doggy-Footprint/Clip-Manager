---
version: 6
run_id: 878368af3a25f02c
status: aborted
base_commit: 19c0d9ddc169e363360366d138671709240a2d1b
max_verifier_invocations: 2
handoff: none
---

# User Intent
| id | stakeholder | intention | observable goal |
|---|---|---|
| I1 | 사용자 | 파일과 미디어를 다중 선택해 관리 | 네 탭에서 선택, 복사, 이동, 압축, 삭제, 복원 |
| I2 | 사용자 | 파일을 앱 안에서 확인 | 동영상, 이미지, 음성, PDF, TXT, CBZ 뷰어 |
| I3 | 사용자 | 압축 파일을 폴더처럼 사용 | ZIP, CBZ, 7z, tar.xz, tar.gz, tar 탐색, 추출, 일반 압축 내부 편집 |

# Scope
In scope: 기본 파일·비디오·오디오·이미지 탭, 공유 저장소 파일과 MediaStore URI, 압축 항목, 휴지통, 내장 뷰어, 정상 쓰기 실패 시 원본 보존.
Out of scope: 강제 종료 중 압축 교체 복구(별도 이슈), 앱 플러그인 탭 변경, 암호 압축 내부 편집, 지원하지 않는 압축 방식의 변환.

# Paths
Implementation: app/src/main, feature/sidelayer/src/main, feature/mainlayer/src/main, core/data/src/main, core/model/src/main, core/ui/src/main, gradle/libs.versions.toml, app/build.gradle.kts, core/data/build.gradle.kts, feature/sidelayer/build.gradle.kts, feature/mainlayer/build.gradle.kts, agent-docs/issues
Tests: app/src/test, app/src/androidTest, feature/sidelayer/src/test, core/data/src/test, feature/mainlayer/src/test, agent-docs/spec-logs/878368af3a25f02c-review.md
Test command: ./gradlew :core:data:testDebugUnitTest :feature:sidelayer:testDebugUnitTest :feature:mainlayer:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
Review evidence: Android API 24 and 36 device or emulator procedure, including URI-only MediaStore item and the four tabs, recorded in agent-docs/spec-logs/878368af3a25f02c-review.md; unavailable devices recorded as open evidence.

# Signatures
In `core.data.repository`: `FileOperationSource.Local(file: File)`, `.Content(uri: Uri, name: String)`, `.Archived(archive: File, path: String, password: CharArray? = null)`; `OperationDestination.Directory(directory: File)`, `.ArchiveDirectory(archive: File, path: String)`; `ConflictPolicy` values `ASK`, `SKIP`, `AUTO_RENAME`, `REPLACE`.
`open class FileOperationRepository @Inject constructor(Context, ArchiveRepository)` with `suspend fun copyOrMove(sources: List<FileOperationSource>, destination: OperationDestination, conflictPolicy: ConflictPolicy = ASK, move: Boolean = false): List<OperationOutcome>`; `OperationOutcome(source, destinationName: String? = null, error: String? = null, conflict: Boolean = false)` and `succeeded`. An `ASK` collision yields `conflict = true`; UI prompts and retries that source alone with the chosen policy. Only `SKIP` and `AUTO_RENAME` can be retained for later collisions. `protected open fun verify(source: FileOperationSource, destination: File)` is the deterministic verification-failure seam.
`ArchiveEntry(path: String, isDirectory: Boolean, size: Long)`, `ArchiveListing(entries, readOnly, failureReason)`, and `ArchiveEdit.Rename(path,newName)`, `.Copy(path,target)`, `.Move(path,target)`, `.Delete(path)`, `.Paste(path,bytes)`, `.CreateZip(paths,target)`.
`open class ArchiveRepository @Inject constructor()` with suspending `list(archive: File, path: String = "", password: CharArray? = null): ArchiveListing`; `open(archive: File, path: String, password: CharArray? = null): ByteArray`; `extract(archive: File, path: String, destination: File, password: CharArray? = null): List<File>`; `edit(archive: File, edits: List<ArchiveEdit>): Result<Unit>`; `contains(archive: File, path: String): Boolean`. `protected open fun beforeReplace(completed: File, original: File)` is the deterministic pre-replacement failure seam.
`TrashEntry(id: String, name: String, originalParent: String, archiveOrigin: String?, deletedAtMillis: Long, payload: File)`; `class TrashRepository @Inject constructor(Context, ArchiveRepository)` with suspending `list(): List<TrashEntry>`, `delete(sources: List<FileOperationSource>): List<OperationOutcome>`, `restore(id: String): Result<File>`, `empty(): Result<Unit>`, `expire(nowMillis: Long = System.currentTimeMillis()): Result<Int>`.
`BrowserScreenRoute(onOpenVideo: (String) -> Unit, onOpenFile: (File) -> Unit = {}, modifier: Modifier = Modifier, selectedPath: String? = null, compact: Boolean = false, viewModel: BrowserViewModel = hiltViewModel(), operations: OperationViewModel = hiltViewModel())`.
`MediaGridRoute(kind: MediaKind, onEntrySelected: (MediaEntry) -> Unit, modifier: Modifier = Modifier, selectedUri: String? = null, viewModel: MediaGridViewModel = hiltViewModel(), operations: OperationViewModel = hiltViewModel())`.
`OperationViewModel @Inject constructor(FileOperationRepository, ArchiveRepository, TrashRepository)` exposes observable selection mode, selected sources, staged sources/move flag, conflict, message, busy, trash entries and trash dialog; methods `enterSelection`, `clearSelection`, `toggle`, `leaveScreen`, `stage`, `cancelStage`, `paste`, `resolveConflict`, `delete`, `zip`, `openTrash`, `closeTrash`, `restore`, `emptyTrash`. Screen composables retain existing arguments and add optional operation state/callbacks.
Exact state types: `selectionMode: Boolean`, `selected: SnapshotStateList<FileOperationSource>`, `staged: List<FileOperationSource>`, `stagedMove: Boolean`, `conflict: FileOperationSource?`, `message: String?`, `busy: Boolean`, `trashEntries: List<TrashEntry>`, `showTrash: Boolean`. Methods: `enterSelection()`, `clearSelection()`, `toggle(source: FileOperationSource)`, `leaveScreen()`, `stage(move: Boolean)`, `cancelStage()`, `paste(destination: OperationDestination)`, `resolveConflict(policy: ConflictPolicy, applyRemaining: Boolean)`, `delete(sources: List<FileOperationSource>, onDone: () -> Unit = {})`, `zip(sources: List<FileOperationSource>, destination: File, onDone: () -> Unit = {})`, `openTrash()`, `closeTrash()`, `restore(id: String, onDone: () -> Unit = {})`, `emptyTrash()`.
`BrowserScreen` retains its six required arguments and optional `modifier`, `selectedPath`, `compact`; it adds `onEntryLongClick: (FileEntry) -> Unit = {}` and `operations: OperationViewModel? = null`. `MediaGridScreen` retains its four required arguments and optional `modifier`, `selectedUri`; it adds `onEntryLongClick: (MediaEntry) -> Unit = {}` and `operations: OperationViewModel? = null`. Operation header displays `Select` or `Clear (N)`; footer displays `Copy`, `Move`, `ZIP`, `Delete`; staged destination displays `Paste`; conflict dialog displays `Name conflict`, `Skip`, `Auto rename` and `Cancel`. These labels are the UI observation surface for V1/V3; accessible equivalents may be used.

# Functional Requirements
| id | requirement | priority | source |
|---|---|---|---|
| R1 | Four tabs have Select; tap opens normally outside selection mode and toggles selection within it; selection count and clear appear on top; copy, move, ZIP, delete appear in a fixed bottom bar; information and other actions appear in overflow; item overflow and long press offer actions | must | 사용자 계획 |
| R2 | Selection contains only visible current-screen entries and clears on leaving the screen | must | 사용자 전제 |
| R3 | Copy and move stage sources; user navigates to a normal folder or editable archive directory and taps Paste | must | 사용자 계획 |
| R4 | Move deletes each source only after copy completes and destination size, readability and available checksum pass verification | must | 사용자 계획 |
| R5 | Name conflicts prompt per item; Skip and Auto rename may apply to remaining conflicts; other choices do not persist | must | 사용자 계획 |
| R6 | Video, image, audio, PDF, TXT and CBZ open in-app, including URI-only media entries | must | 사용자 계획 |
| R7 | ZIP, CBZ, 7z, tar.xz, tar.gz and tar browse as folders and extract safely | must | 사용자 계획 |
| R8 | Password archives request a password for browse, view and extract and are read-only | must | 사용자 계획 |
| R9 | Unencrypted archives support internal rename, copy, move, delete, paste and ZIP creation, saved in their original format through a completed temporary result | must | 사용자 계획 |
| R10 | Unsupported compression method or malformed archive shows a reason and leaves original unchanged | must | 사용자 계획 |
| R11 | Deleted entries remain in hidden shared-storage trash for seven days; trash icon above settings opens Restore and Empty | must | 사용자 계획 |
| R12 | Regular file restore targets original parent; archived entry restore targets archive parent as an ordinary file; collision gets `_restore` suffix | must | 사용자 계획 |
| R13 | URI-only media operations use ContentResolver and applicable Android write/delete permission requests | must | 사용자 계획 |
| R14 | Create an issue in agent-docs/issues for absent forced-termination recovery during archive replacement | must | 사용자 계획 |

# Errors
- Destination unavailable, permission denied, write failure, verification mismatch: show failed item and reason; preserve its source, other sources and readable pre-operation destination state.
- Conflict dialog cancelled: do not overwrite destination or delete source; remaining items stay pending.
- Wrong archive password, corruption, unsupported method: show distinct reason; do not alter archive or extract partial output.
- Archive entry absolute path, parent traversal, or unsafe link: reject the entry and keep all output within the selected destination.
- Trash restore destination unavailable: retain trash entry and metadata; report failure.
- Media URI no longer resolves or user denies system consent: report failure without treating it as completed.

# Cases
| id | level | input / state | expected result |
|---|---|---|
| C1 | normal | Select, tap two entries, clear or navigate away | count 2 then selection 0 |
| C2 | normal | Stage two files, browse folder, Paste | two verified copies or moves at destination |
| C3 | boundary | same-name destination for several sources | each conflict prompts; Skip/Auto rename can carry to remaining conflicts only |
| C4 | error | injected destination verification failure during move | source remains, failure shown |
| C5 | normal | supported local and URI-only media | corresponding in-app viewer opens and URI stream is read |
| C6 | normal | each of six archive extensions | browse and extract expected bytes |
| C7 | error | wrong password, corrupt archive, unsupported compression | reason shown, original unchanged |
| C8 | edge | `../`, absolute path, or unsafe link archive entry | rejected, no path escapes destination |
| C9 | normal | edit each unencrypted format and reopen | requested change persists in same format |
| C10 | error | archive rewrite fails before replacement | original bytes remain |
| C11 | boundary | trash age just below, at, above seven days | below retained; at/above expired |
| C12 | normal | regular and archive-origin restore, with collision | original parent or archive parent, `_restore` unique name |
| C13 | error | permission denied or missing URI | operation fails visibly; source retained |
| C14 | edge | empty selection, empty archive, missing destination | action disabled or descriptive error; no mutation |

# Quality Applicability
| ISO/IEC 25010:2023 characteristic | applicable | rationale |
|---|---|---|
| Functional suitability | yes | All requested workflows must complete |
| Performance efficiency | yes | Archive and media I/O can block UI |
| Compatibility | yes | API 24–36 storage behavior differs |
| Interaction capability | yes | Selection, conflicts and failure feedback are user decisions |
| Reliability | yes | Move, archive and trash must preserve data on failure |
| Security | yes | Archive paths and URI permissions cross trust boundaries |
| Maintainability | yes | Several storage backends must share operation semantics |
| Flexibility | no | No requested adaptation or portability target beyond supported Android APIs |
| Safety | yes | Destructive file operations require source preservation |

# Quality Requirements
| id | characteristic / subcharacteristic | target and context | measure method / inputs / unit | threshold and direction | evidence: automated, review, mutation | source |
|---|---|---|---|---|---|---|
| Q1 | Functional suitability / completeness | R1–R14 on declared variants | passing required obligations / declared obligations | 100% (≥) | automated + review | 사용자 승인 요청 |
| Q2 | Performance efficiency / responsiveness | copy, archive, trash and media I/O from UI | code review of entry points and main-thread blocking operations, count | 0 blocking I/O operations (≤) | review | 사용자 승인 요청 |
| Q3 | Compatibility / interoperability | API 24 and 36, path and URI sources | successful device scenarios / 4 combinations | 4/4 (≥) | review | 사용자 승인 요청 |
| Q4 | Interaction capability / error protection | selection, conflict, password and destructive actions | UI scenarios with state and message asserted / 6 | 6/6 (≥) | automated + review | 사용자 승인 요청 |
| Q5 | Reliability / faultlessness | injected copy, verification and archive-write failures | source/original intact scenarios / 3 | 3/3 (≥) | automated + mutation | 사용자 승인 요청 |
| Q6 | Security / integrity | malicious archive paths and unauthorized URI mutation | rejected dangerous cases / 4 | 4/4 (≥) | automated + mutation | 사용자 승인 요청 |
| Q7 | Maintainability / modularity | local path, URI and archive operation backends | operation contract shared by backend implementations, review count | 1 contract (≥) | review | 사용자 승인 요청 |
| Q8 | Safety / data preservation | move, restore and archive mutation failure | source bytes preserved in injected failure cases / 3 | 3/3 (≥) | automated + mutation | 사용자 승인 요청 |

# Verification Obligations
| id | parent requirement/Case ids | variant and target surface | test layer and selection policy | ISO/IEC/IEEE 29119-4 technique | coverage items | coverage target | observation and expected result | evidence procedure |
|---|---|---|---|---|---|---|---|
| V1 | R1,R2,C1,C14,Q4 | four tabs and navigation | UI, one test per tab plus exit | state transition | select, toggle, clear, leave, empty | 5/5 | counts, modes, actions and clearing | Compose UI tests |
| V2 | R3,R4,C2,C4,Q5,Q8 | file and URI operation repository | unit/integration, copy/move/fail | decision table | copy success, move success, verification fail, permission denial | 4/4 | result bytes and source retention | repository tests |
| V3 | R5,C3,Q4 | conflict resolver and UI | unit + UI, prompt sequence | state transition | per-item, Skip remaining, Auto rename remaining, cancel | 4/4 | only approved policy persists | conflict tests |
| V4 | R6,C5,Q3 | six viewers, local and URI-only | device, each format and URI mode | classification tree | video, image, audio, PDF, TXT, CBZ, URI-only | 7/7 | in-app content visibly/readably opens | device review |
| V5 | R7,C6 | archive service | integration, all named formats | equivalence partitioning | ZIP, CBZ, 7z, tar.xz, tar.gz, tar | 6/6 | browse names and extraction bytes | archive tests |
| V6 | R8,R10,C7 | encrypted ZIP, CBZ and 7z, plus invalid archives | integration, each encrypted format and failures | equivalence partitioning | ZIP success, CBZ success, 7z success, wrong password for each, corrupt, unsupported method | 8/8 | read-only status, reason, original bytes | archive tests |
| V7 | R9,C9,C10,Q5,Q8 | archive mutation | integration, all six formats | scenario | rename, copy, move, delete, paste, ZIP creation, write fail | 7/7 per supported format | reopen verifies contents and format; failure retains bytes | archive tests |
| V8 | R7,C8,Q6 | extraction and archive entry resolution | integration, malicious entries | error guessing | parent traversal, absolute path, unsafe link | 3/3 | no outside output | security tests |
| V9 | R11,R12,C11,C12,Q8 | trash store and UI | unit + UI, clock boundary and origins | boundary value analysis (3-value) | 7d−1ms, 7d, 7d+1ms, regular, archive, collision | 6/6 | retention, expiry, restore names/paths | trash tests |
| V10 | R13,C13,Q3,Q6 | URI-only MediaStore | device, grant/deny/missing URI | decision table | read, write grant, write deny, delete grant, missing | 5/5 | no path dependency, correct system consent and result | device review |
| V11 | R14 | issue index and issue file | review | scenario | issue present | 1/1 | indexed issue states failure and revisit condition | document review |
| V12 | Q2,Q7 | all I/O entry points and backend contracts | review | scenario | copy, archive, trash, viewer, backend interface | 5/5 | background I/O and single shared contract | code review record |

# Assumptions and Defaults
| id | decision | evidence and uncertainty | user approval or explicit delegation |
|---|---|---|---|
| A1 | CBZ uses ZIP container semantics; encrypted CBZ follows password read-only rule | CBZ is a ZIP-based comic format; exact image ordering can use archive entry order | 사용자 승인 요청 |
| A2 | ZIP/CBZ use Zip4j and 7z/tar families use Commons Compress with XZ for Java | cited library docs; methods and metadata unsupported by libraries produce R10 error | 사용자 승인 요청 |
| A3 | Hidden trash resides in shared-storage root and metadata is durable app data | existing file root is `Environment.getExternalStorageDirectory()`; exact hidden directory name is implementation detail | 사용자 승인 요청 |
| A4 | Archive edits involving entries whose method or metadata cannot be faithfully rewritten fail before replacement | Commons Compress lists write limitations; no silent format change | 사용자 승인 요청 |

# Traceability
| requirement id | Case ids | obligation ids | evidence procedure |
|---|---|---|---|
| R1,R2 | C1,C14 | V1 | UI tests |
| R3,R4 | C2,C4 | V2 | repository tests |
| R5 | C3 | V3 | conflict tests |
| R6 | C5 | V4 | device review |
| R7 | C6,C8 | V5,V8 | archive/security tests |
| R8,R10 | C7 | V6 | archive tests |
| R9 | C9,C10 | V7 | archive tests |
| R11,R12 | C11,C12 | V9 | trash tests |
| R13 | C13 | V10 | device review |
| R14 | C14 | V11 | document review |
| Q1–Q8 | C1–C14 | V1–V12 | Test command and review record |

# Workflow Control
| item | value |
|---|---|
| correction batches used | 0 |
| verifier invocations | 0 |
| open finding ids | none |

Audit state:
| obligation id | spec version | evidence references and revision | accepted / open / invalidated / pending | rationale and mutation outcome | dependencies and reopening evidence |
|---|---|---|---|---|---|
| V1–V12 | 1 | none | pending | Awaiting approval and implementation | all |

Execution ledger:
| attempt | finding / failure signature | cause hypothesis | changed approach / new evidence | result / disposition |
|---|---|---|---|---|

# Version Log
## v6
- Fixed state, callback and visible-label signatures for UI tests. No behavioral expectation, quality threshold or verification policy changed.
## v5
- Fixed public UI route and operation state signatures for independent UI tests. No behavioral expectation, quality threshold or verification policy changed.
## v4
- Clarified that repository operation methods are suspending, matching the established API. No behavioral expectation, quality threshold or verification policy changed.
## v3
- Fixed constructors and deterministic failure seams for V2 and V7 and the trash API so independent tests can compile. No behavioral expectation, quality threshold or verification policy changed.
## v2
- Fixed public operation and archive signatures so the independent test role can compile tests; clarified V6's already intended encrypted-format selection as ZIP, CBZ and 7z. No behavioral expectation, quality threshold or verification policy changed.
## v1
- Drafted from the user-provided implementation plan and current repository boundaries.
