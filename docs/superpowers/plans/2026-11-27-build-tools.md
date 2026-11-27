# Build Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Maven, Ant, and Gradle tabs beside a new Java tab, each able to list published versions, install any number side by side with verified downloads, and switch which one is on `PATH` without disturbing Java or the other tools.

**Architecture:** A JavaFX-free `dev.jdktrustmanager.buildtools` package holds one `BuildTool` description per tool plus the shared catalog, verifier, installer, and adopter. `env` gains an `EnvironmentPlan` that both the JDK switcher and the tool switchers use (stage 2 profiles will reuse it). Three generic `ui` panes take a `BuildTool` and serve all three tabs.

**Tech Stack:** Java 21, JavaFX 21, Maven, Jackson 2.23, JUnit 6, Bouncy Castle `bcpg-jdk18on` + `bcprov-jdk18on` 1.86.

**Spec:** [docs/superpowers/specs/2026-11-27-build-tools-design.md](../specs/2026-11-27-build-tools-design.md)

## Global Constraints

- Only `dev.jdktrustmanager.ui` may import JavaFX.
- Every network URL is `https://`; the HTTP client follows redirects (Gradle's `services.gradle.org` → `downloads.gradle.org`), uses `ProxySelector.getDefault()`, and sends `User-Agent: JdkTrustManager`.
- Install root is `Settings.rootDirectory()` (default `C:\Java`); tools live in `<root>\build-tools\<prefix>-<version>\` with prefixes `maven`, `ant`, `gradle`. A version folder is never overwritten.
- Registry writes go to `HKCU\Environment` through `reg add`/`reg delete` and end with the existing settings-changed broadcast. Never `setx`.
- Variables: Maven `MAVEN_HOME` + `M2_HOME`, PATH `%MAVEN_HOME%\bin`; Ant `ANT_HOME`, PATH `%ANT_HOME%\bin`; Gradle `GRADLE_HOME`, PATH `%GRADLE_HOME%\bin`.
- Managed PATH block order: `%JAVA_HOME%\bin`, `%MAVEN_HOME%\bin`, `%ANT_HOME%\bin`, `%GRADLE_HOME%\bin` (only the ones present).
- Apache verification rule: valid signature by a key in KEYS → `SIGNATURE`; invalid signature or unknown signer → reject and delete the zip; KEYS (live and cached) or `.asc` unavailable → strongest checksum SHA-512 > SHA-1 > MD5, warning when SHA-1/MD5. Gradle → SHA-256 from `versions/all`.
- Every action that installs, removes, or writes the environment goes through `ConfirmDialog` (Cancel default) and the `WorkQueue`; panes use `Spacing.page` / `Spacing.GAP`.
- New state files: `%LOCALAPPDATA%\JdkTrustManager\tools.json`, `tool-catalog.json`, `keys\<tool>-KEYS.asc`.
- Tests run with `mvn -B -ntp -f app/pom.xml test` (single class: add `-Dtest=<Class>`). In the Claude desktop app, launch Maven outside the app package (WMI `Win32_Process Create`) or JDK sockets fail with "Unable to establish loopback connection".

## Review Focus

- Old Apache releases signed by keys that have since **expired**: a valid signature from an expired key that is in KEYS must be accepted (expiry is not revocation). Test in Task 6.
- Signatures made by a **subkey**: lookup must search subkeys in KEYS, not only primary keys. Test in Task 6.
- A **leftover `.unpack-*` or `.adopt-*` folder** in `build-tools` after a crash must not appear as an installed version or block a later install. Test in Task 8.
- A **legacy PATH entry** such as `%M2_HOME%\bin` or `C:\apache-maven-3.3.9\bin` counts as "another Maven" for remove-others, but is never removed unless the user ticks it. Test in Task 9.
- **Offline with a cache**: the Versions list shows the cached list marked stale rather than an error; offline with no cache shows the error text. Test in Task 5.

---

### Task 1: Tool model and state files

**Files:**
- Create: `app/src/main/java/dev/jdktrustmanager/model/Tool.java`, `Verification.java`, `ToolSource.java`, `ToolInstallation.java`, `ToolInstallations.java`
- Modify: `app/src/main/java/dev/jdktrustmanager/persist/JsonStores.java`, `persist/AppFiles.java`
- Test: `app/src/test/java/dev/jdktrustmanager/model/ToolInstallationsTest.java`, `persist/JsonStoresTest.java`

**Interfaces:**
- Produces: `enum Tool { MAVEN, ANT, GRADLE }`; `enum Verification { SIGNATURE, SHA512, SHA256, SHA1, MD5, NONE; boolean weak() }` (true for SHA1, MD5); `enum ToolSource { DOWNLOADED, ADOPTED }`
- Produces: `record ToolInstallation(Tool tool, String version, String path, ToolSource source, Verification verification, Instant installedAt, boolean active, boolean pinned, String notes)`
- Produces: `ToolInstallations.markActive(List<ToolInstallation>, Tool, String pathOrNull) -> List<ToolInstallation>` (only rows of that tool change; `null` clears), `withPinned(List, String path, boolean) -> List`, `of(List, Tool) -> List` (that tool's rows, newest version first via `ToolVersions.compare` from Task 2 — until Task 2 lands, insertion order).
- Produces: `JsonStores.readTools(Path) -> List<ToolInstallation>` (missing file → empty list), `writeTools(Path, List<ToolInstallation>)`; `AppFiles.tools()`, `AppFiles.toolCatalog()`, `AppFiles.keysDir()`.

- [ ] **Step 1: Write the failing tests**

```java
@Test void markActiveTouchesOnlyThatTool() {
    var rows = List.of(row(Tool.MAVEN, "3.9.9", true), row(Tool.MAVEN, "3.3.9", false), row(Tool.ANT, "1.10.15", true));
    var next = ToolInstallations.markActive(rows, Tool.MAVEN, "C:\\Java\\build-tools\\maven-3.3.9");
    assertEquals(List.of(false, true, true), next.stream().map(ToolInstallation::active).toList());
}
@Test void markActiveNullClearsThatToolOnly() { /* MAVEN rows all false, ANT row stays true */ }
@Test void weakIsSha1AndMd5Only() { assertTrue(Verification.SHA1.weak()); assertTrue(Verification.MD5.weak()); assertFalse(Verification.SHA256.weak()); }
// JsonStoresTest
@Test void toolsRoundTripAndMissingFileIsEmpty() { /* write two rows, read equal list; readTools(nonexistent) is empty */ }
```

`row(tool, version, active)` builds a path `C:\Java\build-tools\<prefix>-<version>`.

- [ ] **Step 2: Run `-Dtest=ToolInstallationsTest,JsonStoresTest`** — Expected: compile failure.
- [ ] **Step 3: Implement the types, helpers, and store methods.** Path matching reuses `Installations.samePath`.
- [ ] **Step 4: Run the same tests** — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Add build tool model and tools.json"`

---

### Task 2: Version ordering and pre-release detection

**Files:**
- Create: `app/src/main/java/dev/jdktrustmanager/buildtools/ToolVersions.java`
- Test: `app/src/test/java/dev/jdktrustmanager/buildtools/ToolVersionsTest.java`

**Interfaces:**
- Produces: `ToolVersions.compare(String a, String b) -> int` (numeric parts compared numerically; a release sorts after its own pre-releases: `3.0-alpha-2 < 3.0-beta-1 < 3.0`, `4.0.0-rc-7 < 4.0.0`, `0.9-rc-1 < 0.9`, `1.10.15 > 1.9.16`, `2.1.0-M1 < 2.1.0`); `ToolVersions.NEWEST_FIRST` comparator; `ToolVersions.isPreRelease(String) -> boolean` (contains, case-insensitive, `alpha`, `beta`, `rc`, `milestone`, or matches `-M\d+`).

- [ ] **Step 1: Write the failing tests** with exactly the orderings above, plus `isPreRelease("4.0.0-rc-7")`, `isPreRelease("2.1.0-M1")` true and `isPreRelease("3.9.16")`, `isPreRelease("1.10.15")` false, and sorting the Maven list `3.3.9, 3.10.0, 3.9.16, 4.0.0-rc-7, 2.2.1` NEWEST_FIRST to `4.0.0-rc-7, 3.10.0, 3.9.16, 3.3.9, 2.2.1`. (The rc is newest first because the list is sorted first and filtered later.)
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement.** Split on `.`/`-`; numeric tokens compare numerically; a qualifier token ranks `alpha < beta < milestone/M < rc < (none)`.
- [ ] **Step 4: Run** — Expected: PASS. Then switch `ToolInstallations.of` to `NEWEST_FIRST`.
- [ ] **Step 5: Commit** `git commit -m "Order build tool versions"`

---

### Task 3: Version-list parsers

**Files:**
- Create: `app/src/main/java/dev/jdktrustmanager/buildtools/ToolRelease.java`, `MavenMetadataParser.java`, `AntListingParser.java`, `GradleVersionsParser.java`
- Create fixtures: `app/src/test/resources/buildtools/maven-metadata.xml`, `ant-binaries.html`, `gradle-versions.json` (trimmed copies of the live responses; the Gradle one must include one each of `snapshot:true`, `nightly:true`, `releaseNightly:true`, `activeRc:true`, non-empty `rcFor`, non-empty `milestoneFor`, `broken:true`, and two normal releases)
- Test: `app/src/test/java/dev/jdktrustmanager/buildtools/ParsersTest.java`

**Interfaces:**
- Produces: `record ToolRelease(Tool tool, String version, String zipUrl, String sha256, boolean preRelease)` — `sha256` is non-null only for Gradle.
- Produces: `MavenMetadataParser.versions(String xml) -> List<String>`; `AntListingParser.versions(String html) -> List<String>` (from hrefs `apache-ant-<v>-bin.zip`, ignoring `.asc`/`.md5`/`.sha*` and `-src`, deduplicated); `GradleVersionsParser.releases(String json) -> List<ToolRelease>` (drops `snapshot`, `nightly`, `releaseNightly`, `broken`; marks `activeRc`, non-empty `rcFor`/`milestoneFor`, or `ToolVersions.isPreRelease` as preRelease; uses `downloadUrl` and `checksum`).

- [ ] **Step 1: Write the failing tests.** Maven fixture → contains `2.0.9`, `3.3.9`, `4.0.0-rc-7`; Ant fixture → contains `1.5.2`, `1.6.5`, `1.10.15`, no duplicates, none ending in `.asc`; Gradle fixture → no snapshot/nightly/broken versions, the rc and milestone entries have `preRelease == true`, a normal entry has the fixture's `downloadUrl` and 64-hex `sha256`.
- [ ] **Step 2: Run `-Dtest=ParsersTest`** — Expected: FAIL.
- [ ] **Step 3: Implement.** XML via `javax.xml.parsers` with external entities disabled; HTML via a regex over `href="apache-ant-([0-9][^"]*)-bin\.zip"`; JSON via Jackson.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Parse Maven, Ant, and Gradle version lists"`

---

### Task 4: Tool descriptions

**Files:**
- Create: `app/src/main/java/dev/jdktrustmanager/buildtools/BuildTool.java`, `BuildTools.java`, `JavaRange.java`
- Test: `app/src/test/java/dev/jdktrustmanager/buildtools/BuildToolsTest.java`

**Interfaces:**
- Consumes: `Tool`, `ToolRelease`, the three parsers.
- Produces: `interface BuildTool` with `Tool tool()`, `String displayName()`, `String folderPrefix()`, `String versionListUrl()`, `List<ToolRelease> parse(String body)`, `String zipUrl(String version)`, `List<Checksum> checksumUrls(String version)` (strongest first), `Optional<String> signatureUrl(String version)`, `Optional<String> keysUrl()`, `List<String> launchers()` (first existing one is used), `List<String> homeVariables()` (first is the one `PATH` references), `String pathEntry()`, `JavaRange javaRange(String version)`.
- Produces: `record Checksum(Verification kind, String url)`; `record JavaRange(int min, Integer max)` with `boolean allows(int javaMajor)` and `String describe()` (e.g. `"Java 8 or newer"`, `"Java 8 to 21"`).
- Produces: `BuildTools.MAVEN`, `BuildTools.ANT`, `BuildTools.GRADLE`, `BuildTools.ALL`, `BuildTools.of(Tool)`.

Values:
- Maven: list URL and zip/checksum/`.asc` under `https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/<v>/apache-maven-<v>-bin.zip`; checksums `.sha512`, `.sha1`, `.md5`; plus, for 3.6.x only, `https://archive.apache.org/dist/maven/maven-3/<v>/binaries/apache-maven-<v>-bin.zip.sha512` as the first SHA-512 candidate; keys `https://downloads.apache.org/maven/KEYS`; launchers `bin\mvn.cmd`, `bin\mvn.bat`; variables `MAVEN_HOME`, `M2_HOME`; entry `%MAVEN_HOME%\bin`.
- Ant: list `https://archive.apache.org/dist/ant/binaries/`; zip `…/apache-ant-<v>-bin.zip`; checksums `.sha512`, `.sha1`, `.md5`; keys `https://downloads.apache.org/ant/KEYS`; launcher `bin\ant.bat`; variable `ANT_HOME`; entry `%ANT_HOME%\bin`.
- Gradle: list `https://services.gradle.org/versions/all`; zip from the release's `zipUrl`; no checksum URLs (SHA-256 comes from the release); no signature or keys; launcher `bin\gradle.bat`; variable `GRADLE_HOME`; entry `%GRADLE_HOME%\bin`.
- Java ranges (minimum; Gradle also maximum). The implementer checks each against the project's official compatibility pages before committing and corrects any value that differs, noting the source in a comment:
  - Maven: 2.0 → 4; 2.1–3.1 → 5; 3.2 → 6; 3.3–3.8 → 7; 3.9 → 8; 4.x → 17.
  - Ant: 1.5–1.7 → 2 (treated as "any"); 1.8 → 4; 1.9 → 5; 1.10 → 8.
  - Gradle minimum: 0.x–1.x → 5; 2.x → 6; 3.x–4.x → 7; 5.x–8.x → 8; 9.x → 17. Gradle maximum: below 4.3 → 8; 4.3 → 9; 4.7 → 10; 5.0 → 11; 5.4 → 12; 6.0 → 13; 6.3 → 14; 6.7 → 15; 7.0 → 16; 7.3 → 17; 7.5 → 18; 7.6 → 19; 8.3 → 20; 8.5 → 21; 8.8 → 22; 8.10 → 23; 8.14 → 24; 9.1 → 25; newer → no maximum.

- [ ] **Step 1: Write the failing tests:** `MAVEN.zipUrl("3.3.9")` equals the Central URL; `MAVEN.checksumUrls("3.6.3").get(0)` is the archive SHA-512; `MAVEN.checksumUrls("3.9.9").get(0).kind() == SHA512` on Central; `ANT.signatureUrl("1.6.5")` ends with `apache-ant-1.6.5-bin.zip.asc`; `GRADLE.keysUrl()` empty; `MAVEN.javaRange("3.9.9").min() == 8`; `MAVEN.javaRange("4.0.0-rc-7").min() == 17`; `GRADLE.javaRange("6.9.4").allows(17) == false`; `GRADLE.javaRange("8.10.2").allows(21) == true`; `JavaRange(8, 21).describe().equals("Java 8 to 21")`.
- [ ] **Step 2: Run `-Dtest=BuildToolsTest`** — Expected: FAIL.
- [ ] **Step 3: Implement** the three descriptions as `final class`es implementing `BuildTool`.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Describe Maven, Ant, and Gradle"`

---

### Task 5: HTTP access and cached version catalog

**Files:**
- Create: `app/src/main/java/dev/jdktrustmanager/buildtools/ToolHttp.java`, `HttpToolHttp.java`, `NotFoundException.java`, `ToolCatalog.java`, `model/ToolCatalogDocument.java`
- Modify: `app/src/main/java/dev/jdktrustmanager/install/ZipInstaller.java` (make `download`, `unzip`, `deleteRecursively` `public`; `download` rejects non-`https` URLs)
- Test: `app/src/test/java/dev/jdktrustmanager/buildtools/ToolCatalogTest.java`

**Interfaces:**
- Produces: `interface ToolHttp { byte[] get(String url) throws IOException; }` — 404 throws `NotFoundException extends IOException`; non-`https` throws `IOException("HTTPS required")`.
- Produces: `ToolCatalog(ToolHttp http, JsonStores stores, Path cacheFile)`; `DiscoCatalog.Load<List<ToolRelease>> releases(BuildTool tool, long maxAgeHours, boolean forceRefresh) throws IOException` — fresh cache returned without a request; stale or forced → fetch, save, return; fetch fails with a cache → cached value with `stale == true`; fetch fails without cache → rethrow. Releases sorted `NEWEST_FIRST`.
- Produces: `record ToolCatalogDocument(Map<Tool, Cached> tools)` with `record Cached(Instant fetchedAt, List<ToolRelease> releases)`.

- [ ] **Step 1: Write the failing tests** with a fake `ToolHttp` that counts calls and can be told to fail: fresh cache makes zero calls; expired cache refetches once; failing fetch with cache returns `stale == true` and the cached versions (Review Focus: offline with cache); failing fetch without cache throws.
- [ ] **Step 2: Run `-Dtest=ToolCatalogTest`** — Expected: FAIL.
- [ ] **Step 3: Implement** `HttpToolHttp` like `HttpDiscoTransport` (15 s connect timeout, proxy, redirects `NORMAL`, one retry on dropped connection) and `ToolCatalog`.
- [ ] **Step 4: Run `-Dtest=ToolCatalogTest,ZipInstallerTest`** — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Fetch and cache build tool version lists"`

---

### Task 6: PGP signature check

**Files:**
- Modify: `app/pom.xml` (add `org.bouncycastle:bcpg-jdk18on:1.86` and `org.bouncycastle:bcprov-jdk18on:1.86`, compile scope); `.github/dependabot.yml` (group `org.bouncycastle:*`)
- Create: `app/src/main/java/dev/jdktrustmanager/buildtools/SignatureCheck.java`
- Test: `app/src/test/java/dev/jdktrustmanager/buildtools/SignatureCheckTest.java`

**Interfaces:**
- Produces: `SignatureCheck.verify(Path file, byte[] armoredSignature, byte[] armoredKeys) -> Result` where `record Result(boolean valid, String keyId, boolean signerKnown)`; `keyId` is the signature's issuer key ID as 16 upper-case hex digits. Throws `IOException` only for unreadable input (not a signature, not a key ring).

- [ ] **Step 1: Write the failing tests.** Generate RSA key pairs with Bouncy Castle in the test, export an armored KEYS blob, sign a temp file:
  - valid signature → `valid && signerKnown`;
  - file changed after signing → `!valid`;
  - signed by a key not in KEYS → `!signerKnown`, `keyId` set;
  - key with an expiry time already in the past, signature made before it → `valid && signerKnown` (Review Focus: expired keys);
  - signature made by a signing **subkey** of a key in KEYS → `valid && signerKnown` (Review Focus: subkeys);
  - KEYS blob containing several armored blocks separated by text (as Apache's file is) → still found.
- [ ] **Step 2: Run `-Dtest=SignatureCheckTest`** — Expected: FAIL.
- [ ] **Step 3: Implement** with `PGPPublicKeyRingCollection` over every armored block in the KEYS text, `PGPObjectFactory` for the signature, `JcaPGPContentVerifierBuilderProvider` with the BC provider. Key expiry is not checked.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Verify PGP signatures with Bouncy Castle"`

---

### Task 7: Download verification policy

**Files:**
- Create: `app/src/main/java/dev/jdktrustmanager/buildtools/ToolVerifier.java`, `Checksums.java`, `VerificationException.java`
- Test: `app/src/test/java/dev/jdktrustmanager/buildtools/ToolVerifierTest.java`, `ChecksumsTest.java`

**Interfaces:**
- Consumes: `BuildTool`, `ToolRelease`, `ToolHttp`, `SignatureCheck`, `Verification`.
- Produces: `Checksums.hex(Path file, Verification kind) -> String` (lower-case); `Checksums.extract(String fileText, Verification kind) -> Optional<String>` (first hex run of the right length: 128/64/40/32, so `hash`, `hash  name`, `hash *name`, and `MD5 (name) = hash` all work).
- Produces: `ToolVerifier(ToolHttp http, Path keysDir)`; `Outcome verify(BuildTool tool, ToolRelease release, Path zip) throws IOException` with `record Outcome(Verification used, String message)`; rejection throws `VerificationException` (an `IOException`) and deletes `zip`.

Policy (from the spec, in order):
1. Gradle: `release.sha256()` must equal `Checksums.hex(zip, SHA256)` → `SHA256`, else reject `"SHA-256 mismatch"`.
2. Apache: fetch KEYS; on success write it to `keysDir/<tool>-KEYS.asc`; on failure use that cached file if present. Fetch `.asc`.
3. Both available → `SignatureCheck.verify`. `!signerKnown` → reject `"Signed by unknown key <id>"`; `!valid` → reject `"Signature check failed (key <id>)"`. Valid → also check the strongest available checksum if one downloads; a mismatch rejects. Result `SIGNATURE`, message `"Signature verified (key <id>)"`.
4. KEYS (live and cached) or `.asc` unavailable → strongest checksum that downloads, in `checksumUrls` order. Match → that kind; message `"Verified with SHA-1 only: Apache's signing keys couldn't be reached"` (or MD5 / SHA-512 wording). Mismatch → reject. None downloads → reject `"No signature or checksum could be downloaded"`.

- [ ] **Step 1: Write the failing tests** with a fake `ToolHttp` serving per-URL bytes or throwing, and keys/signatures generated as in Task 6: valid signature → `SIGNATURE`; tampered → rejected and zip deleted; unknown signer → rejected; KEYS 404 + no cache + good `.sha1` → `SHA1` and message contains `"SHA-1 only"`; KEYS unreachable + cached KEYS → `SIGNATURE`; everything unreachable → rejected; Gradle good/bad SHA-256. `ChecksumsTest`: the four text formats.
- [ ] **Step 2: Run `-Dtest=ToolVerifierTest,ChecksumsTest`** — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Verify build tool downloads"`

---

### Task 8: Layout and installer

**Files:**
- Create: `app/src/main/java/dev/jdktrustmanager/buildtools/ToolLayout.java`, `ToolInstaller.java`, `JdkPicker.java`, `RunCheck.java`
- Modify: `app/src/main/java/dev/jdktrustmanager/install/JdkScanner.java` (skip `build-tools` like `trusted-certs`), `install/Layout.java` (`checkInsideRoot` refuses `<root>\build-tools` and anything under it as a JDK target)
- Test: `app/src/test/java/dev/jdktrustmanager/buildtools/ToolInstallerTest.java`, `ToolLayoutTest.java`; extend `install/InventoryTest.java`

**Interfaces:**
- Consumes: `ZipInstaller.download/unzip/deleteRecursively` (now public), `ZipInstaller.InstallProgress`, `ToolVerifier`, `BuildTool`.
- Produces: `ToolLayout.folder(Path root) -> Path` (`root\build-tools`); `ToolLayout.destination(Path root, BuildTool, String version) -> Path` (rejects versions containing `..`, `/`, `\`, or characters outside `[0-9A-Za-z.\-]`); `ToolLayout.installed(Path root, BuildTool) -> List<Path>` (folders named `<prefix>-<v>` whose launcher exists; ignores names starting with `.`).
- Produces: `JdkPicker.pick(JavaRange range, String activeJavaHome, List<Installation> managed) -> Optional<Path>` (active home if its major is in range, else the newest managed JDK in range); `RunCheck.run(BuildTool, Path home, Path javaHome) -> String` (runs `<launcher> -v`/`-version`/`--version` with `JAVA_HOME` set and `PATH` prefixed, 120 s timeout, returns output; throws when exit code ≠ 0).
- Produces: `ToolInstaller(ToolHttp http, ToolVerifier verifier, Path zipCache)`; `ToolInstallation install(BuildTool tool, ToolRelease release, Path root, Optional<Path> javaForRunCheck, ZipInstaller.InstallProgress progress) throws IOException`; `void uninstall(ToolInstallation installation, Path root) throws IOException` (refuses `active` or `pinned` rows and any path not directly inside `ToolLayout.folder(root)`; deletes the folder; keeps the cached zip).

Install steps: refuse if destination exists (`"<tool> <v> is already installed"`); download to `zipCache/<tool>-<v>.zip` (reuse when present and it verifies); verify; unzip into `build-tools\.unpack-<millis>`; find the single top-level folder that contains one of `tool.launchers()`; move to destination; if `javaForRunCheck` present, `RunCheck.run` and require the output to contain the version, rolling back the destination on failure; always delete the `.unpack-*` folder.

- [ ] **Step 1: Write the failing tests** with a fake `ToolHttp` serving a small zip built in the test (`apache-maven-3.3.9/bin/mvn.cmd`, plus a `.sha1` for it, KEYS 404) and `Optional.empty()` for the run check: installs to `root\build-tools\maven-3.3.9` with `verification == SHA1`; second install of the same version throws "already installed"; a zip whose only top folder lacks a launcher throws and leaves no destination; a pre-existing `build-tools\.unpack-123` folder does not appear in `ToolLayout.installed` and does not block install (Review Focus: leftover temp folders); `ToolLayout.destination(root, MAVEN, "..\\x")` throws; `JdkScanner` scan of a root containing `build-tools\maven-3.3.9` finds no JDK there; `Layout.checkInsideRoot(root, root.resolve("build-tools"), null)` throws; `uninstall` deletes an inactive version, refuses an active one, a pinned one, and a row whose path is `root\temurin-21`. `JdkPicker`: active 8 + range min 17 + managed 21 → 21; nothing in range → empty.
- [ ] **Step 2: Run `-Dtest=ToolInstallerTest,ToolLayoutTest,InventoryTest`** — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Install build tools side by side"`

---

### Task 9: Environment plans and PATH rules

**Files:**
- Create: `app/src/main/java/dev/jdktrustmanager/env/EnvironmentPlan.java`
- Modify: `app/src/main/java/dev/jdktrustmanager/env/PathEditor.java`, `env/UserEnvironment.java`, `env/JavaVariables.java` (its `isOtherJava` filter must not hide tool entries), `ui/EnvironmentPane.java` and `ui/InstalledPane.java` (JDK switch uses `EnvironmentPlan.forJava`)
- Test: `app/src/test/java/dev/jdktrustmanager/env/EnvironmentPlanTest.java`, extend `env/PathEditorTest.java`

**Interfaces:**
- Consumes: `BuildTool` (`homeVariables`, `pathEntry`, `launchers`, `folderPrefix`), `BuildTools.ALL`.
- Produces: `record EnvironmentPlan(Map<String, String> set, Set<String> delete, String path)` (`set` holds `REG_SZ` homes; `path` is written as `REG_EXPAND_SZ`).
- Produces: `EnvironmentPlan.forJava(String currentPath, String root, String javaHome, boolean removeOtherJava)`; `EnvironmentPlan.forTool(String currentPath, String root, BuildTool tool, String homeOrNull, List<String> otherEntriesToRemove)` — `homeOrNull == null` means Remove from PATH: delete the tool's variables and entry.
- Produces: `PathEditor.TOOL_ENTRIES` (`%MAVEN_HOME%\bin`, `%ANT_HOME%\bin`, `%GRADLE_HOME%\bin`); `PathEditor.isToolEntry(String entry, String root)` (one of `TOOL_ENTRIES`, or under `<root>\build-tools\`); `PathEditor.otherEntriesFor(String currentPath, String root, BuildTool tool) -> List<String>` (entries that provide that tool's launcher but aren't managed: `%M2_HOME%\bin`, `…\apache-maven-*\bin`, Chocolatey/Scoop shims folders containing `mvn.cmd` etc., matched by name pattern without touching disk).
- Produces: `UserEnvironment.apply(EnvironmentPlan plan)` (`reg add` each `set`, `reg delete HKCU\Environment /v <name> /f` each `delete` ignoring "not found", write `Path`, broadcast once). The old `apply(String, String)` delegates to it.

Rules: `edit` and both plan builders rebuild the managed block in the Global Constraints order; JDK cleanup (`isManaged` for `<root>\…\bin` and `isOtherJava`) never matches `isToolEntry` entries; a tool plan only adds/removes its own entry and variables.

- [ ] **Step 1: Write the failing tests:** JDK switch on `"%MAVEN_HOME%\\bin;C:\\Windows"` keeps `%MAVEN_HOME%\bin` right after `%JAVA_HOME%\bin`; JDK switch with removeOtherJava on `"C:\\Java\\build-tools\\ant-1.10.15\\bin"` keeps it; Maven plan on `"%JAVA_HOME%\\bin;%ANT_HOME%\\bin;C:\\Windows"` yields `%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%ANT_HOME%\bin;C:\Windows` and `set` = `MAVEN_HOME`, `M2_HOME`; Maven plan with no `%JAVA_HOME%\bin` puts `%MAVEN_HOME%\bin` first; Maven remove plan deletes `MAVEN_HOME`, `M2_HOME` and the entry, keeps Ant; `otherEntriesFor(…, MAVEN)` on `"%M2_HOME%\\bin;C:\\apache-maven-3.3.9\\bin;C:\\ant\\bin"` returns the first two only, and a plan built without passing them leaves both in place (Review Focus: legacy entries); `JavaVariables` listing still shows tool entries.
- [ ] **Step 2: Run `-Dtest=EnvironmentPlanTest,PathEditorTest,JavaVariablesTest`** — Expected: FAIL.
- [ ] **Step 3: Implement**, then move `EnvironmentPane.applyUserScope` and `InstalledPane.setToPath` onto `EnvironmentPlan.forJava` + `UserEnvironment.apply(plan)`.
- [ ] **Step 4: Run the whole suite** — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Keep build tool entries when switching PATH"`

---

### Task 10: Adopt existing tool installs

**Files:**
- Create: `app/src/main/java/dev/jdktrustmanager/buildtools/ToolAdopter.java`
- Test: `app/src/test/java/dev/jdktrustmanager/buildtools/ToolAdopterTest.java`

**Interfaces:**
- Consumes: `BuildTool`, `ToolLayout`, `ZipInstaller.deleteRecursively`.
- Produces: `ToolAdopter.candidates(BuildTool tool, Map<String, String> userAndMachineVariables, String path, List<Path> extraRoots) -> List<Path>` (homes from the tool's variables, `PATH` entries whose parent has a launcher, and children of `extraRoots`; deduplicated; homes already under `build-tools` excluded); `ToolAdopter.DEFAULT_ROOTS` (`C:\Program Files\Apache`, `C:\Program Files`, `C:\`, `C:\tools`, `C:\ProgramData\chocolatey\lib\<maven|ant|gradle>\tools`, `%USERPROFILE%\scoop\apps\<maven|ant|gradle>`); `ToolAdopter.versionOf(BuildTool, Path home) -> Optional<String>`; `ToolInstallation adopt(BuildTool, Path home, Path root) throws IOException` (copy via `<dest>.adopt-<millis>` then move; refuse existing destination; `ADOPTED`, `NONE`).

Version reading: Maven `lib\maven-core-<v>.jar`; Ant `lib\ant.jar` manifest `Implementation-Version`; Gradle `lib\gradle-core-api-<v>.jar`, else `lib\gradle-launcher-<v>.jar`.

- [ ] **Step 1: Write the failing tests** with fixture homes built in the test (empty jars with the right names; an `ant.jar` with a manifest): each tool's `versionOf` returns the version; `candidates` finds a home named by `MAVEN_HOME` and one from a `PATH` entry, once each; `adopt` copies to `build-tools\maven-3.3.9`, leaves the source, and refuses a second time.
- [ ] **Step 2: Run `-Dtest=ToolAdopterTest`** — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Adopt existing Maven, Ant, and Gradle installs"`

---

### Task 11: Tabs and tool screens

**Files:**
- Modify: `app/src/main/java/dev/jdktrustmanager/model/Settings.java` (append `boolean showBuildToolPreReleases`), every `new Settings(` (`JdkTrustManagerApp`, `ui/SettingsPane` with a "Show build tool pre-releases" checkbox, `CatalogBrowserTest`, `JsonStoresTest`)
- Modify: `ui/Screens.java` (`TOP_LEVEL = List.of("Java", "Maven", "Ant", "Gradle")`, `TOOL = List.of("Versions", "Installed", "Environment")`; `NAMES` unchanged), `ui/Confirmations.java`, `JdkTrustManagerApp.java`
- Create: `ui/AppTabs.java`, `ui/ToolTab.java`, `ui/ToolVersionsPane.java`, `ui/ToolInstalledPane.java`, `ui/ToolEnvironmentPane.java`, `ui/ToolContext.java`
- Test: `app/src/test/java/dev/jdktrustmanager/SmokeTest.java`, `ui/ConfirmationsTest.java`, `persist/JsonStoresTest.java` (a settings file without the new field reads as `false`)

**Interfaces:**
- Consumes: everything above.
- Produces: `record ToolContext(Path root, Path toolsFile, JsonStores stores, WorkQueue queue, ToolCatalog catalog, ToolInstaller installer, ToolAdopter adopter, Supplier<Settings> settings, Path installationsFile)`; `ToolTab(BuildTool, ToolContext)` (a `TabPane` of the three panes); `AppTabs(Node java, List<ToolTab> tools)` (closing disabled).
- Produces: `Confirmations.installTool(String name, String version, String folder, boolean signed)`, `setToolPath(String name, String version, String javaWarningOrNull)`, `removeTool(String name, String version, String folder)`, `removeToolFromPath(String name, List<String> variables)`.

Screen behavior (from the spec's Screens section; jobs on `WorkQueue` with kinds `INSTALL`, `UNINSTALL`, `ADOPT` so the existing cancel button works for downloads):
- **Versions:** Install above a filter and the list; rows `"<v>   installed"` / `"<v>   pre-release"`; pre-releases hidden unless the setting is on; stale list shows "Offline: showing the list from <date>"; Install confirmation states how it will be verified ("checked against Apache's signing keys" or "checked by SHA-256").
- **Installed:** rows `"<v>   <path>   [on PATH] [pinned] [signature|SHA-512|SHA-256|weak checksum|adopted]"`; Refresh, Set to path, Adopt (lists candidates in a choice dialog), Pin/Unpin, Remove; Remove refused when on PATH or pinned; Set to path shows the `JavaRange` warning when the active JDK is outside it.
- **Environment:** the tool's variables and entries, preview of `EnvironmentPlan.forTool`, Set, Remove from PATH, "Remove other <Tool> entries" checkbox listing `otherEntriesFor`, PowerShell snippet `$env:<VAR> = '<home>'; $env:Path = '<home>\bin;' + $env:Path` for every variable.

- [ ] **Step 1: Update `SmokeTest`** to assert `Screens.TOP_LEVEL` and `Screens.TOOL` and that `Screens.NAMES` is still the six Java screens; add `ConfirmationsTest` cases (install wording names the folder and the verification; set-to-path includes the Java warning when given); add the settings-compatibility test.
- [ ] **Step 2: Run `-Dtest=SmokeTest,ConfirmationsTest,JsonStoresTest`** — Expected: FAIL.
- [ ] **Step 3: Implement** the settings field and checkbox, `Screens`, `Confirmations`, the panes, `AppTabs`, and wire `JdkTrustManagerApp`: `new BorderPane(new AppTabs(new MainView(...), BuildTools.ALL.stream().map(t -> new ToolTab(t, context)).toList()))`.
- [ ] **Step 4: Run the whole suite**, then launch the app (`mvn -f app/pom.xml javafx:run` with `JAVA_HOME` on a JDK 21) and check: four top-level tabs; Java tab shows the six old screens; Maven Versions lists versions; installing Maven 3.3.9 ends with "signature" or "weak checksum" on its Installed row; Set to path then `cmd /c "mvn -v"` in a new terminal prints 3.3.9; switching the JDK keeps `mvn` working.
- [ ] **Step 5: Commit** `git commit -m "Add Java, Maven, Ant, and Gradle tabs"`

---

### Task 12: Packaging and docs

**Files:**
- Modify: `THIRD-PARTY-NOTICES.txt` (Bouncy Castle, MIT-style licence, `https://www.bouncycastle.org/licence.html`), `README.md` (What it does: a "Manages Maven, Ant, and Gradle" block with layout `C:\Java\build-tools\`, variables, verification rule; Screens: top-level tabs), `.agent/project.md` (the `buildtools` package)

- [ ] **Step 1: Run** `.\package-app.ps1 -JavaHome C:\Java\temurin-21 -Maven <mvn.cmd>` — Expected: a zip in `app\target` roughly 8–10 MB larger than 0.2.0's 34.6 MB, with `bcpg-jdk18on-1.86.jar` and `bcprov-jdk18on-1.86.jar` under `app\`.
- [ ] **Step 2: Unzip and launch** the packaged exe; the window opens with four tabs.
- [ ] **Step 3: Grep tracked files** for local usernames and `C:\Users\` paths (only the `C:\Users\me` test fixture may match).
- [ ] **Step 4: Commit** `git commit -m "Document build tools and ship Bouncy Castle notices"`
