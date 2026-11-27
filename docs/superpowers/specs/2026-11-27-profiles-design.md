# Profiles tab

Stage 2 of "Build tools, profiles, and profile terminals" in [roadmap.md](../../../roadmap.md). Branch: `profiles`. Builds on stage 1 (build tools), merged in PR #11.

## Why

The user's projects are developed and tested across many legacy versions, each needing its own JDK plus a specific Maven, Ant, or Gradle, and the work moves between them day to day. Stage 1 lets each tool be switched on its own tab, but a context change still means several switches on several tabs, and nothing remembers which versions belong together. Profiles save those combinations and switch all of them at once.

## Goals

- A top-level **Profiles** tab, placed after Gradle and before Activity, where the user lists, creates, edits, deletes, and **activates** profiles.
- A profile is a name, one JDK, and optionally one version each of Maven, Ant, and Gradle.
- Two ways to create one: **New** (an empty form) and **Save current setup** (the same form, filled from what is on the user PATH now).
- Activating applies the whole profile with one preview, one confirmation, and one registry write.
- A profile whose JDK or tool version isn't installed says which ones are missing and offers to install them, then activates.

## Non-goals (this stage)

- The always-visible profile switcher in the top bar. It is the next step, once the tab works. The model and "which profile is active" logic built here must serve it unchanged.
- Profile terminals (stage 3).
- Sharing profiles between machines or people (export/import).
- Choosing trusted certificates per profile. Certificates stay global.
- Machine-scope (system) environment writes.

## Anti-goals

- Activating a profile that leaves a tool from the previous context on PATH. Tools a profile leaves out come off PATH.
- A partial activation. If a required install fails, nothing is written.
- Writing the environment from a stale PATH. Every write goes through `UserPathWriter`, which reads the user Path at write time.

## Approaches

1. **Profiles store identities and resolve folders when applied (chosen by the user).** A slot records what it is, such as "JDK temurin major 8" or "Maven 3.3.9", and the folder is worked out on apply. Only identities make "missing → offer to install" possible, because the app knows what to download. Profiles also keep working if the install root changes.
2. **Profiles store folder paths.** Simpler, but a missing folder can only be reported, not reinstalled.

Creation: both **New** and **Save current setup** (chosen by the user).

## Design

### Model and state

- `record Profile(String name, JdkRef jdk, String maven, String ant, String gradle)` in `model`, with `record JdkRef(String vendorSlug, int major)`.
  - A null tool version means "not in this profile". The JDK is required.
- Profiles are stored as a list in `%LOCALAPPDATA%\JdkTrustManager\profiles.json`, using the same atomic write as the other state files.
- Names are trimmed, non-empty, and unique ignoring case. Saving a duplicate name is refused, with a message.
- Resolving a profile against `root`:
  - The JDK folder is `Layout.destination(root, vendorSlug, major)`.
  - A tool folder is `ToolLayout.destination(root, tool, version)`.
  - A slot counts as installed when its folder holds a JDK home (`JdkScanner.isHome`) or that tool's launcher.
- Resolving is pure, as `ProfileResolver.resolve(Profile, Path root) -> Resolved`. `Resolved` holds each slot's folder and whether it is installed. The later drop-down uses the same resolver.

### Which profile is active

- A profile is active when the user environment matches it exactly. Matching means:
  - `JAVA_HOME` is the profile's JDK folder;
  - each tool in the profile has its first home variable set to that tool's folder;
  - each tool left out has no home variable set.
- This is computed from the registry on every refresh and never stored, so a one-off switch on another tab makes no profile active. The list then says "No profile matches the current setup".
- The matching is pure: `ProfileMatch.active(List<Profile>, Map<String, String> userVariables, Path root) -> Optional<String>`.

### Activating

- The plan is built by a new pure function, `EnvironmentPlan.forProfile(String currentPath, String root, String javaHome, Map<BuildTool, String> toolHomesOrNull)`:
  - It sets `JAVA_HOME`.
  - For each of Maven, Ant, and Gradle, it either sets the home variables and adds the PATH entry (home given) or deletes the variables and the entry (null).
  - The PATH is built with the same rules as today: `%JAVA_HOME%\bin` first, then the tool entries in Maven, Ant, Gradle order, then everything else unchanged.
  - "Remove other Java entries" is not applied.
- The write goes through `UserPathWriter.live().write(current -> EnvironmentPlan.forProfile(current, …))`.
- After the write, `installations.json` marks the JDK active and `tools.json` marks each tool's row active or inactive, as the per-tab switches do.
- The confirmation lists what will change, one line per slot (for example "JDK temurin 8 → JAVA_HOME", "Maven 3.3.9 → on PATH", "Gradle → off PATH"), plus the new PATH preview. It also shows a Java-range warning for any tool whose range (from `BuildTool.javaRange`) doesn't include the profile's JDK major.
- Activation runs on the WorkQueue.

### Missing versions

- When a slot isn't installed, Activate first shows "Not installed: Maven 3.3.9, Temurin 8. Install them and activate?" with **Install and activate** or Cancel.
- Installs are queued on the WorkQueue, followed by the activation job. The queue runs jobs in order.
  - **JDK:** the vendor's catalog distribution is found by matching `Slug.of(apiParameter, javafx, arch)` to `vendorSlug`. The latest GA package for that major is installed with the existing `InstallJob`. A slug the catalog doesn't know (an adopted vendor) can't be installed; the dialog says so and the profile can't be activated until it's installed by hand.
  - **Tools:** the release is looked up in `ToolCatalog` by version and installed with the stage 1 installer.
  - The activation job checks every slot again and writes nothing if any slot is still missing, for example because an install failed or was cancelled.

### Save current setup

- Reads the user `JAVA_HOME` and the tool home variables.
- The JDK slot is filled when `JAVA_HOME` is a managed installation (`installations.json`); its `vendorSlug` and `major` are used.
- A tool slot is filled when its home is a folder under `build-tools` (its version from `ToolLayout.versionOf`). Otherwise the slot is left empty, and the form says why (for example "MAVEN_HOME points outside build-tools; adopt it on the Maven tab to use it here").
- The form then opens for the user to name it and adjust.

### Screens

- **Profiles tab.** A top row of buttons: **New**, **Save current setup**, **Edit**, **Delete**, **Activate**, and a status label. Below it is the list of profiles.
  - Each row reads `Release 4.2   Temurin 8 · Maven 3.3.9 · Ant 1.9.16`.
  - The active one is marked `[active]`. A profile with missing slots is marked `[missing: Maven 3.3.9]`.
  - When none is active, the line above the list says "No profile matches the current setup".
  - Under the list, a details area shows the selected profile's folders and the PATH it would produce.
- **Profile form.** A dialog with:
  - a name field;
  - **JDK**: managed JDKs only, shown as "temurin 8 — C:\Java\temurin-8";
  - **Maven**, **Ant**, **Gradle**: "(none)" plus the installed versions;
  - a warning line when a tool's Java range excludes the chosen JDK.
  - Editing a profile whose slot isn't installed shows that slot as "3.3.9 (not installed)" and keeps it.
  - The form is not given unmanaged JDKs. It says "Adopt a JDK on Java → Installed to use it in a profile".
- **Delete** asks first. Deleting a profile never changes the environment.
- Layout uses `Spacing`, confirmations use `ConfirmDialog` with Cancel as the default, and blocking work stays off the FX thread.

## Tests (all automated)

- `Profile` and the store:
  - round-trip;
  - a missing file reads as empty;
  - a duplicate name (ignoring case) is refused;
  - a blank name is refused;
  - a missing JDK is refused.
- `ProfileResolver`:
  - folders for each slot;
  - installed and missing detection on temp folders;
  - an omitted tool resolves to "not in profile", not "missing".
- `ProfileMatch`:
  - an exact match is active;
  - an extra tool on PATH means no match;
  - a different JDK means no match;
  - two profiles that differ only in an omitted tool pick the right one.
- `EnvironmentPlan.forProfile`:
  - Java plus two tools, from a PATH that had a third tool, removes the third;
  - unrelated entries keep their order;
  - variables are set and deleted correctly;
  - activating twice is a no-op.
- Save current:
  - `JAVA_HOME` for a managed JDK fills the JDK slot;
  - a `MAVEN_HOME` outside build-tools leaves Maven empty, with the reason;
  - tool homes under build-tools fill their slots.
- Missing-install chaining:
  - the activation job writes nothing when a slot is still missing;
  - for this, the job's decision logic is a pure function tested without the registry.
- Confirmations: the activation prompt lists every slot change and the Java warning.
- `SmokeTest`: the top-level tabs are Java, Maven, Ant, Gradle, Profiles, Activity.

## Left to the builder

- Class names inside `ui` and the exact dialog layout.
- Whether the details area uses a list or a text block.
