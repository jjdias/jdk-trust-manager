# JDK Trust Manager

A Windows desktop app that installs JDK builds, chooses which one is on `PATH`, and keeps the same trusted certificates in every JDK's truststore.

It is built for machines that run several JDKs and need private or test CAs trusted by all of them. Examples are Selenium suites that fetch the TestNG DTD and services behind an internal certificate authority.

> Status: early and Windows only. The full product spec is in [roadmap.md](roadmap.md). Some features described there do not have a screen yet.

## Download

Get `JdkTrustManager-<version>-windows-x64.zip` from the [latest release](https://github.com/jjdias/jdk-trust-manager/releases/latest).

1. Unzip it anywhere, for example `%LOCALAPPDATA%\Programs\JdkTrustManager`. Keep the whole folder together, because the exe needs the `app` and `runtime` folders next to it.
2. Run `JdkTrustManager.exe`. You don't need to install Java, because the zip includes its own Java 21 runtime.

The exe is not code-signed, so Windows SmartScreen may show "Windows protected your PC". Choose **More info**, then **Run anyway**. To check the download first, compare its SHA-256 with the one in the release notes:

```powershell
Get-FileHash .\JdkTrustManager-0.1.0-windows-x64.zip -Algorithm SHA256
```

## What it does

**Installs JDKs from [Foojay](https://api.foojay.io/disco/v3.0).**
- Lists vendors and versions from the Disco API. Only GA, x64, portable `.zip` builds that are free for production use are shown.
- Downloads the zip, checks its SHA-256, unzips it safely, and places it at `C:\Java\<vendor>-<major>\`.
- A newer patch of the same vendor and major replaces that folder. If the new build fails its checks, the old one is restored.
- Never uses MSI or winget.
- Lists JDKs already on disk and uninstalls managed ones. Uninstall only deletes folders inside the configured root.

**Switches the active JDK.**
- Writes `JAVA_HOME` and a single `%JAVA_HOME%\bin` entry at the front of `PATH`, directly in the registry, and shows a preview first.
- Never calls `setx`, which cuts `PATH` off at about 1024 characters.
- Leaves other Java entries, such as Oracle's `javapath`, alone unless you ask.
- Copies a per-shell PowerShell snippet, a Maven toolchain snippet, and a Gradle toolchain note for any installed JDK.

**Syncs trusted certificates.**
- Keeps a shared certificate folder next to the JDKs at `C:\Java\trusted-certs\`. It holds downloaded HTTPS chains (TestNG by default) and a `local\` drop folder for your own PEM or CER files.
- Imports every checked certificate into every managed JDK's `cacerts`, using that JDK's own `keytool`, under `jkm-<sha256 prefix>` aliases.
- Never replaces or removes vendor aliases. Backs up `cacerts` before each write.
- Syncs at startup when the last sync is more than 24 hours old, and whenever you press Sync. Automatic sync can be paused.

## Layout

```text
C:\Java\
  trusted-certs\        shared certificates (sources.json, urls\, local\)
  temurin-21\           one JDK home per vendor and major
  zulu-21\
  liberica-fx-21\       JavaFX builds get an -fx suffix

%LOCALAPPDATA%\JdkTrustManager\
  installations.json    installed JDKs and their state
  settings.json         root folder, filters, sync options
  catalog.json          cached Foojay catalog
  cache\<sha256>.zip    downloaded zips, kept for reinstalls
  logs\                 activity log
```

The root folder is configurable. Creating `C:\Java` or writing system-wide variables needs a one-time elevated helper. Everything else runs as the current user.

## Screens

Catalog, Installed, Environment, Truststore, Trusted certificates, and Activity. Settings are in the **Options** menu. Downloads, unzips, `keytool` calls, and registry writes run off the UI thread on a single work queue, so two jobs never change `C:\Java` at the same time.

## Build and run

Requirements: Windows, a JDK 21 that includes `jmods` (for example Temurin 21), and Maven 3.9 or newer.

Run the tests:

```powershell
mvn -f app\pom.xml test
```

Start the app during development:

```powershell
mvn -f app\pom.xml javafx:run
```

Build a standalone app image with its own runtime:

```powershell
.\package-app.ps1
```

The script uses `JAVA_HOME` and `mvn` from `PATH` by default. Pass `-JavaHome <jdk>` or `-Maven <path to mvn.cmd>` to use other ones. The result is `app\target\dist\JdkTrustManager\`, with `JdkTrustManager.exe` inside, plus the release zip `app\target\JdkTrustManager-<version>-windows-x64.zip` and its SHA-256. Copy the whole folder, not just the exe. The bundled runtime means the app keeps working when the JDKs under `C:\Java` change.

One test installs a cached Temurin 21 zip. It is skipped until that zip is in `%LOCALAPPDATA%\JdkTrustManager\cache\`, which happens after you install Temurin 21.0.13+9 with the app once.

## Security notes

- Downloads are HTTPS only. A zip whose SHA-256 does not match the Disco API is deleted and not retried.
- Zip entries with absolute paths or `..` are rejected.
- Passwords are never written to JSON or logs. The default `cacerts` password is `changeit`.
- Files you put in `trusted-certs\local\` are trusted on purpose, so you are responsible for what goes there. PEM files that contain a private key are rejected.
- Running Java processes keep the truststore they loaded. Restart them after a sync.

## Out of scope for now

Linux and macOS, SDKMAN, Scoop, winget, JRE packages, native-image kits, early-access builds (behind a setting), editing private keys, and named trust profiles.

## License

[MIT](LICENSE). The release zip also contains a Java runtime built from Eclipse Temurin with OpenJFX linked in, both under GPLv2 with the Classpath Exception, and the Jackson libraries, under Apache-2.0. See [THIRD-PARTY-NOTICES.txt](THIRD-PARTY-NOTICES.txt).
