param(
    [Parameter(Mandatory = $true)][string]$Tests,
    [string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent),
    [string]$Task = ':examples:runClient',
    [string]$ReportDir,
    [int]$TimeoutSeconds = 600,
    [int]$ShutdownSeconds = 20,
    [string]$HorizonQaJar,
    [string[]]$GradleArguments = @(),
    [string]$LaunchId,
    [switch]$Worker
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
if (!$ReportDir) {
    $ReportDir = Join-Path $ProjectRoot ('build/client-tests/' + [Guid]::NewGuid().ToString('N'))
}
$ReportDir = [IO.Path]::GetFullPath($ReportDir)
if ($Worker) {
    $ErrorActionPreference = 'Continue'
    Set-Location -LiteralPath $ProjectRoot
    $clientGradleArguments = @($Task, '--console=plain', '--mcJvmArgs=-Dhorizonqa.mode=ci',
        '--mcJvmArgs=-Dhorizonqa.client=true', '--mcJvmArgs=-Dhorizonqa.world=normal', "--mcJvmArgs=-Dhorizonqa.tests=$Tests",
        "--mcJvmArgs=-Dhorizonqa.reportDir=$ReportDir", '--mcArgs=--width', '--mcArgs=1280',
        '--mcArgs=--height', '--mcArgs=720')
    $clientGradleArguments += @('--init-script', (Join-Path $PSScriptRoot 'client-test-isolation.init.gradle'),
        "-PhorizonQaClientTask=$Task", "-PhorizonQaClientReportDir=$ReportDir", "--mcJvmArgs=-Dhorizonqa.launchId=$LaunchId")
    if ($HorizonQaJar) { $clientGradleArguments += "-PhorizonQaJar=$HorizonQaJar" }
    $extraArguments = Get-Content -LiteralPath (Join-Path $ReportDir 'gradle-arguments.json') -Raw | ConvertFrom-Json
    $clientGradleArguments += @($extraArguments)
    & .\gradlew.bat @clientGradleArguments *> (Join-Path $ReportDir 'launch.log')
    exit $LASTEXITCODE
}

if (Test-Path -LiteralPath $ReportDir) { throw "Report directory must be new: $ReportDir" }
New-Item -ItemType Directory -Path $ReportDir | Out-Null
$LaunchId = [Guid]::NewGuid().ToString('N')
$processMarker = "-Dhorizonqa.launchId=$LaunchId"
ConvertTo-Json -InputObject $GradleArguments | Set-Content -LiteralPath (Join-Path $ReportDir 'gradle-arguments.json')
$arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"", '-Worker',
    '-Tests', "`"$Tests`"", '-ProjectRoot', "`"$ProjectRoot`"", '-Task', "`"$Task`"",
    '-ReportDir', "`"$ReportDir`"", '-LaunchId', $LaunchId)
if ($HorizonQaJar) { $arguments += @('-HorizonQaJar', "`"$HorizonQaJar`"") }
$shellExecutable = (Get-Process -Id $PID).Path
$workerProcess = Start-Process -FilePath $shellExecutable -ArgumentList $arguments -PassThru -WindowStyle Hidden
$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
$testProcess = $null
while (!$workerProcess.HasExited -and [DateTime]::UtcNow -lt $deadline) {
    $matches = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine.Contains($processMarker) -and
            $_.CommandLine -notmatch 'org\.gradle\.(wrapper|launcher)|gradle-wrapper\.jar' })
    if ($matches.Count -eq 1) { $testProcess = $matches[0] }
    Start-Sleep -Milliseconds 500
    $workerProcess.Refresh()
}
$timedOut = !$workerProcess.HasExited
$forcedTermination = $false
if ($timedOut) {
    New-Item -ItemType File -Path (Join-Path $ReportDir 'stop-client') | Out-Null
    if ($testProcess) {
        $jcmd = Join-Path (Split-Path $testProcess.ExecutablePath) 'jcmd.exe'
        if (Test-Path -LiteralPath $jcmd) {
            $dump = Start-Process -FilePath $jcmd -ArgumentList @($testProcess.ProcessId, 'Thread.print') -PassThru -WindowStyle Hidden `
                -RedirectStandardOutput (Join-Path $ReportDir 'threads.txt') -RedirectStandardError (Join-Path $ReportDir 'threads-error.txt')
            if (!$dump.WaitForExit(5000)) { Stop-Process -Id $dump.Id }
        }
    }
    if (!$workerProcess.WaitForExit($ShutdownSeconds * 1000) -and $testProcess) {
        $current = Get-CimInstance Win32_Process -Filter "ProcessId = $($testProcess.ProcessId)"
        if ($current -and $current.CreationDate -eq $testProcess.CreationDate -and
            $current.CommandLine.Contains($processMarker)) {
            Stop-Process -Id $testProcess.ProcessId
            $forcedTermination = $true
        }
    }
}
$statusPath = Join-Path $ReportDir 'horizonqa-result.json'
$exitCode = 2
if (!$timedOut -and (Test-Path -LiteralPath $statusPath)) {
    $status = Get-Content -LiteralPath $statusPath -Raw | ConvertFrom-Json
    if ($null -ne $status.exitCode) {
        $exitCode = [int]$status.exitCode
        if ($exitCode -eq 0 -and $workerProcess.ExitCode -ne 0) { $exitCode = 2 }
    }
}
$summary = [ordered]@{ exitCode = $exitCode; timedOut = $timedOut; forcedTermination = $forcedTermination; reportDir = $ReportDir
    gameDir = (Join-Path $ReportDir 'game'); launchId = $LaunchId
    statusFile = $statusPath; launchLog = (Join-Path $ReportDir 'launch.log') }
$summary | ConvertTo-Json | Tee-Object -FilePath (Join-Path $ReportDir 'launch-result.json')
exit $exitCode
