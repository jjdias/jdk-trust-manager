# Profile Switcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a top-bar drop-down, to the right of Options, that shows the active profile (or `custom` / `unavailable`) and switches profiles through the same always-confirmed activation as the Profiles tab.

**Architecture:**
- The activation flow moves out of `ProfilesPane` into a shared `ui/ProfileActivator`, together with a `ui/ProfileState` snapshot reader, so the tab and the switcher can't drift.
- A pure `profiles/SwitcherModel` decides labels and selection.
- `ProfileActivator` keeps a listener list. Both views register; the list fires after an activation, after every finished work-queue job, and when the window regains focus.

**Tech Stack:** Java 21, JavaFX 21, JUnit 6. No new dependencies.

**Spec:** [docs/superpowers/specs/2026-11-28-profile-switcher-design.md](../specs/2026-11-28-profile-switcher-design.md)

## Global Constraints

- Only `ui` imports JavaFX. Blocking work (registry, disk, network) never runs on the FX thread.
- Every switch confirms, using `Confirmations.activateProfile` with changes, warnings, leftovers, and the preview. Missing versions go through `Confirmations.installMissing` first. Every write goes through `UserPathWriter` on the `WorkQueue`, and the activation job re-checks with `ProfileActivation.readyToWrite` before writing. That behavior is moved unchanged from `ProfilesPane`.
- Labels: `Profile: <name>`, `Profile: custom`, `Profile: unavailable`. A list row reads `<name>`, or `<name>   (missing <labels>)`.
- Cancelling, or choosing the already-active profile, changes nothing and puts the selection back.
- Tests run with `mvn -B -ntp -f app/pom.xml test [-Dtest=Class]`. Maven is launched outside the desktop app's package (WMI). All verification is automated; the user does the UI checks.

## Review Focus

- **The drop-down's own selection event during a programmatic refresh** must not start an activation. Only a user's pick does. That logic is in Task 3, and the pure part, "which pick starts an activation", is tested in Task 1 as `SwitcherModel.shouldActivate(picked, active)`.
- **A refresh while a confirmation dialog is open** must not change what the dialog is about, because the profile is captured before the dialog opens. That's covered by code review in Task 3.
- **Two profiles whose names differ only in case:** the selection follows the active one ignoring case. That's tested in Task 1.

---

### Task 1: Switcher labels and selection

**Files:**
- Create: `app/src/main/java/dev/jdktrustmanager/profiles/SwitcherModel.java`
- Test: `app/src/test/java/dev/jdktrustmanager/profiles/SwitcherModelTest.java`

**Interfaces:**
- Produces:
  - `record Item(String name, String label)`.
  - `SwitcherModel.items(List<Profile> profiles, Map<String, List<String>> missingByName) -> List<Item>`. Items keep the list order; `label` is the name, or `name + "   (missing " + join + ")"`.
  - `SwitcherModel.title(Optional<String> active, boolean unavailable) -> String`.
  - `SwitcherModel.selected(List<Item> items, Optional<String> active) -> Optional<Item>`, matching names ignoring case.
  - `SwitcherModel.shouldActivate(Item picked, Optional<String> active) -> boolean`. It's false for a null pick or when the pick is the active profile (ignoring case).

- [ ] **Step 1: Write the failing tests:**
  - labels with and without missing versions;
  - the three titles;
  - selection ignoring case;
  - `shouldActivate` is false for null, false for the active profile in another case, and true for another profile.
- [ ] **Step 2: Run `-Dtest=SwitcherModelTest`.** Expected: compile failure.
- [ ] **Step 3: Implement.** Use `ProfileActivation.join` for the missing labels.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** with `git commit -m "Decide what the profile switcher shows"`.

---

### Task 2: Shared activation

**Files:**
- Create: `app/src/main/java/dev/jdktrustmanager/ui/ProfileState.java`, `ui/ProfileActivator.java`
- Modify: `app/src/main/java/dev/jdktrustmanager/ui/ProfilesPane.java`, which loses `read()`, `activate`, `confirmAndActivate`, `activationJob`, `jdkInstall`, `toolInstall`, and `plan` to the new classes

**Interfaces:**
- Produces:
  - `record ProfileState(List<Profile> profiles, String error, Optional<String> active, Map<String, List<String>> missing, List<Installation> managed, Map<Tool, List<String>> installed, Map<String, String> userVariables, String userPath)`, with `static ProfileState read(ToolContext context, ProfileStore store)` (moved from `ProfilesPane.read`) and `static ProfileState empty()`.
  - `ProfileActivator(ToolContext context, ProfileStore store, CatalogBrowser browser, DiscoClient client)`, with:
    - `void activate(Node owner, Profile profile, String userPath, Consumer<String> status)`, the moved flow, which calls `changed()` after the activation job and after every install job;
    - `EnvironmentPlan plan(Profile, ProfileResolver.Resolved, String currentPath)`;
    - `void onChange(Runnable listener)`;
    - `void changed()`, which runs the listeners on the FX thread.
  - `ProfilesPane(ToolContext, ProfileStore, ProfileActivator)` registers `this::refresh` with `onChange`. Its own Save, Edit, and Delete also call `activator.changed()`.

- [ ] **Step 1: Move the code** without changing behavior.
- [ ] **Step 2: Run the whole suite.** Expected: PASS. The activation logic's tests (`ProfileActivationTest`, `ConfirmationsTest`, `EnvironmentPlanTest`, `UserPathWriterTest`) are unchanged.
- [ ] **Step 3: Commit** with `git commit -m "Share profile activation between views"`.

---

### Task 3: The drop-down and staying in step

**Files:**
- Create: `app/src/main/java/dev/jdktrustmanager/ui/ProfileSwitcher.java`
- Modify: `ui/AppTabs.java` (build one `ProfileActivator` and the switcher; add `ProfileSwitcher switcher()` and `void refreshProfiles()`), `JdkTrustManagerApp.java` (add the switcher to the top bar after Options; call `refreshProfiles()` when `stage.focusedProperty()` becomes true)

**Interfaces:**
- Consumes: `SwitcherModel`, `ProfileState`, `ProfileActivator`.
- Produces: `ProfileSwitcher(ProfileActivator activator, ToolContext context, ProfileStore store)`, an `HBox` holding a `ComboBox<SwitcherModel.Item>`, with `void refresh()`.

Behavior:
- **`refresh()`:**
  - It reads `ProfileState` on a worker thread, then on the FX thread sets the items and the button-cell text `SwitcherModel.title(...)`, and selects `SwitcherModel.selected(...)`.
  - A `boolean updating` flag is set while doing so, so the value listener ignores these changes (Review Focus).
  - When `error != null`, the combo is disabled and titled `Profile: unavailable`.
- **A user's pick:**
  - When `updating` is false and `SwitcherModel.shouldActivate(picked, active)` is true, it captures the `Profile` by name from the last state, calls `activator.activate(this, profile, state.userPath(), status -> {})`, and then calls `refresh()` so a cancelled pick snaps back.
  - Otherwise it just calls `refresh()`.
- **Registration:** the switcher registers `this::refresh` with `activator.onChange`.
- **Queue jobs:** `AppTabs` subscribes to the `WorkQueue` and calls `activator.changed()` on every `DONE` or `FAILED` event.

- [ ] **Step 1: Implement** the switcher and the wiring.
- [ ] **Step 2: Run the whole suite.** Expected: PASS, and `SmokeTest` is unchanged. Don't launch the app; the UI check is the user's.
- [ ] **Step 3: Commit** with `git commit -m "Add the profile switcher to the top bar"`.

---

### Task 4: Docs

**Files:** `README.md` (Profiles block: the top-bar switcher), `roadmap.md` (stage 2 done; stage 3 next), `.agent/project.md` (the shared `ProfileActivator`)

- [ ] **Step 1: Edit**, then grep the tracked files for local usernames and `C:\Users\` paths. Only the test fixture and the plans' own text may match.
- [ ] **Step 2: Run the whole suite.** Expected: PASS.
- [ ] **Step 3: Commit** with `git commit -m "Document the profile switcher"`.
