param([string[]]$Tasks = @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug'))
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $env:JAVA_HOME -and (Test-Path '.tools/jdk-17.0.20.1+1')) {
    $env:JAVA_HOME = (Resolve-Path '.tools/jdk-17.0.20.1+1').Path
}
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $PWD '.tools/gradle-user-home' }
& .\gradlew.bat @Tasks --console plain
exit $LASTEXITCODE
