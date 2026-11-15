# MIUI 14 (Redmi Note 10) - bezpecne ADB tweaky BEZ rootu.
# Pouziti:
#   1) Stahni Android platform-tools: https://developer.android.com/tools/releases/platform-tools
#      rozbal treba do C:\platform-tools a pridej do PATH (nebo spust skript odtamtud).
#   2) Na mobilu: Nastaveni > O telefonu > 7x tapnout na "Verze MIUI" -> vyvojarske moznosti.
#      Dalsi nastaveni > Moznosti pro vyvojare > Ladeni USB = ZAP (+ "Ladeni USB (Nastaveni zabezpeceni)" pro settings put).
#   3) .\adb_tweaks.ps1            -> provede tweaky
#      .\adb_tweaks.ps1 -Restore   -> vrati odinstalovane appky zpet
#
# "pm uninstall --user 0" appku NEMAZE ze systemu, jen ji skryje pro tvuj ucet.
# Vratit jde kdykoliv: adb shell cmd package install-existing <balicek>

param([switch]$Restore)

$bloat = @(
    "com.miui.msa.global",                 # reklamy v systemu (MSA)
    "com.miui.analytics",                  # telemetrie
    "com.miui.android.fashiongallery",     # Glance / Wallpaper carousel - smeti na zamykacce
    "com.xiaomi.glgm",                     # Games
    "com.miui.videoplayer",                # Mi Video
    "com.miui.player",                     # Mi Music (pokud pouzivas Spotify apod.)
    "com.xiaomi.mipicks",                  # GetApps (obchod s reklamami)
    "com.mi.globalminusscreen",            # App Vault (karta vlevo od plochy)
    "com.miui.bugreport",                  # Bug report
    "com.facebook.appmanager",
    "com.facebook.services",
    "com.facebook.system",
    "com.netflix.partner.activation",
    "com.amazon.appmanager"
)

adb devices
if ($Restore) {
    foreach ($p in $bloat) { adb shell cmd package install-existing $p }
    exit
}

foreach ($p in $bloat) {
    Write-Host "-> $p"
    adb shell pm uninstall -k --user 0 $p
}

# Svizneji pusobici animace (1.0 = default, 0.5 = rychlejsi, 0 = vypnuto)
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5

Write-Host "Hotovo. Restartuj telefon."
