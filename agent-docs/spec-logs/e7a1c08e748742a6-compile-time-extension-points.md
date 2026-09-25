---
version: 3
run_id: e7a1c08e748742a6
status: complete
base_commit: bbdb5dcfa05581370bf1d2d5b484f5d189d8312c
max_verifier_invocations: 2
handoff: none
---

# User Intent
| id | stakeholder | intention | observable goal |
|---|---|---|---|
| UI1 | app owner | Tabs, settings sections, built-in tab explorer content extensible via optional compile-time Gradle modules (Hilt multibindings) | An `:ext:*` module contributing bindings shows its tab/section/override without editing app code |
| UI2 | app owner | Settings screen with public dummy clicker "hi, bye 반복해서 바꿔줘 누를 때마다" | Clicking the Settings rail item shows settings; clicker label toggles hi→bye→hi |
| UI3 | app owner | No optional module → build and behavior identical to today | Build succeeds with no `extensionModules.dir`; built-in tabs behave as before |

# Scope
In scope: new `core/extension-api` module (interfaces, `BuiltInTab`, `@Multibinds` module); app tab key model, extension tab rendering, override application, settings screen; `androidx-datastore-preferences` catalog entry; optional `:ext:*` include from `local.properties`.
Out of scope: viewer-pane callbacks for extensions; clicker persistence across restart; runtime plugins; ADR; any real extension module in the repo.

# Paths
Implementation: core/extension-api/build.gradle.kts, core/extension-api/src/main, settings.gradle.kts, app/build.gradle.kts, gradle/libs.versions.toml, app/src/main
Tests: app/src/test
Test command: ./gradlew assembleDebug testDebugUnitTest
Review evidence: RV1 — main creates a sample extension module under the scratchpad (one TabExtension, one SettingsSection, one BuiltInTabOverride for IMAGES), sets `extensionModules.dir` in local.properties, runs `./gradlew :app:assembleDebug`, confirms `:ext:<name>` appears in `./gradlew projects` and the build succeeds, then removes the property. RV2 — `./gradlew :core:extension-api:dependencies --configuration debugRuntimeClasspath` shows no `:core:database`.

# Signatures
core/extension-api (package `com.doggy.clip_manager.core.extension`):
enum class BuiltInTab { FILES, VIDEOS, AUDIO, IMAGES }
interface TabExtension { val id: String; val icon: ImageVector; val order: Int; @Composable fun label(): String; @Composable fun Content(modifier: Modifier) }
interface SettingsSection { val id: String; val order: Int; @Composable fun title(): String; @Composable fun Content() }
interface BuiltInTabOverride { val target: BuiltInTab; @Composable fun Content(modifier: Modifier, default: @Composable (Modifier) -> Unit) }
@Module @InstallIn(SingletonComponent::class) abstract class ExtensionBindingsModule { @Multibinds abstract fun tabs(): Set<TabExtension>; @Multibinds abstract fun sections(): Set<SettingsSection>; @Multibinds abstract fun overrides(): Set<BuiltInTabOverride> }
app (package `com.doggy.clip_manager.ui`):
enum class MediaTab(icon, label, val builtIn: BuiltInTab)  // existing entries unchanged
sealed interface TabKey { data class BuiltIn(val tab: MediaTab); data class Extension(val id: String); data object Settings }
val TabKeySaver: Saver<TabKey, String>
fun orderedTabExtensions(set: Set<TabExtension>): List<TabExtension>          // by order, then id
fun orderedSettingsSections(set: Set<SettingsSection>): List<SettingsSection> // by order, then id
fun overridesByTarget(set: Set<BuiltInTabOverride>): Map<BuiltInTab, BuiltInTabOverride>
fun resolveTabKey(key: TabKey, extensionIds: Set<String>): TabKey
@Composable fun TabLayer(selected: TabKey, onSelect: (TabKey) -> Unit, extensions: List<TabExtension>, compact: Boolean, modifier: Modifier = Modifier)
@Composable fun SettingsScreen(sections: List<SettingsSection>, modifier: Modifier = Modifier)
@HiltViewModel class ExtensionsViewModel @Inject constructor(Set<TabExtension>, Set<SettingsSection>, Set<BuiltInTabOverride>) { val tabs; val sections; val overrides }

# Functional Requirements
| id | requirement | priority | source |
|---|---|---|---|
| FR1 | With no contributions, the three injected sets are empty and the app compiles/runs | must | UI3 |
| FR2 | Extension tabs render in the rail after the 4 built-in tabs and before the divider, ordered by `order` asc then `id` asc | must | UI1 |
| FR3 | Selecting an extension tab marks it selected and renders its `Content` in the explorer slot | must | UI1 |
| FR4 | For a built-in tab with an override, the explorer slot renders `override.Content(modifier, default)` where `default` is the unchanged built-in content; editor-editing branch keeps priority | must | UI1 |
| FR5 | Two or more overrides with the same `target` → `IllegalStateException` from `overridesByTarget` (and thus app start) | must | user decision |
| FR6 | `resolveTabKey` returns `BuiltIn(FILES)` for `Extension(id)` whose id is absent; other keys unchanged | must | handoff |
| FR7 | Settings rail item selects `TabKey.Settings` (shown selected); the explorer+viewer area is replaced by `SettingsScreen` | must | UI2, user decision |
| FR8 | SettingsScreen shows the public section first (clicker), then sections by `order` asc then `id` asc, each with its `title()` then `Content()` | must | UI2 |
| FR9 | Clicker label starts `hi`, toggles `hi`↔`bye` on every click, survives configuration change (`rememberSaveable`) | must | UI2, user decision |
| FR10 | `libs.versions.toml` defines `androidx-datastore-preferences` | must | handoff |
| FR11 | If `local.properties` has `extensionModules.dir`, every direct subdirectory containing `build.gradle.kts` is included as `:ext:<dirName>` with projectDir there, and `app` gets `implementation` on each; absent property → no change | must | handoff |
| FR12 | `core/extension-api` depends only on Compose/designsystem/Hilt, never `core/database` | must | handoff |

# Errors
Duplicate override target — `IllegalStateException` whose message names the target — app fails at start (no partial state).
Restored extension id missing — no exception; selection becomes `BuiltIn(FILES)`.
`extensionModules.dir` points to non-existent dir — no modules included, build proceeds as without the property.

# Cases
| id | level | input / state | expected result |
|---|---|---|---|
| C1 | normal | ext tabs {b(order 2), a(order 1)} | ordered [a, b] |
| C2 | boundary | ext tabs same order, ids "y","x" | ordered [x, y] |
| C3 | boundary | empty sets | empty lists / empty map |
| C4 | error | two overrides target IMAGES | IllegalStateException mentioning IMAGES |
| C5 | normal | overrides IMAGES, FILES | map has both keys |
| C6 | edge | resolveTabKey(Extension("gone"), {"a"}) | BuiltIn(FILES) |
| C7 | normal | resolveTabKey(Extension("a"), {"a"}), BuiltIn(VIDEOS), Settings | unchanged |
| C8 | normal | TabLayer with ext tab "Ext" rendered, click it | onSelect(Extension(id)) called; label shown |
| C9 | normal | TabLayer click Settings | onSelect(Settings) |
| C10 | normal | SettingsScreen, click clicker 1/2/3 times | label hi → bye → hi → bye |
| C11 | edge | SettingsScreen clicked once, state restoration (StateRestorationTester) | label stays bye |
| C12 | normal | SettingsScreen with sections order 2 "S2", order 1 "S1" | clicker node, then S1, then S2 vertical order |
| C13 | boundary | settings sections same order ids "b","a" | orderedSettingsSections → [a, b] |

# Quality Applicability
| ISO/IEC 25010:2023 characteristic | applicable | rationale |
|---|---|---|
| Functional suitability | yes | core feature |
| Performance efficiency | no | tiny static sets |
| Compatibility | yes | no-ext build must be unchanged (co-existence of optional modules) |
| Interaction capability | no | dummy UI, no UX targets requested |
| Reliability | yes | stale saved extension key must not crash |
| Security | no | compile-time only, no new input surface |
| Maintainability | yes | extension-api must stay decoupled from database |
| Flexibility | yes | adding a module requires no app code edit |
| Safety | no | no physical harm surface |

# Quality Requirements
| id | characteristic / subcharacteristic | target and context | measure method / inputs / unit | threshold and direction | evidence: automated, review, mutation | source |
|---|---|---|---|---|---|---|
| QR1 | Compatibility / co-existence | build without property | Test command exit code | = 0 | automated | UI3 |
| QR2 | Reliability / fault tolerance | stale extension key | C6 pass count | 1/1 | automated, mutation | FR6 |
| QR3 | Maintainability / modularity | extension-api classpath | count of `:core:database` in RV2 output | = 0 | review RV2 | FR12 |
| QR4 | Flexibility / installability | sample ext module | RV1 build exit code, `:ext:` listed | = 0 and listed | review RV1 | FR11 |

# Verification Obligations
| id | parent requirement/Case ids | variant and target surface | test layer and selection policy | ISO/IEC/IEEE 29119-4 technique | coverage items | coverage target | observation and expected result | evidence procedure |
|---|---|---|---|---|---|---|---|---|
| VO1 | FR2, C1, C2, C3 | orderedTabExtensions | unit | equivalence partitioning | {distinct orders, equal orders, empty} | 100% | list order as in Cases | Test command |
| VO2 | FR8, C13, C3 | orderedSettingsSections | unit | equivalence partitioning | {distinct orders, equal orders, empty} | 100% | list order | Test command |
| VO3 | FR5, C3, C4, C5 | overridesByTarget | unit | equivalence partitioning | {empty, unique targets, duplicate target} | 100% | map / exception with target name | Test command |
| VO4 | FR6, C6, C7 | resolveTabKey | unit | decision table | {Extension present, Extension absent, BuiltIn, Settings} | 100% | per Cases | Test command |
| VO5 | FR2, FR3, FR7, C8, C9 | TabLayer composable | Robolectric Compose UI test | scenario | {ext label shown, click ext → Extension(id), click Settings → Settings, ext placed after last built-in and before post-divider item, Settings item selected iff selected == Settings} | 100% | onSelect args | Test command |
| VO6 | FR8, FR9, C10, C11, C12 | SettingsScreen composable | Robolectric Compose UI test | state transition | transitions {hi→bye, bye→hi, restore bye}, section order item | 100% | label text / node bounds top order | Test command |
| VO7 | FR1, FR4, FR10, FR11, FR12, QR1, QR3, QR4 | build & wiring | build/review | error guessing | {no-ext build, sample ext build, database decoupling} | none — experience-based | exit codes, project listing, classpath grep | Test command, RV1, RV2 |

# Assumptions and Defaults
| id | decision | evidence and uncertainty | user approval or explicit delegation |
|---|---|---|---|
| A1 | Keep `MediaTab` in app, add `BuiltInTab` enum in API mapped via `MediaTab.builtIn` | MediaTab uses app R.string | user approved spec v1 |
| A2 | Tie-break by `id` for equal `order` | handoff silent | user approved spec v1 |
| A3 | Clicker strings "hi"/"bye" are literal (not localized) resources in strings.xml | user wording | user approved spec v1 |
| A4 | FR4 (ClipApp override wiring) is verified by build + RV1 only, no ClipApp UI test (ClipApp needs Hilt/media stack) | cost | user approved spec v1 |

# Traceability
| requirement id | Case ids | obligation ids | evidence procedure |
|---|---|---|---|
| FR1 | C3 | VO7 | Test command |
| FR2 | C1, C2, C8 | VO1, VO5 | Test command |
| FR3 | C8 | VO5 | Test command |
| FR4 | — | VO7 | RV1 |
| FR5 | C4, C5 | VO3 | Test command |
| FR6 | C6, C7 | VO4 | Test command |
| FR7 | C9 | VO5 | Test command |
| FR8 | C12, C13 | VO2, VO6 | Test command |
| FR9 | C10, C11 | VO6 | Test command |
| FR10, FR11, FR12 | — | VO7 | RV1, RV2 |

# Workflow Control
| item | value |
|---|---|
| correction batches used | 4 |
| verifier invocations | 2 |
| open finding ids | none |

Audit state:
| obligation id | spec version | evidence references and revision | accepted / open / invalidated / pending | rationale and mutation outcome | dependencies and reopening evidence |
|---|---|---|---|---|---|
| VO1–VO6 | 2 | app/src/test/.../ui/*Test.kt, 21 tests pass | accepted | verifier-2 pass; mutations M1–M5 killed | — |
| VO7 | 2 | Test command exit 0; RV1 (:ext:sample included, app depends, assembleDebug exit 0, removed after); RV2 (0 :core:database) | accepted | verifier-2 pass; mutations M1–M5 killed | — |

Execution ledger:
| attempt | finding / failure signature | cause hypothesis | changed approach / new evidence | result / disposition |
|---|---|---|---|---|
| 1 | parcelize alias fails ("already on classpath with unknown version"); workaround needed compiler classpath hacks | build-logic included build exposes unversioned KGP | TabKey made plain sealed interface + String Saver | build ok; spec v2 signature |
| 2 | test compile: unresolved import assertExists | test defect (member, not extension) | import removed | 21 tests pass |
| 3 | main review: editor.editing priority only applied to BuiltIn keys, not Extension | implementation defect vs original behavior (editing always shows image grid) | editing checked before key dispatch | Test command exit 0 |
| 4 | verifier-1 F1/F2: FR2 placement, FR7 selected unverified | obligation coverage items omitted these FR clauses | added 3 TabLayer tests (VO5 v3) | 24 pass |
| 5 | F1 test failed: post-divider top = 0 | test defect: clipped boundsInRoot in scroll container on short viewport | positionInRoot + requiredHeight(2000.dp) | 24 pass |
| 6 | verifier-1 F4: no QR2 mutation reported | evidence not passed to verifier | mutations executed: M1 fallback→killed by c6; M2 dup guard→killed by c4; M3 tie-break→killed by c2,c13; M4 settings selected=false→killed by f2; M5 ext before built-ins→killed by f1 (first run inconclusive: config-cache error; rerun --no-configuration-cache) | all restored, Test command exit 0 |

# Version Log
## v1
- Initial draft from handoff 4d5556d9c2c3b575 and user decisions (rememberSaveable, explorer-only API, runtime error on duplicate override, no ADR, settings replaces explorer+viewer).
## v2
- TabKey no longer Parcelable; persisted with TabKeySaver (signature-only; parcelize plugin unusable with build-logic's KGP classpath). Not a behavior change.
## v3
- VO5 coverage items extended with FR2 placement and FR7 selected state (verifier-1 F1/F2); no behavior change.
