# Compile-time extension points (tabs, settings sections, built-in tab override)

## Goal

Make tabs, settings sections, and the built-in tabs' explorer content extensible through optional Gradle modules registered with Hilt multibindings. Add a settings screen whose public section holds a dummy clicker:

> hi, bye 반복해서 바꿔줘 누를 때마다

With no optional module present, the app must build and behave exactly as today.

## State

- Branch: `feat/plugin`, base commit `427425d11964eeae81a022f18d6533b5a210e4e6`.
- Changed files: none. No implementation has started.

Deciding facts in the current code:
- The tabs are a closed enum, `MediaTab` (`app/src/main/java/com/doggy/clip_manager/ui/TabLayer.kt:27`), and the explorer content is chosen with `when (selectedTab)` in `app/src/main/java/com/doggy/clip_manager/ui/ClipApp.kt:82-111`. IMAGES renders `MediaGridRoute(kind = MediaKind.IMAGE)`.
- The Settings rail item has `onClick = {}` (`TabLayer.kt:71-77`). There is no settings screen and no DataStore/SharedPreferences, and `gradle/libs.versions.toml` has no datastore entry.
- Room: one DB, `ClipDatabase`, file name `clip-database`, provided in `core/database/.../di/DatabaseModule.kt`. `AndroidRoomConventionPlugin` sets `schemaDirectory("$projectDir/schemas")`, which is module-relative, so an optional module with its own DB exports its schemas inside that module.
- `settings.gradle.kts` uses `includeBuild("build-logic")` and `TYPESAFE_PROJECT_ACCESSORS`. Modules included into this build can use the `clip.*` convention plugins and the `libs` catalog.

## Failed Attempts

| attempt | failure evidence | cause |
|---|---|---|
| none | — | — |

## Next Step

Start a workflow-approach spec covering:

1. **New module `core/extension-api`** (depends on Compose + `core/designsystem` only; never on `core/database`):
   - `TabExtension`: `id: String`, `icon`, label, `order: Int`, `@Composable Content(modifier)`. Rendered in the explorer slot.
   - `SettingsSection`: `id`, `order`, `@Composable title()`, `@Composable Content()`.
   - `BuiltInTabOverride`: target built-in tab, `@Composable Content(modifier, default: @Composable (Modifier) -> Unit)`. The override may render `default` to keep the built-in behavior.
2. **DI**: an `@Multibinds` module declaring `Set<TabExtension>`, `Set<SettingsSection>`, `Set<BuiltInTabOverride>` so that the sets are empty when no module contributes.
3. **Tabs**: replace the enum-only selection with built-in + extension keys (e.g. `sealed interface TabKey { BuiltIn(MediaTab); Extension(id) }`). `TabLayer` renders the extension tabs after the built-in ones, ordered by `order`. A restored selection whose extension id is absent falls back to FILES. `ClipApp` applies a `BuiltInTabOverride` for a built-in tab when one exists.
4. **Settings screen**: wire the Settings rail item to a new screen that shows the public sections, then `Set<SettingsSection>` ordered by `order`.
   - Public dummy section: a clicker whose label toggles `hi` → `bye` → `hi` … on every click.
5. **Catalog**: add `androidx-datastore-preferences` to `libs.versions.toml` so that modules can keep settings in their own DataStore files.
6. **Optional module include**: in `settings.gradle.kts`, read `extensionModules.dir` from `local.properties`. When it is set, include every direct subdirectory that has a `build.gradle.kts` as `:ext:<dirName>` with `projectDir` pointed there. `app` depends on each included `:ext:*` project through `findProject`/`subprojects` lookup, not typesafe accessors.
   - Absent property: the build is identical to today.

## Open Questions

- Should the clicker's `hi`/`bye` state survive restart (DataStore) or only configuration changes (`rememberSaveable`)?
- Does selecting an item inside an extension tab or an override drive the existing viewer pane? That needs a callback in the extension API (e.g. open image URI / open path). The explorer-only API is the minimum.
- If two extensions override the same built-in tab: first by `order`, or a build error?
- ADR proposal pending: "compile-time extension modules + Hilt multibinding instead of runtime plugins". Checklist items all hold (costly to reverse; runtime plugin forms were considered and rejected for security, Play-policy, and cross-process UI cost; reason is not recoverable from code). Not written; awaiting user decision.

## Spec

none — no workflow spec was created. The user requested this handoff instead of starting work in this session.

## Execution Ledger

- Findings: none.
- Evidence / mutation outcomes: none.
- Correction batches: 0. Verifier invocations: 0.
