# Startet den lokalen Paper-26.2-Testserver mit dem MurderMystery-Plugin (frische Welt).
$root = Join-Path $env:LOCALAPPDATA "Temp\mmbuild"
Set-Location (Join-Path $root "server")
Remove-Item world, world_nether, world_the_end, logs, crash-reports -Recurse -Force -ErrorAction SilentlyContinue
$jdk = (Get-ChildItem (Join-Path $root "jdk25") -Directory)[0].FullName
& "$jdk\bin\java.exe" -Xmx2G -jar paper.jar --nogui
