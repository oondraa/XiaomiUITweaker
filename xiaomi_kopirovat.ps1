# Nahraje pripravene fotky (Obrazky\Pro Xiaomi) do Xiaomi -> DCIM\iPhone
# Na telefonu: pripojit kabelem -> v notifikaci "Nabijeni pres USB" zvolit "Prenos souboru".
param(
    [string]$Zdroj = (Join-Path ([Environment]::GetFolderPath('MyPictures')) 'Pro Xiaomi'),
    [string]$Album = 'iPhone'
)

$shell = New-Object -ComObject Shell.Application
$tel = $shell.NameSpace(17).Items() | Where-Object { $_.Name -match 'Redmi|Xiaomi|POCO|Note|M2101' } | Select-Object -First 1
if (-not $tel) {
    Write-Host "Xiaomi nevidim. Pripoj ho a v notifikaci USB zvol 'Prenos souboru'." -ForegroundColor Red
    Write-Host "Zarizeni, ktera vidim:"; $shell.NameSpace(17).Items() | ForEach-Object { "  - $($_.Name)" }
    exit 1
}
Write-Host "Nasel jsem: $($tel.Name)"
$uloziste = $tel.GetFolder.Items() | Select-Object -First 1
if (-not $uloziste) { Write-Host "Telefon neukazuje uloziste - odemkni ho a zvol 'Prenos souboru'." -ForegroundColor Red; exit 1 }
$dcim = $uloziste.GetFolder.Items() | Where-Object { $_.Name -eq 'DCIM' } | Select-Object -First 1

function Get-OrMake($parentItem, $name) {
    $f = $parentItem.GetFolder.Items() | Where-Object { $_.Name -eq $name } | Select-Object -First 1
    if (-not $f) {
        $parentItem.GetFolder.NewFolder($name)
        Start-Sleep 2
        $f = $parentItem.GetFolder.Items() | Where-Object { $_.Name -eq $name } | Select-Object -First 1
    }
    return $f
}

function Copy-Missing($srcDir, $destItem, $label) {
    $mam = @{}
    foreach ($i in $destItem.GetFolder.Items()) { $mam[$i.Name.ToLower()] = $true }
    $soubory = Get-ChildItem $srcDir -File -Force
    $chybi = $soubory | Where-Object { -not $mam.ContainsKey($_.Name.ToLower()) -and -not $mam.ContainsKey($_.BaseName.ToLower()) }
    Write-Host "$label : $($soubory.Count) souboru, na telefonu chybi $($chybi.Count)"
    $n = 0
    foreach ($f in $chybi) {
        $pred = $destItem.GetFolder.Items().Count
        $destItem.GetFolder.CopyHere($f.FullName, 16)
        $t = 0
        while ($destItem.GetFolder.Items().Count -le $pred -and $t -lt 1800) { Start-Sleep -Milliseconds 100; $t++ }
        $n++
        if ($n % 25 -eq 0) { Write-Host "  ...$n / $($chybi.Count)" }
    }
}

$cilAlbum = Get-OrMake $dcim $Album
Copy-Missing $Zdroj $cilAlbum "Fotky a videa"

# Live Photo videa -> Pictures\iPhone Live (s .nomedia, Galerie je neukaze)
$live = Join-Path $Zdroj 'Live Photo videa'
if (Test-Path $live) {
    $pics = $uloziste.GetFolder.Items() | Where-Object { $_.Name -eq 'Pictures' } | Select-Object -First 1
    $cilLive = Get-OrMake $pics 'iPhone Live'
    Copy-Missing $live $cilLive "Live Photo videa"
}

$naTel = $cilAlbum.GetFolder.Items().Count
Write-Host ""
Write-Host "Hotovo. V DCIM\$Album je ted $naTel souboru (ocekavano $((Get-ChildItem $Zdroj -File).Count))." -ForegroundColor Green
