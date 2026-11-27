# Project facts agents keep getting wrong

Settled choices. Do not research them again and do not propose alternatives unless the user asks to change the spec.

- Windows only. Java 21, JavaFX 21, Maven. Package root `dev.jdktrustmanager`. Only the `ui` package may import JavaFX.
- JDKs come from the Foojay Disco API `https://api.foojay.io/disco/v3.0`, as zip files, into `C:\Java\<vendor>-<major>\`. `free_use_in_production` must be true. `amd64` and `x64` are the same. JavaFX uses a `-fx` suffix.
- The app owns `JAVA_HOME` and one `%JAVA_HOME%\bin` entry. Write the registry. Never `setx`.
- Shared certificates live in `C:\Java\trusted-certs\`. The only built-in source is the live certificate chain of `https://testng.org`. Users add more HTTPS URLs on the Trusted certificates screen. The Mozilla bundle is not a sync source, and `UrlCatalog` drops it from `sources.json`. Files dropped in `local\` are trusted on purpose. Private keys are rejected. Do not replace a vendor `cacerts`.
- Truststore writes go through that JDK's own `keytool.exe`.
- The app ships its own runtime. Do not uninstall or retarget the JDK that `JAVA_HOME` points at while it is the build JDK.
- Named trust profiles, Linux, macOS, winget, and JRE packages are out of this version.
- Maven, Ant, and Gradle live in `C:\Java\build-tools\<maven|ant|gradle>-<version>\`, one folder per exact version, never overwritten. The code is in `dev.jdktrustmanager.buildtools` (no JavaFX). Tool state is in `%LOCALAPPDATA%\JdkTrustManager\tools.json`.
- Maven and Ant downloads are checked with the release's PGP signature against Apache's KEYS file (Bouncy Castle). A bad signature or unknown signer is always rejected. Only when the keys or signature can't be fetched does it fall back to the strongest checksum, flagged "weak checksum" for SHA-1/MD5. Gradle is checked by the SHA-256 in `services.gradle.org/versions/all`.
- Tool variables: `MAVEN_HOME` + `M2_HOME`, `ANT_HOME`, `GRADLE_HOME`. PATH entries `%MAVEN_HOME%\bin`, `%ANT_HOME%\bin`, `%GRADLE_HOME%\bin` sit right after `%JAVA_HOME%\bin`. A JDK switch never removes them; a tool switch never touches `JAVA_HOME`. Write environment changes through `EnvironmentPlan` + `UserEnvironment.apply(plan)`.
- Next stages: a Profiles tab (named JDK + tool combinations applied in one write), then profile terminals. See roadmap.md.
- All verification is automated tests. Agents don't launch the app or run manual/live checks; the user does UI testing.
