# Nahraje XiaomiUITweaker do telefonu a povoli vsechna opravneni pres ADB (bez proklikavani).
# Telefon: USB kabel + Ladeni USB + "Ladeni USB (Nastaveni zabezpeceni)" v Moznostech pro vyvojare.
#   powershell -ExecutionPolicy Bypass -File .\instalovat.ps1          (sestavi + nainstaluje)
#   powershell -ExecutionPolicy Bypass -File .\instalovat.ps1 -NoBuild

param([switch]$NoBuild)

$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$pkg = 'cz.miuitweak.ioshell'
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'

if (-not $NoBuild) {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
    & (Join-Path $PSScriptRoot 'gradlew.bat') -p $PSScriptRoot assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build selhal.' }
}

$dev = & $adb devices | Select-String -Pattern '\tdevice$'
if (-not $dev) {
    Write-Host 'Telefon nevidim. Pripoj ho kabelem a povol ladeni USB (na telefonu potvrd dialog).' -ForegroundColor Red
    exit 1
}

Write-Host '-> instaluji'
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Instalace selhala. Na MIUI zapni v Moznostech pro vyvojare "Instalovat pres USB".' -ForegroundColor Red
    exit 1
}

function sh($cmd) { & $adb shell $cmd 2>&1 | Out-Null }

# MIUI po instalaci chvili "kontroluje" appku a pritom resetuje opravneni -> pockat
Start-Sleep -Seconds 4
Write-Host '-> opravneni'
sh "cmd notification disallow_listener $pkg/$pkg.notif.NotifListener"
sh "appops set $pkg SYSTEM_ALERT_WINDOW allow"
sh "appops set $pkg WRITE_SETTINGS allow"
sh "pm grant $pkg android.permission.POST_NOTIFICATIONS"
sh "cmd notification allow_listener $pkg/$pkg.notif.NotifListener"
sh "cmd notification allow_dnd $pkg"
sh "dumpsys deviceidle whitelist +$pkg"
# iOS bannery (vypinani systemovych vyskakovacich notifikaci) + AirPods (Bluetooth sken)
sh "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
sh "pm grant $pkg android.permission.BLUETOOTH_SCAN"
sh "pm grant $pkg android.permission.BLUETOOTH_CONNECT"
sh "pm grant $pkg android.permission.ACCESS_FINE_LOCATION"
sh "pm grant $pkg android.permission.ACCESS_COARSE_LOCATION"
# MIUI vlastni opravneni: 10008 = autostart, 10020 = zobrazit na zamykaci obrazovce, 10021 = otevirat okna na pozadi
sh "appops set $pkg 10008 allow"
sh "appops set $pkg 10020 allow"
sh "appops set $pkg 10021 allow"

# sluzba pristupnosti (pridat k uz zapnutym, nic neprepsat)
$svc = "$pkg/$pkg.overlay.EdgeService"
$cur = (& $adb shell settings get secure enabled_accessibility_services).Trim()
$cur = $cur.Trim('"')
if ($cur -eq 'null' -or -not $cur) { $new = $svc }
elseif ($cur -notlike "*$svc*") { $new = "${cur}:$svc" }
else { $new = $cur }
# po aktualizaci se sluzba sama nepripoji -> nejdriv odebrat, pak znovu pridat (vynuti pripojeni)
$without = (($cur -split ':') | Where-Object { $_ -and $_ -ne 'null' -and $_ -ne $svc }) -join ':'
if (-not $without) { $without = '""' }
sh "settings put secure enabled_accessibility_services $without"
Start-Sleep -Seconds 1
sh "settings put secure enabled_accessibility_services $new"
sh "settings put secure accessibility_enabled 1"

Write-Host '-> spoustim'
sh "am start -n $pkg/.MainActivity"

Write-Host ''
Write-Host 'Hotovo. V appce zapni "Zamykacka zapnuta" a vyber fotku.' -ForegroundColor Green
Write-Host 'Rucne zbyva jen MIUI Autostart (tlacitko v appce) a pripadne Shizuku.'
