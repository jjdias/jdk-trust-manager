# JDK Trust Manager

Windows desktop app that installs JDK builds from the Foojay Disco API, keeps each one at `C:\Java\<vendor>-<major>\`, chooses which one is on `PATH`, and shows and updates the truststore of every installation.

Packages are portable Windows zips. The app downloads a zip, checks its SHA-256, unpacks it, and moves the JDK home into place. There is no MSI and no winget install.

## Product decisions

- The app is a JavaFX desktop program. It ships with its own runtime, built with `jlink` and `jpackage`, so uninstalling or replacing a JDK under `C:\Java` cannot take the app down with it.
- Language level of the app itself: Java 21. UI toolkit: JavaFX 21. Build: Maven.
- Windows only for the first version. Install talks to the Disco API and vendor CDNs. Environment code talks to the registry. Certificate code talks to `keytool`.
- One directory per vendor variant and major version: `C:\Java\<vendor>-<major>\`. That directory is the JDK home. It contains `bin`, `lib`, `conf`, and `release`. A newer patch of the same vendor and major replaces that directory.
- The app owns `JAVA_HOME` and the single managed `PATH` entry. A zip install does not set either.
- Catalog API is pinned to `https://api.foojay.io/disco/v3.0`. A package is listed only when `free_use_in_production` is `true`, plus the request filters in the next section.
- Mutations of a truststore always go through that installation's own `bin\keytool.exe`, so Java 8 JKS stores and later store types stay compatible with the JDK that will read them.
- Passwords are not written into the JSON state file. The default `cacerts` password is `changeit`. A per-installation override goes in Windows Credential Manager.

## Catalog

`GET /distributions` fills the vendor list. It already includes version strings, so the screen does not call `/packages` until the user selects a vendor and a major. `latest=available` is the default row. The full general-availability list is loaded only when the version picker opens. Every request sends `User-Agent: JdkTrustManager`. Connect timeout is 15 seconds. A dropped connection is retried once. A checksum failure is not retried.

A distribution is shown when `maintained` and `available` are true. Its packages are requested with:

- `operating_system=windows`
- `archive_type=zip`
- `package_type=jdk`
- `architecture=x64` (responses that say `amd64` are the same architecture)
- `release_status=ga`
- `directly_downloadable=true`
- `free_use_in_production=true` is checked on each package object, not only in the query, and packages with any other value are dropped
- `build_of_graalvm=true` stays behind a setting, off by default, including when the license flag is true. Native-image kits stay out of the default list.

`lib_c_type` is a Linux concern. On Windows the value is `c_std_lib` and the UI does not offer a libc choice.

JavaFX is the same package with `javafx_bundled=true`. Those installs use a `-fx` directory suffix (`liberica-fx-21`, `zulu-fx-21`). The default catalog view is `javafx_bundled=false`.

The directory slug is the Disco `api_parameter` with underscores replaced by hyphens (`sap_machine` becomes `sap-machine`). x64 has no architecture suffix. An arm64 package, when requested, uses `-arm64`.

Early-access versions (`28-ea`, `release_status=ea`) stay off the default list. A setting can show them later.

The LTS badge uses the package field `term_of_support=lts`. The UI still allows installing a general-availability build whose upstream support has ended. Vendor support and upstream support are not the same thing, and the badge does not claim the vendor has dropped the build.

Majors below 8 are hidden by default and can be shown from settings.

The UI groups results as vendor, then major, then `java_version`. Each card shows the distribution name, JavaFX when bundled, `tck_tested`, `term_of_support`, architecture, size, and whether that directory already exists. `tck_tested=unknown` does not hide a package.

Before a download, compare the package `size` with free space on the target drive. The verification process runs with `JDK_JAVA_OPTIONS` and `_JAVA_OPTIONS` cleared. If Windows locks `java.exe` during the final rename, the move is retried a few times. "Update all" shows a preview and skips pinned installs.

After every install the app reads the `release` file and records `JAVA_VERSION`, `IMPLEMENTOR`, and `OS_ARCH`. If the feature version does not match the requested major, the install is marked failed and the new folder is removed.

Catalog cache: `%LOCALAPPDATA%\JdkTrustManager\catalog.json`, refreshed on startup when older than 24 hours, and whenever the user presses Refresh. If the API is unreachable, the cache is shown and Install stays available for versions whose zip is already in the local cache.

Example that resolved on 19 November 2026: Temurin 21, Windows x64 zip, `OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.zip`, SHA-256 `a4dbaa05fca4bd89d84584e58c459bd732efebb1722af80ab13c6516832004f9`, direct URL on the Adoptium GitHub release. Liberica 21 returned two zips of `21.0.13+11`: the standard JDK and the same build with `javafx_bundled=true` (`bellsoft-jdk21.0.13+11-windows-amd64-full.zip`).

## Install layout

```text
C:\Java\
  trusted-certs\              shared certificates, not a JDK
  temurin-21\                 JDK home for the active Temurin 21 patch
  zulu-21\
  corretto-17\
  microsoft-21\
  liberica-21\
  liberica-fx-21\
  semeru-17\
  sap-machine-21\
  openlogic-11\
  zulu-fx-21\
```

`<vendor>` is the Disco `api_parameter` normalized as above. `<major>` is `major_version`, so a Corretto build reported as `1.8.0.x` or `8.0.x` is `C:\Java\corretto-8`.

The root is configurable. The default is `C:\Java`. Changing it does not move existing installs; the UI lists what would move and does it only after confirmation.

State for each install, in `%LOCALAPPDATA%\JdkTrustManager\installations.json`:

- vendor slug, major, Disco package id, `java_version`, `javafx_bundled`
- path, architecture, implementor, full version from `release`
- SHA-256 of the zip that was installed
- installed-at, source (`foojay` or `adopted`)
- whether it is the active `JAVA_HOME`
- last truststore sync time and fingerprint of the applied overlay

Zip cache, outside `C:\Java`: `%LOCALAPPDATA%\JdkTrustManager\cache\<sha256>.zip`.

Logs: `%LOCALAPPDATA%\JdkTrustManager\logs\`.

### Download and place

Install sequence, on a background thread:

1. `GET /packages` for the chosen distribution, major, and JavaFX flag. The user can pick an older `java_version`; the default is the newest `release_status=ga` package.
2. `GET /ids/<id>` and read `direct_download_uri`, `checksum`, and `checksum_type`. Refuse to download when `checksum_type` is not `sha256` or the checksum is empty. Do not store the redirect URL as the artifact location.
3. Download `direct_download_uri` over HTTPS to the zip cache. Skip the transfer when that SHA-256 is already cached. Compare the file hash to the API checksum and delete the file on mismatch.
4. Unzip with `ZipInputStream` into a temp directory. Reject any entry whose path is absolute or contains `..`.
5. Find the extracted directory that contains `bin\java.exe` and `release`. Move that directory to `C:\Java\<vendor>-<major>`.
6. Run `<jdk>\bin\java.exe -version` and read `release`. On a feature-version mismatch, delete the new directory and keep the previous one.

The first implementation task is a spike that downloads Temurin 21 and Liberica 21 (standard and JavaFX), checks the hashes, and confirms each zip has a single JDK home.

Upgrades of the same vendor and major:

1. Export the trust overlay that was applied to the old tree (see Certificates).
2. Rename `C:\Java\<vendor>-<major>` to `C:\Java\<vendor>-<major>.bak-<timestamp>`.
3. Download and place the new patch into `C:\Java\<vendor>-<major>`.
4. Replay the trust overlay onto the new `cacerts`.
5. Delete the backup only after replay succeeds and `java -version` from the new home prints the expected version. If install fails, rename the backup back.

Uninstall deletes `C:\Java\<vendor>-<major>`. There is no vendor registration to remove. If that directory is the active `JAVA_HOME`, the app clears `JAVA_HOME` and its `PATH` entry first, and asks which remaining install should become active. The cached zip is kept so a reinstall does not download it again.

Adopt: a scan can register a JDK that is already on disk. The scan looks at `C:\Java`, `C:\Program Files\Eclipse Adoptium`, `C:\Program Files\Zulu`, `C:\Program Files\Microsoft`, `C:\Program Files\Amazon Corretto`, `C:\Program Files\BellSoft`, `C:\Program Files\SapMachine`, `C:\Program Files\OpenLogic`, and the JavaSoft registry keys. Adopting copies or moves the JDK home into the layout only when the user confirms. A foreign install left in Program Files is shown as unmanaged and is never deleted automatically.

The running app image is never a candidate for uninstall, replace, or adopt.

## Background work

The JavaFX thread never waits on HTTP, unzip, keytool, or certificate downloads. One single-thread queue runs installs, uninstalls, and truststore updates so two installs cannot rewrite `C:\Java` at once. The UI shows a log pane, a progress state (`queued`, `downloading`, `verifying`, `unpacking`, `updating truststore`, `failed`, `done`), and a cancel button. Cancel aborts the HTTP transfer. Once unzip has started, cancel is disabled until that install finishes or fails.

Elevation: creating `C:\Java` and writing machine-wide environment variables needs an elevated helper. Placing a zip does not. The normal UI stays as the current user. The helper accepts a single JSON command on stdin (`create-root`, `set-system-env`) and writes a result on stdout. On `create-root`, grant the Users group modify permission on `C:\Java` so later installs do not need elevation. User-scope environment changes do not need elevation.

## Environment variables

Two scopes, shown as a toggle:

- User: `HKCU\Environment`
- System: `HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment`

Write the registry values directly. Do not call `setx`. `setx` truncates `PATH` around 1024 characters.

Values when an installation is made active:

- `JAVA_HOME` = `C:\Java\<vendor>-<major>` as `REG_SZ`
- `PATH` as `REG_EXPAND_SZ`, with exactly one managed JDK entry: `%JAVA_HOME%\bin` at the front

Making an installation active:

1. Read the current `PATH` for the chosen scope.
2. Show a preview of the old and new values.
3. Remove previous managed entries. A managed entry is `%JAVA_HOME%\bin` or a path under the configured root that ends in `\bin` (`C:\Java\temurin-21\bin` and similar).
4. Insert `%JAVA_HOME%\bin` at the front.
5. Set `JAVA_HOME`.
6. Broadcast `WM_SETTINGCHANGE` with `Environment` so new processes pick it up. Already-open terminals keep the old environment until they are reopened. The UI says that.

The preview also lists other `java.exe` locations from `where.exe java`, including `C:\Program Files\Common Files\Oracle\Java\javapath`. Those are not removed unless the user turns on "Remove other Java PATH entries" and confirms each path. The app never silently deletes Oracle, Scoop, or IDE entries.

Optional, off by default: also write the JavaSoft current-version keys that old tools still read, `HKLM\SOFTWARE\JavaSoft\JDK` and `HKCU\SOFTWARE\JavaSoft\JDK`, with `CurrentVersion` and `JavaHome`. Off by default because those keys are a second source of truth.

Not set by this app: `JDK_HOME`, `JRE_HOME`, `CLASSPATH`, `JDK_JAVA_OPTIONS`, `_JAVA_OPTIONS`.

Per-shell use, without changing the registry: a button copies a PowerShell snippet.

```powershell
$env:JAVA_HOME = 'C:\Java\zulu-21'
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
```

Maven and Gradle export, also optional buttons, write snippets only. They do not edit the user's `~/.m2/toolchains.xml` or Gradle files until the user confirms a file path.

- Maven toolchain snippet: vendor implementor plus the major version and the directory.
- Gradle: a `java { toolchain { languageVersion = JavaLanguageVersion.of(<major>) } }` note, plus the directory so Gradle can find it if auto-detection misses `C:\Java`.

The Installed screen marks one row as active, shows the scope, and shows the resolved `java.exe` after the change.

## Certificates

Each JDK trusts the store at `<jdk>\lib\security\cacerts`. The UI calls this the truststore. Entries are trusted certificates. The app does not import private keys into `cacerts`.

A second list lets the user point at extra keystores (application `.jks` or `.p12` files). Those are inspected the same way. Private-key entries are shown as "private key present" plus the certificate subject and dates. Key bytes and passwords are never printed in the log.

### Reading an entry

Run `<jdk>\bin\keytool.exe -list -v -keystore <jdk>\lib\security\cacerts -storepass <password>` for Java 8, and `keytool -list -v -cacerts -storepass <password>` for Java 9 and newer. Parse alias, owner, issuer, serial, signature algorithm, SHA-256 fingerprint, valid from, valid until, and entry type.

The truststore screen:

- Table of aliases with subject, issuer, SHA-256 fingerprint, not-before, not-after.
- Search across alias and subject.
- Expiry badges: expired, expiring within 30 days, expiring within 90 days.
- Detail pane with the parsed fields and the PEM export of the selected certificate.
- Compare mode: two installations side by side, grouped into only-left, only-right, and different-fingerprint. Match on the certificate SHA-256, not on the alias. Vendors name the same CA differently.
- Dashboard counts per installation: trusted certificates, expired, expiring within 30 days, days since the last overlay sync.

### Backup

Before every write, copy `cacerts` to `C:\Java\<vendor>-<major>\.jdk-trust-manager\backups\<timestamp>\cacerts`. Keep the last 10 backups. Restore copies a chosen backup back over `cacerts`.

### Shared certificate folder

Enabled certificates live beside the JDKs, not inside one of them and not under `%LOCALAPPDATA%`:

```text
C:\Java\trusted-certs\
  sources.json
  mozilla\cacert.pem
  urls\<slug>\<downloaded file>
  local\<files added through the GUI>
```

`sources.json` is the list of HTTPS URLs the app keeps updated, plus the enable flag for every certificate parsed from those files and from `local\`. The scanner that adopts JDKs ignores `trusted-certs`. Uninstall of a JDK does not delete it.

Private test CAs are a primary use of this folder. Internal CM clusters present certificates Java will not trust until those CAs are in `cacerts`. The user adds them by dropping a PEM or CER into `local\`, or by adding the cluster's HTTPS URL. Files placed in `local\` are picked up on the next refresh and start checked. The GUI lists them so the user can uncheck one. This program is a free local tool: a bad file in that folder is the operator's choice, and the certificate screen says so in one line. A PEM that contains a private key is still rejected, because a truststore cannot use it.

Each certificate row shows subject, issuer, SHA-256, validity, and whether it is a CA (`Basic Constraints` with `CA:true`). The checked set is global. Every managed JDK receives that same set, so switching `JAVA_HOME` keeps the certificates Selenium and the internal clusters need. A newly installed JDK gets the set at the end of install.

Built-in URLs, both enabled by default:

1. Mozilla CA bundle, `https://curl.se/ca/cacert.pem`. Its certificates start checked.
2. TestNG host, `https://testng.org`. TestNG downloads `https://testng.org/testng-1.0.dtd` when Selenium runs suites. The app opens that HTTPS port, saves the certificate chain as PEM under `urls\testng\`, and syncs those certificates. When the host presents a new chain, the app downloads it and syncs it to every managed JDK. The activity log records the old and new fingerprints, and the `cacerts` backup can undo it.

Windows roots (`Cert:\LocalMachine\Root`) are an optional local source, off by default. Turning it on copies those certificates into the shared folder and lists them for checking. It is off by default because a corporate root can be an intercepting proxy.

### URL updates and sync

On startup and every 24 hours the app downloads each enabled HTTPS URL again. Connect timeout is 15 seconds, one retry on a dropped connection, no retry when the file is not a certificate or PEM bundle. The Windows system proxy is used. A download larger than 5 MB is refused.

When the file hash changes, certificates are parsed again. Sync then runs against every managed JDK:

- Add a checked certificate that is missing.
- Remove a `jkm-` alias this app created when that certificate is unchecked or has left an updated bundle.
- Leave every other alias in `cacerts` untouched. The vendor store is never replaced wholesale.
- Skip expired certificates until the user checks them anyway.
- A user-added certificate without `CA:true` stays unchecked until the user confirms it. The built-in TestNG chain is checked by default, including intermediates that are not trust anchors, because that chain is what the TestNG host presents.
- One fingerprint is imported once even if two sources contain it.
- Each JDK is synced with its own `keytool`. A Java 8 failure is reported on that row and does not stop the newer JDKs.
- Backup `cacerts` before the write. The activity log records added and removed fingerprints. Restore from a backup undoes a bad sync.
- The first time a source is enabled, the GUI shows how many certificates will be added. Later updates of a bundle such as Mozilla apply on their own and are listed in the activity log.
- A user-added URL that contains a single certificate pauses when that fingerprint changes. The GUI asks before replacing it. The built-in TestNG chain does not pause: a new chain is downloaded and synced, and the activity log shows the fingerprint change.
- The checked certificates can be exported as one PEM file for tools that are not Java.
- Named trust profiles come later, for two projects that must not share the same private CA. The first version has one global checked set.
- A setting pauses automatic sync. Manual "Sync now" remains.
- Already running Java processes keep the truststore they loaded at startup. The UI says to restart those processes.

Managed aliases are `jkm-` plus the first 8 hex characters of the SHA-256. Vendor aliases that happen to be the same certificate are left in place.

After a JDK upgrade replaces `cacerts`, the same checked set is applied before the upgrade is marked complete. The app's own bundled runtime is never a sync target.

Import of a single file and delete of a single alias are on the same screen, with the same backup rule. Delete asks for the alias name.

Export writes the selected certificate as PEM. Export of the whole store writes one PEM bundle.

Commands used, always with that JDK's `keytool`:

- List: `-list -v`
- Import: `-importcert -noprompt -alias <alias> -file <cer> -keystore <cacerts> -storepass <password>`
- Delete: `-delete -alias <alias> -keystore <cacerts> -storepass <password>`
- Export: `-exportcert -rfc -alias <alias> -keystore <cacerts> -storepass <password>`

If `keytool` returns a bad-password error, the UI asks for the store password and offers to save it in Credential Manager.

## Other behavior included in the first releases

- Health row for each install: `release` file present, `java.exe` runs, version matches the recorded `java_version`, `cacerts` readable, overlay up to date, directory is or is not `JAVA_HOME`.
- Filter the catalog by vendor, major, LTS only, installed only, update available, JavaFX.
- Update available compares the installed `java_version` with the newest general-availability package for that distribution, major, architecture, and JavaFX flag.
- Pin an install so "update all" skips it.
- Notes field stored in `installations.json`.
- Activity log in the UI, backed by the log files.
- Use the Windows system proxy for the Disco API, vendor zips, and the Mozilla download.
- Architecture: x64 packages are the default. An arm64 install uses a `-arm64` directory so it does not overwrite x64.
- Copy trusted certificates from one installation to others, using the same preview and fingerprint match.
- Report button: write an HTML file with installations, active `JAVA_HOME`, and certificates that are expired or expiring within 30 days.

## Application structure

```text
app/
  pom.xml
  src/main/java/dev/jdktrustmanager/
    JdkTrustManagerApp.java
    ui/                 JavaFX screens
    catalog/            Disco API client and cache
    install/            download, checksum, unzip, layout, adopt
    env/                registry PATH and JAVA_HOME
    trust/              keytool, trusted-certs, URL sync
    model/              Installation, Package, CertificateEntry
    persist/            installations.json, catalog.json
  src/main/resources/
```

Packages stay free of JavaFX except `ui`. Catalog, install, env, and trust are callable from tests without a window.

Screens:

1. Catalog. Vendors, majors, patches, Install.
2. Installed. Path, version, active badge, update, uninstall, make active.
3. Environment. Scope, preview, apply, copy per-shell snippet, toolchain snippets.
4. Truststore. Pick an installation or an extra keystore, table, detail, compare, import, delete, export.
5. Trusted certificates. URL list, downloaded and local files, per-certificate checkboxes, sync status per JDK.
6. Activity. Queue and log.

Settings live in `%LOCALAPPDATA%\JdkTrustManager\settings.json`: root directory, environment scope, overlay choices, catalog max age, "hide majors below 8", "hide upstream end of life".

## Security rules

- The app never displays or logs truststore passwords, keystore passwords, or private key material. Command lines passed to `keytool` put the password in the process command line, which a local admin can see. Document that, and prefer `keytool`'s stdin password prompt where that JDK's keytool supports it.
- Download URLs come from the Disco package detail, not from text typed into the UI. The unzipper rejects absolute paths and `..`.
- Disco, vendor zip, and certificate-source downloads require HTTPS. A zip is deleted when its SHA-256 does not match.
- `C:\Java\trusted-certs\local` is an intentional drop folder. Certificates found there are synced. The screen states that the operator is responsible for what they put there. Sync never reads a private key.
- A path that is not inside the configured root cannot be deleted by Uninstall.
- The bundled runtime directory cannot be selected as an install target.

## Build order

1. Spike. Download Temurin 21 and both Liberica 21 zips (standard and JavaFX), verify SHA-256, and confirm the JDK home inside each archive. Confirm `keytool -list -v -cacerts` against Temurin 21 and `keytool -list -v -keystore` against a Java 8 build.
2. Model, JSON state, and Disco client, with tests using saved `/distributions` and `/packages` responses.
3. Catalog screen against the live API, falling back to the cache.
4. Install queue, checksum, safe unzip, layout under `C:\Java`, `release` verification.
5. Installed screen, uninstall, adopt scan.
6. Environment preview and apply for the user scope, then the system scope.
7. Truststore list, detail, compare, backup, single import and delete.
8. `trusted-certs`, Mozilla and the TestNG host chain as default URLs, drop folder for private CAs, per-certificate checkboxes, sync to every managed JDK, replay after upgrade.
9. Health, update-available, pin, report, per-shell and toolchain snippets.
10. Package the app with its own runtime.

## Tests

- Parser tests on saved Disco JSON, including a package with `free_use_in_production=false` that must be dropped, an `amd64` package treated as x64, and a JavaFX package mapped to a `-fx` directory.
- Layout tests: slug and major mapping, refusal to write outside the root, Java 8 mapping to major 8, zip entries with `..` rejected.
- PATH editor tests: prepend `%JAVA_HOME%\bin`, remove an older `C:\Java\*\bin`, leave an unrelated Oracle path in place unless asked.
- Certificate diff tests: same fingerprint different alias, managed alias removed from the source, vendor alias preserved.
- PEM splitter tests on a small fixture bundle.
- Checksum tests: matching hash kept, mismatch deletes the file. HTTP is not required; the tests feed a local zip.

## Out of scope for now

- Linux and macOS, SDKMAN, Scoop, and winget installs.
- JRE packages and native-image kits (`liberica_native`, Mandrel).
- Early-access builds until a setting turns them on.
- Editing private-key entries, generating key pairs, or creating CSRs.
- Replacing a vendor `cacerts` with a downloaded bundle.
- Named trust profiles. The first version syncs one global checked set.
