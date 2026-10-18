param([switch]$UseEnvironmentProxy, [string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug'))
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$ezTemp = Join-Path $env:SystemDrive 'ezsmb-tmp'
New-Item -ItemType Directory -Force -Path $ezTemp | Out-Null
$ezOldJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$ezTemp"
    if ($UseEnvironmentProxy -and $env:HTTPS_PROXY) {
        $ezProxy = [Uri]$env:HTTPS_PROXY
        if ($ezProxy.UserInfo) { throw 'Do not put proxy credentials in JVM command-line options.' }
        $env:JAVA_TOOL_OPTIONS += " -Dhttps.proxyHost=$($ezProxy.Host) -Dhttps.proxyPort=$($ezProxy.Port) -Dhttp.proxyHost=$($ezProxy.Host) -Dhttp.proxyPort=$($ezProxy.Port) -Dhttp.nonProxyHosts=localhost|127.*"
    }
    & .\gradlew.bat --no-daemon --console=plain @Tasks
    exit $LASTEXITCODE
} finally { $env:JAVA_TOOL_OPTIONS = $ezOldJavaOptions }
