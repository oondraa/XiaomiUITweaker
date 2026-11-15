# Stahne VSECHNY fotky a videa z pripojeneho iPhonu do jedne slozky na PC.
# Ty divne slozky (100APPLE, 202610_a...) projde sam a vysype vse na jedno misto.
#
# Pouziti:
#   1) Pripoj iPhone kabelem, ODEMKNI ho a na displeji tapni "Duverovat" (Trust).
#   2) V PowerShellu:  powershell -ExecutionPolicy Bypass -File .\iphone_fotky.ps1
#   3) Fotky najdes v:  Obrazky\iPhone fotky
#
# Spustit muzes opakovane, uz stazene soubory preskoci (porovnava cele jmeno vc. pripony).

param(
    [string]$Cil = (Join-Path ([Environment]::GetFolderPath('MyPictures')) 'iPhone fotky')
)

$shell = New-Object -ComObject Shell.Application
$tentoPc = $shell.NameSpace(17)

$iphone = $tentoPc.Items() | Where-Object { $_.Name -match 'iPhone|Apple' } | Select-Object -First 1
if (-not $iphone) {
    Write-Host "iPhone nevidim. Je pripojeny, odemceny a dal jsi 'Duverovat'?" -ForegroundColor Red
    Write-Host "Pripadne nainstaluj 'Apple Devices' z Microsoft Storu (prida ovladace)."
    exit 1
}
Write-Host "Nasel jsem: $($iphone.Name)"

$uloziste = $iphone.GetFolder.Items() | Select-Object -First 1
if (-not $uloziste) {
    Write-Host "iPhone je pripojeny, ale neukazuje uloziste. Odemkni ho a zkus to znovu." -ForegroundColor Red
    exit 1
}
$dcim = $uloziste.GetFolder.Items() | Where-Object { $_.Name -eq 'DCIM' } | Select-Object -First 1
if (-not $dcim) { $dcim = $uloziste }

New-Item -ItemType Directory -Force -Path $Cil | Out-Null
$cilNs = $shell.NameSpace($Cil)

# jmena (bez pripony), ktera uz mame
$mame = @{}
Get-ChildItem $Cil -File | ForEach-Object { $mame[$_.Name.ToLower()] = $true }

$nove = 0; $preskoceno = 0; $chyby = 0
foreach ($slozka in $dcim.GetFolder.Items()) {
    if (-not $slozka.IsFolder) { continue }
    $soubory = @($slozka.GetFolder.Items() | Where-Object { -not $_.IsFolder })
    Write-Host "Slozka $($slozka.Name): $($soubory.Count) souboru"
    foreach ($f in $soubory) {
        # Explorer schovava pripony -> plne jmeno ber z System.FileName
        # (Live Photo = IMG_1234.HEIC + IMG_1234.MOV, musi se stahnout oba)
        $zaklad = $f.ExtendedProperty('System.FileName')
        if (-not $zaklad) { $zaklad = $f.Name }
        if ($mame.ContainsKey($zaklad.ToLower())) { $preskoceno++; continue }

        $pred = (Get-ChildItem $Cil -File).Count
        $cilNs.CopyHere($f, 16)
        # cekej, az pribude soubor (max 3 min - videa jsou velka)
        $t = 0
        while ((Get-ChildItem $Cil -File).Count -le $pred -and $t -lt 1800) { Start-Sleep -Milliseconds 100; $t++ }
        if ($t -ge 1800) { Write-Host "  ! nepodarilo se: $($f.Name)" -ForegroundColor Yellow; $chyby++; continue }

        $mame[$zaklad.ToLower()] = $true
        $nove++
        if ($nove % 10 -eq 0) { Write-Host "  ...stazeno $nove" }
    }
}

Write-Host ""
Write-Host "Hotovo: $nove novych, $preskoceno uz jsi mel, $chyby chyb." -ForegroundColor Green
Write-Host "Slozka: $Cil"
Start-Process explorer.exe $Cil
