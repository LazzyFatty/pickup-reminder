param([string]$SmsFile)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
Push-Location $project
try {
    $javac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { 'javac' }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    $output = Join-Path $project '.tooling/parser-test'
    New-Item -ItemType Directory -Force $output | Out-Null
    & $javac -encoding UTF-8 -d $output 'app/src/main/java/ren/haom/pikeupcode/Parcel.java' 'app/src/main/java/ren/haom/pikeupcode/SmsParser.java' 'app/src/test/java/ren/haom/pikeupcode/SmsParserCheck.java'
    if ($LASTEXITCODE -ne 0) { throw 'Parser compilation failed' }
    $arguments = @('-Dfile.encoding=UTF-8', '-cp', $output, 'ren.haom.pikeupcode.SmsParserCheck')
    if ($SmsFile) { $arguments += $SmsFile }
    & $java @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Parser checks failed' }
} finally { Pop-Location }
