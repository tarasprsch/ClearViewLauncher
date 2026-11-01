$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if ((Test-Path 'keystore.properties') -or (Test-Path '.signing/personal.jks')) {
    throw 'A signing configuration or key already exists. Reuse it for updates; do not replace it.'
}
if (-not $env:JAVA_HOME -and (Test-Path '.tools/jdk-17.0.20.1+1')) {
    $env:JAVA_HOME = (Resolve-Path '.tools/jdk-17.0.20.1+1').Path
}
$keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
New-Item -ItemType Directory -Force '.signing' | Out-Null
$random = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $rng.GetBytes($random) } finally { $rng.Dispose() }
$signingPassword = [Convert]::ToBase64String($random)
$env:TVLAUNCHER_SIGN_PASSWORD = $signingPassword
try {
    & $keytool -genkeypair -keystore '.signing/personal.jks' -storetype JKS -alias personal -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Clear View Launcher' -storepass:env TVLAUNCHER_SIGN_PASSWORD -keypass:env TVLAUNCHER_SIGN_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
    @(
        'storeFile=.signing/personal.jks'
        "storePassword=$signingPassword"
        'keyAlias=personal'
        "keyPassword=$signingPassword"
    ) | Set-Content -LiteralPath 'keystore.properties' -Encoding ASCII
} finally {
    Remove-Item Env:TVLAUNCHER_SIGN_PASSWORD -ErrorAction SilentlyContinue
}
Write-Output 'Signing key created. Back up .signing/personal.jks and keystore.properties securely together; updates require this key.'
