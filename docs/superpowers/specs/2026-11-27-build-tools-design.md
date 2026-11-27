# Build tools: Maven, Ant, and Gradle tabs

Stage 1 of "Build tools, profiles, and profile terminals" in [roadmap.md](../../../roadmap.md). Branch: `build-tools`.

## Why

The user's projects are developed and tested across many legacy versions. Each version needs its own JDK plus a specific Maven, Ant, or Gradle, and the work moves between them from one day to the next. Today the app installs and switches JDKs only; the build tools are managed by hand. This stage makes Maven, Ant, and Gradle as easy to install side by side and switch as JDKs are. Stage 2 (a Profiles tab that switches a whole combination at once) builds on it, so the model and environment code written here must be reusable by profiles.

## Goals

- A top-level **Java** tab containing today's six screens unchanged, and next to it **Maven**, **Ant**, and **Gradle** tabs.
- Each tool tab lists published versions, installs any of them, keeps any number of versions side by side, and switches which one is on `PATH`.
- Legacy versions are first-class: Maven back to 2.0.9, Ant back to 1.5.x, Gradle back to 0.7, all verified before they are used.
- Switching a tool never disturbs `JAVA_HOME`, the Java `PATH` entry, or another tool. Switching the JDK never disturbs the tools.

## Non-goals

- Profiles and profile terminals (stages 2 and 3).
- Maven Wrapper and Gradle Wrapper downloads; projects using `mvnw` or `gradlew` keep fetching their own version.
- System-scope (machine) environment writes. User scope only, like the JDK switcher today.
- Editing `settings.xml`, `gradle.properties`, or proxies for the tools.
- Certificate screens for the tools. They run on the active JDK and use its `cacerts`.

## Anti-goals (a failure even if it works)

- A JDK switch that silently drops a tool from `PATH`, or a tool switch that changes `JAVA_HOME`.
- Installing a new version over an existing one, or "upgrading" a version in place. Every version is its own folder.
- A download used without any verification, or a verification downgrade that happens silently.

## Approaches

1. **One shared set of screens driven by a per-tool description (chosen).** A `BuildTool` description holds everything that differs between Maven, Ant, and Gradle: version-list source and parser, download and checksum URLs, signature and KEYS URLs, folder prefix, launcher file names, environment variable names, how to read a version from an unpacked home, and minimum Java. Three generic screens (Versions, Installed, Environment) take a description. Adding a fourth tool later is one more description.
2. **Three separate sets of screens.** Each tool gets its own panes. Simple to start, but three copies to keep in step, and stage 2 needs one uniform model anyway.
3. **Fold tools into the JDK model** (`Installation` with a "kind"). Reuses existing code, but JDK rules (one folder per major, patch replaces folder, `release` file, `cacerts`, Foojay) don't fit tools, and every JDK code path would need "unless it's a tool" checks.

The user handed over the how; approach 1 was presented and not objected to.

## Decided with the user

- Three top-level tabs (Maven, Ant, Gradle), not one combined tab. Existing screens nest under a Java tab.
- Verification of Apache releases (Maven, Ant): **check the PGP signature for every release; reject a bad signature; fall back to the strongest published checksum, with a warning, only when the signing keys can't be reached.**

## Design

### Model and state

`ToolInstallation(tool, version, path, source, verification, installedAt, active, pinned, notes)` in `model`, stored in `%LOCALAPPDATA%\JdkTrustManager\tools.json` (separate from `installations.json`, so JDK code is untouched).

- `tool`: `MAVEN`, `ANT`, `GRADLE`.
- `source`: `DOWNLOADED` or `ADOPTED`.
- `verification`: `SIGNATURE`, `SHA512`, `SHA256`, `SHA1`, `MD5`, `NONE` (adopted only). Shown in the Installed list; `SHA1`/`MD5` show as "weak checksum".

### Sources and version lists

| Tool | Version list | Zip | Checksums | Signature + keys |
|---|---|---|---|---|
| Maven | `https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/maven-metadata.xml` | `…/apache-maven/<v>/apache-maven-<v>-bin.zip` | `.sha512` (3.9+ on Central), `.sha1`, `.md5` | `.asc` next to the zip; keys `https://downloads.apache.org/maven/KEYS` |
| Ant | directory listing `https://archive.apache.org/dist/ant/binaries/` (names `apache-ant-<v>-bin.zip`) | same folder | `.sha512` (1.8+), `.sha1`, `.md5` | `.asc`; keys `https://downloads.apache.org/ant/KEYS` |
| Gradle | `https://services.gradle.org/versions/all` (JSON) | `downloadUrl` from the JSON (`gradle-<v>-bin.zip`) | `checksum` (SHA-256) from the JSON | not checked (see below) |

- Maven 3.6.x lacks `.sha512` on Central but has it on `archive.apache.org/dist/maven/maven-3/<v>/binaries/`; the builder may use the archive for the checksum when Central has none.
- Pre-releases are hidden unless the new setting **Show build tool pre-releases** is on: Maven/Ant versions containing `alpha`, `beta`, `rc`, `-M<n>`; Gradle entries with `snapshot`, `nightly`, `releaseNightly`, `activeRc`, non-empty `rcFor` or `milestoneFor`. Gradle entries with `broken: true` are never shown.
- Version lists are cached in the state folder and reused within the existing "catalog cache hours" setting, falling back to the cache when offline, as the JDK catalog does.
- Versions are sorted newest first by numeric comparison (reuse `Installations.newer`-style comparison; it must handle `3.0-alpha-2`, `4.0.0-rc-7`, `1.10.15`, `0.9-rc-1`).

### Verification

For Maven and Ant, after the zip is downloaded:

1. Download `<zip>.asc` and the tool's `KEYS` file.
2. Verify the detached signature over the zip with Bouncy Castle (`bcpg-jdk18on` + `bcprov-jdk18on`, current 1.8x release). The signing key must be in `KEYS`.
3. Outcomes:
   - **Valid signature from a key in KEYS:** use it. Also check the strongest published checksum if present (cheap; catches a corrupt mirror). Record `SIGNATURE`.
   - **Signature present but invalid, or signed by a key not in KEYS:** reject. Delete the zip. Show "Signature check failed" with the signer key ID. No fallback.
   - **KEYS or `.asc` can't be fetched** (network error, timeout, HTTP 5xx, or 404): fall back to the strongest published checksum, SHA-512 > SHA-1 > MD5. If none matches, reject. Record what was used. When it's `SHA1` or `MD5`, the install still completes (a warning, not a block, as decided with the user): the job status and log say "Verified with SHA-1 only: Apache's signing keys couldn't be reached", and the Installed row shows "weak checksum" (my call on where the warning appears; the keys are only known to be unreachable after the download starts).
4. **KEYS cache (my call):** the last successfully downloaded `KEYS` per tool is kept in the state folder and used when the live one can't be fetched. This stops a network that blocks `downloads.apache.org` from forcing the weak path once the app has seen the keys once. The fallback only happens when there's no live and no cached KEYS.

For Gradle (my call): the SHA-256 from `versions/all`, fetched over HTTPS, must match. Gradle doesn't publish an Apache-style KEYS file next to its releases, and SHA-256 is already strong, so its `.asc` is not checked in this stage. Record `SHA256`.

A zip that fails any check is deleted. Download cancel, free-space check, and the zip cache from the JDK installer are reused.

### Layout and install

- Folder per exact version: `<root>\build-tools\maven-<v>\`, `ant-<v>\`, `gradle-<v>\` (root is today's install root, default `C:\Java`).
- Unzip with the existing safe unzipper into a temp folder under `build-tools`, find the single top-level home, check the launcher exists (`bin\mvn.cmd` or `bin\mvn.bat` for Maven before 3.3; `bin\ant.bat`; `bin\gradle.bat`), then move into place. If the target folder exists, the install refuses ("already installed").
- Run check (my call): after placing, run `<home>\bin\<launcher> --version` (`-v` for Maven, `-version` for Ant) with `JAVA_HOME` set to the active JDK if it meets the tool's minimum Java, otherwise the newest managed JDK that does. If no suitable JDK exists, skip the run check and say so in the log. A failed run check rolls back the install.
- The JDK scanner skips `build-tools` the same way it skips `trusted-certs`. `Layout.checkInsideRoot` refuses the `build-tools` folder itself as a JDK target.

### Environment and PATH

Variables (user scope, written to `HKCU\Environment` like `JAVA_HOME`, then the settings-changed broadcast):

- Maven: `MAVEN_HOME` and `M2_HOME` (Maven 2 and early Maven 3 read `M2_HOME`); `PATH` entry `%MAVEN_HOME%\bin`.
- Ant: `ANT_HOME`; `PATH` entry `%ANT_HOME%\bin`.
- Gradle: `GRADLE_HOME`; `PATH` entry `%GRADLE_HOME%\bin`.

`PathEditor` changes (required; today it breaks tools):

- The managed block at the front of `PATH` is, in order, `%JAVA_HOME%\bin`, then whichever of `%MAVEN_HOME%\bin`, `%ANT_HOME%\bin`, `%GRADLE_HOME%\bin` are active.
- A JDK switch keeps the tool entries. Today `isManaged` drops every `<root>\…\bin` and `isOtherJava` drops anything containing `\java\`; both must leave `%X_HOME%\bin` and anything under `<root>\build-tools\` alone.
- A tool switch adds or keeps only its own entry and never touches `JAVA_HOME` or the Java entry. The entry goes right after `%JAVA_HOME%\bin` when that is present, otherwise at the front, keeping the Maven, Ant, Gradle order among tool entries (my call).
- Each tool's Environment screen has **Remove from PATH**: deletes its `PATH` entry and its variables.
- Each tool's Environment screen lists other entries that provide that tool (another `apache-maven\bin`, Chocolatey or Scoop shims, an older `MAVEN_HOME`) and offers "Remove other Maven entries", previewed, like the JDK screen's checkbox.

The environment write code is split so stage 2 can write a JDK and several tools in one preview and one registry pass: a pure function `EnvironmentPlan(current user variables, desired homes) -> (variables to set, variables to delete, new PATH)`, and one writer that applies a plan. The existing JDK switcher moves onto it.

### Minimum Java

Each tool version maps to a minimum Java (and for Gradle, a maximum Java it can run on). The table lives in code, sourced from each project's documentation, and the builder verifies the values: Maven 3.9 needs 8, Maven 4 needs 17, Ant 1.10 needs 8, Gradle 5–8 need 8, Gradle 9 needs 17; older lines need older minimums. Set to path and Install show a warning, not a block, when the active JDK is outside the range.

### Screens

Top level: `TabPane` with **Java** (today's `MainView` nested unchanged), **Maven**, **Ant**, **Gradle**. The Options button and the progress bar stay app-wide. Activity stays inside Java (the user asked for all current tabs to move there); tool jobs still log to it.

Each tool tab has sub-tabs:

- **Versions**: filter box; list of versions newest first with "installed" marked; Install (above the list, as in the JDK catalog). Install confirmation names the version, the folder, and how it will be verified.
- **Installed**: installed versions with `[on PATH]`, `[pinned]`, and verification (`signature`, `SHA-512`, `SHA-256`, `weak checksum`, `adopted`). Buttons: Refresh, Set to path, Adopt, Pin/Unpin, Remove. Remove is refused for the version on `PATH` and for pinned versions. Set to path and Remove ask first.
- **Environment**: current variables and `PATH` entries for this tool, preview, Set, Remove from PATH, remove-other-entries checkbox, and a per-shell PowerShell snippet (`$env:MAVEN_HOME=…; $env:Path="…\bin;$env:Path"`).

Spacing, confirmations (`ConfirmDialog`, Cancel default), and the work queue are the same as the Java tab.

### Adopt

Scan for existing installs: the current `MAVEN_HOME`/`M2_HOME`/`ANT_HOME`/`GRADLE_HOME`; `PATH` entries containing the launcher; `C:\Program Files\Apache\*`, `C:\Program Files\apache-maven-*`, `C:\apache-*`, `C:\tools\*`; Chocolatey `C:\ProgramData\chocolatey\lib\{maven,ant,gradle}\tools\*`; Scoop `%USERPROFILE%\scoop\apps\{maven,ant,gradle}\<v>`. Version is read from the home (Maven `lib\maven-core-<v>.jar`, Ant `lib\ant.jar` manifest `Implementation-Version`, Gradle `lib\gradle-core-api-<v>.jar` or `gradle-<v>` launcher jar). Adopt copies into `build-tools\<tool>-<v>\` and leaves the original; refuses if that folder exists. Verification is recorded as `NONE`.

### Packaging

Bouncy Castle jars ship on the app class path like Jackson (no `jlink` module change). Add Bouncy Castle (MIT-style licence) to `THIRD-PARTY-NOTICES.txt`. Expect the zip to grow by about 8 MB.

## Tests

- Parsers on saved fixtures: Maven `maven-metadata.xml`, the Ant directory listing HTML, Gradle `versions/all` (with snapshot, nightly, rc, milestone, and broken entries that must be dropped or hidden).
- Version ordering across alpha/beta/rc/milestone and three-part numbers.
- Signature: a key pair and signature generated in the test with Bouncy Castle; valid accepted; tampered zip rejected; signer not in KEYS rejected; KEYS unreachable with no cache falls back to SHA-1 and records `SHA1`; KEYS unreachable with a cache verifies by signature.
- `PathEditor`/`EnvironmentPlan`: JDK switch keeps `%MAVEN_HOME%\bin`; Maven switch keeps Java and Ant; Remove from PATH deletes only its entry and variables; remove-others drops Chocolatey Maven but not Ant; nothing under `<root>\build-tools` is dropped by the JDK cleanup.
- Layout: refuses to install over an existing version folder; refuses paths outside the root; JDK scanner ignores `build-tools`.
- Adopt: reads the version from fixture homes for each tool.

## Left to the builder

- Exact class and package names (a `buildtools` package beside `catalog`/`install` is the expected shape).
- HTML parsing approach for the Ant listing.
- Whether Versions shows release dates (Gradle JSON has `buildTime`; Maven metadata does not).
- The minimum/maximum Java table values, checked against official docs.
