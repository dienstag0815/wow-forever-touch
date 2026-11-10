# Baut libgamenative_dns_v4mapped.so (arm64-v8a) mit dem NDK-clang, ohne Gradle.
# Die Flags sind so gewaehlt, dass die Datei zur bisherigen .so passt (NDK r27d, API 26 = minSdk,
# NEEDED libdl.so + libc.so, 16-KB-Seitenausrichtung, BIND_NOW, ohne SONAME und Build-ID).
# Aufruf:  powershell -File tools\build_dns_shim.ps1 [-Ndk <Pfad>] [-Api 26] [-NoInstall]
# Danach: Symbole vergleichen mit  llvm-nm -D  (muss freeaddrinfo, getaddrinfo, gethostname, res_query exportieren).
param(
    [string]$Ndk = $env:ANDROID_NDK_HOME,
    [int]$Api = 26,
    [switch]$NoInstall
)

$ErrorActionPreference = 'Stop'
if (-not $Ndk) { throw 'NDK-Ordner fehlt: -Ndk <Pfad> angeben oder ANDROID_NDK_HOME setzen (NDK r27d)' }
$repo = Split-Path -Parent $PSScriptRoot
$bin = Join-Path $Ndk 'toolchains\llvm\prebuilt\windows-x86_64\bin'
$clang = Join-Path $bin "aarch64-linux-android$Api-clang.cmd"
$src = Join-Path $repo 'app\src\main\cpp\gamenative_dns_v4mapped.c'
$dstDir = Join-Path $repo 'app\src\main\jniLibs\arm64-v8a'
$dst = Join-Path $dstDir 'libgamenative_dns_v4mapped.so'
$out = Join-Path ([System.IO.Path]::GetTempPath()) 'libgamenative_dns_v4mapped.so'

if (-not (Test-Path $clang)) { throw "clang nicht gefunden: $clang" }

$flags = @(
    '-shared', '-fPIC', '-O2', '-Wall', '-Wextra',
    '-Wl,-z,max-page-size=16384', '-Wl,-z,common-page-size=16384',
    '-Wl,-z,now', '-Wl,-z,relro', '-Wl,--build-id=none', '-Wl,--hash-style=gnu',
    '-o', $out, $src, '-ldl'
)
& $clang @flags
if ($LASTEXITCODE -ne 0) { throw "clang fehlgeschlagen ($LASTEXITCODE)" }

& (Join-Path $bin 'llvm-nm.exe') -D $out | Select-String ' T '
if (-not $NoInstall) {
    Copy-Item $out $dst -Force
    Write-Host "installiert: $dst"
}
