# Profile switcher (short design)

Finishes stage 2 of the roadmap ("Profiles"). Branch: `profile-switcher`. Builds on the Profiles tab (PR #13).

## Why

The work moves between legacy and current projects from day to day. With the Profiles tab, switching a whole toolchain already takes one confirmed write, but you have to go to that tab first. The switcher puts the active profile in view on every tab and lets you switch from wherever you are.

## What

- **Where:** a drop-down in the top bar, to the right of **Options**, visible on every tab.
- **What it shows:** `Profile: <name>` for the profile that matches the current setup, or `Profile: custom` when none matches. The list holds every profile; a profile with missing versions is marked `(missing …)`.
- **Picking another profile** runs exactly the Profiles tab's activation, decided with the user:
  - It **always confirms**, showing the change list, Java warnings, unmanaged entries that stay on PATH, and the PATH preview.
  - When versions are missing, it asks to install them first.
- **Cancelling, or picking the active profile again,** changes nothing, and the drop-down goes back to the active profile.
- **Staying in step:**
  - The switcher and the Profiles tab show the same "active" state.
  - Both refresh after any activation from either place.
  - Both also refresh after every finished work-queue job, so per-tab switches move the drop-down to `custom`, and when the app window regains focus, to pick up changes made outside the app.
- **When profiles.json can't be read** (newer or malformed), the drop-down shows `Profile: unavailable` and is disabled. The tab explains why.

## Approach

There is only one sensible way. The activation flow moves out of `ProfilesPane` into a shared `ui/ProfileActivator`: resolve → offer missing installs → confirm → queue installs and the write. Both the tab and the switcher call it. Keeping two copies would let them drift, and the spec's safety rules (re-check before writing, one write through `UserPathWriter`) must hold in both places.

The "which profile is active / what does the list show" logic is pure (`profiles` package), so it's unit-tested:
- `SwitcherModel.items(...)` builds the labels;
- `SwitcherModel.selection(...)` gives the active profile, `custom`, or `unavailable`.

## Non-goals

- Switching without confirmation (the user chose always confirm).
- Keyboard shortcuts for profiles.
- Profile terminals (stage 3).

## Tests (automated)

- **`SwitcherModel` labels:** active name, `custom`, `unavailable`, and missing markers.
- **Selection:** follows the active match, ignoring case.
- **`ProfileActivator` decisions:** the existing activation tests still pass after the move. The pure parts (changes, warnings, leftovers, `readyToWrite`) stay where they are.
- **`SmokeTest`:** the top-level tabs are unchanged.

The UI check is the user's.
