param(
    # A full JDK 21 with jmods. Defaults to JAVA_HOME.
    [string]$JavaHome = $env:JAVA_HOME,
    # Maven launcher. Defaults to mvn on PATH.
    [string]$Maven = 'mvn'
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'jmods'))) {
    throw 'Set JAVA_HOME, or pass -JavaHome, to a JDK 21 that contains a jmods folder.'
}
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;" + $env:Path
& $Maven -f app\pom.xml -DskipTests package dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=$PSScriptRoot\app\target\deps"
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
$deps = Join-Path $PSScriptRoot 'app\target\deps'
$javafx = Get-ChildItem $deps -Filter 'javafx-*-win.jar' | ForEach-Object { $_.FullName }
$modulePath = (@("$JavaHome\jmods") + $javafx) -join ';'
$runtime = Join-Path $PSScriptRoot 'app\target\runtime'
if (Test-Path $runtime) { Remove-Item $runtime -Recurse -Force }
& jlink --module-path $modulePath --add-modules java.base,java.desktop,java.logging,java.naming,java.net.http,java.xml,java.management,jdk.crypto.ec,jdk.unsupported,javafx.controls --output $runtime --strip-debug --no-header-files --no-man-pages
$inputDir = Join-Path $PSScriptRoot 'app\target\app-input'
if (Test-Path $inputDir) { Remove-Item $inputDir -Recurse -Force }
New-Item $inputDir -ItemType Directory | Out-Null
Copy-Item (Join-Path $PSScriptRoot 'app\target\jdk-trust-manager-0.1.0-SNAPSHOT.jar') $inputDir
Get-ChildItem $deps -Filter '*.jar' | Where-Object { $_.Name -notlike 'javafx-*' } | Copy-Item -Destination $inputDir
$dest = Join-Path $PSScriptRoot 'app\target\dist'
if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
& jpackage --type app-image --name JdkTrustManager --input $inputDir --main-jar jdk-trust-manager-0.1.0-SNAPSHOT.jar --main-class dev.jdktrustmanager.JdkTrustManagerApp --runtime-image $runtime --dest $dest
$bundled = Join-Path $dest 'JdkTrustManager\runtime\bin\java.exe'
if ((Resolve-Path $bundled).Path -eq (Join-Path $JavaHome 'bin\java.exe')) {
    throw 'Packaged runtime points at the build JDK'
}
Write-Output $bundled
& $bundled -version
