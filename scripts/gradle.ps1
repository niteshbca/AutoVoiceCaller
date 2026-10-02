$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$dist = Join-Path $project '.gradle-dist'
$gradle = Join-Path $dist 'gradle-8.13\bin\gradle.bat'
if (!(Test-Path $gradle)) {
    New-Item -ItemType Directory -Force -Path $dist | Out-Null
    $archive = Join-Path $dist 'gradle.zip'
    Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.13-bin.zip' -OutFile $archive
    $expected = (Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256').Content.Trim()
    if ((Get-FileHash $archive -Algorithm SHA256).Hash.ToLower() -ne $expected.ToLower()) { throw 'Gradle checksum mismatch' }
    Expand-Archive $archive -DestinationPath $dist -Force
    Remove-Item $archive
}
& $gradle '-p' $project @args
exit $LASTEXITCODE
