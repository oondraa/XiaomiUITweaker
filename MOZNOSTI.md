# Předělání zamykačky a ovládacího centra – všechny cesty

Telefon: **Redmi Note 10S (rosemary, M2101K7BNY)**, MIUI Global 14.0.11 (EEA), Android 13.
Cíl: vlastní vzhled zamykací obrazovky a ovládacího centra (Wi-Fi, BT, jas…).

## Jak to v MIUI funguje

Motiv `.mtz` je zip. Vedle tapet obsahuje **moduly pojmenované podle balíčku**, každý je zase zip:

| Modul v .mtz | Co mění | Jak |
|---|---|---|
| `lockscreen` | celá zamykačka | MAML: `advance/manifest.xml` + obrázky. Hodiny, datum, rozložení, animace, vlastní tlačítka. Volnost je skoro úplná, je to vlastně malý skriptovací engine. |
| `com.android.systemui` | stavový řádek, oznámení, část panelu | přepsání resources: `res/drawable-xxhdpi/*.png` (i .9.png) se stejným jménem jako v originálu, barvy a rozměry v `theme_values.xml` |
| `miui.systemui.plugin` | **nové ovládací centrum** v MIUI 14 (dlaždice, Wi-Fi/BT velké tlačítka, posuvník jasu) | stejně: přepsání drawables, barev a rozměrů podle jména |
| `framework-res`, `framework-miui-res` | systémové barvy, dialogy | stejně |

Motiv ale **nemůže měnit rozložení** ovládacího centra (kód a layouty). Mění jen to, co tam je: barvy, tvary, ikony, průhlednost, zaoblení, velikosti. Zamykačka (MAML) je výjimka, tu jde postavit celou od nuly.

Jména resources, která jde přepsat, se zjistí tak, že se z telefonu přes ADB stáhne originální APK
(`adb shell pm path miui.systemui.plugin`, `adb pull …`) a rozbalí se `apktool d`. Druhá možnost je rozbalit
existující motiv, který ovládací centrum mění (např. „Clay ControlCenter“), a podívat se, co přepisuje.

## Cesty od nejbezpečnější po nejtvrdší

### A) Vlastní .mtz motiv (bez rootu)
- Uděláme: lockscreen (MAML, základ už je v `variants/graphite_manifest.xml`) + `miui.systemui.plugin` + `com.android.systemui`.
- **Problém Global ROM:** appka Motivy nemá import a cizí motiv po 30–60 s vrátí. Vývojář xiaomi.eu (11/2024): na Global ROM .mtz nainstalovat nejde.
- Obezličky: MTZ Tester / MIUI Theme Editor (Play) na aplikaci + Zyper na blokování resetu. Komunitní, nespolehlivé, po aktualizaci appky Motivy se rozbijí.
- Plus: žádné odemykání, vždy vratné (stačí nastavit výchozí motiv).

### B) Barvy přes Shizuku / ADB „fabricated overlays“ (bez rootu)
- Android 12+ umí z ADB/Shizuku přepsat **jen čísla**: barvy, rozměry, booleany v systemui. Žádné obrázky ani layout.
- Na MIUI 14 není ověřené, jestli to systemui plugin respektuje. Jsou hlášky, že to přestalo fungovat.
- Ověřit to stojí 10 minut: `adb shell cmd overlay fabricate …`.

### C) Vlastní APK (bez rootu)
- **Zamykačka:** appka nemůže nahradit zabezpečenou zamykačku. Jde jen „vrstva nad ní“ (lock screen apps typu „Notification/AOD overlay“), na MIUI je zabíjí správa baterie. Pro hodiny/widget vzhled to je OK, pro opravdovou zamykačku ne.
- **Ovládací centrum:** appka s AccessibilityService, která místo MIUI panelu vykreslí vlastní (takhle fungují Power Shade, Mi Control Center). Vzhled 100 % podle nás. Ale: Wi-Fi od Androidu 10 appka **nepřepne přímo**, jen otevře systémový mini-panel. Data a některé přepínače taky nejdou. Na MIUI potřeba vypnout optimalizaci baterie a povolit autostart.

### D) Root + LSPosed (odemčený bootloader)
- Mi Unlock (čeká se 168 h), Magisk, LSPosed. Smaže telefon, bankovky mohou zlobit (Play Integrity).
- Pak jde: vlastní .mtz bez resetu (resp. modul na vypnutí kontroly motivů), modded `miui.systemui.plugin` (Mods-Center: AOSP-styl centra, Monet barvy), HyperCeiler (pro MIUI 14 / Android 13 jen starší verze 2.5.156), vlastní RRO overlaye i na layout.
- Tady jde udělat skutečně cokoliv.

### E) Custom ROM pro rosemary
- Pozor: Note 10S = **rosemary** (MediaTek G95), ne mojito/sunny. Hledat buildy pro rosemary/secret/maltose.
- Čistý Android, žádná MIUI omezení, vzhled se řeší jinak (AOSP systemui, Monet, ROM nastavení).

## Doporučení
1. **Postavit motiv (A)**: lockscreen + ovládací centrum. Práce na něm je stejná pro A i D, takže se nic nezahodí.
2. Zkusit ho na Global nasadit (MTZ Tester + Zyper). Když drží, hotovo.
3. Když ne: rozhodnutí mezi C (vlastní APK panel, bez rootu, ale s omezeními) a D (root, motiv pak funguje naplno).

Zdroje: xiaomi.eu thread „Help on installing a custom mtz theme … MIUI 14, Global“ (2024),
github.com/Mods-Center/Miui_Plugin, HyperCeiler README (ReChronoRain), Zyper (Play Store),
XDA k Fabricated Overlay API / Repainter.
