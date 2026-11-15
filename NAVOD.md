# MIUITWEAK – Redmi Note 10, MIUI 14

## 1. Theme „Graphite“

`python build.py` → `dist/Graphite.mtz` (+ `dist/preview_lock.png` jako náhled, tapety zvlášť v `dist/`).

Úpravy vzhledu: `theme_src/lockscreen/advance/manifest.xml` (pozice, velikosti, barvy jsou okomentované) a paleta tapet nahoře v `build.py`.

### Jak to dostat do mobilu
Globální MIUI **nemá v appce Motivy tlačítko „Importovat“** a cizí .mtz motivy po 30–60 s vrací zpátky. Zkoušej v tomto pořadí:

1. **Region Indie:** Nastavení > Další nastavení > Region > Indie, restart → Motivy > Profil – u některých buildů se pak import objeví.
2. **Klonovaná appka Motivy** (návody „enable import option MTZ Xiaomi global“): appka se naklonuje s povolenou instalací, z klonu pak jde .mtz importovat. Nefunguje to na všech buildech.
3. **Zyper** (Play Store) má zabránit tomu, aby MIUI motiv vrátilo. Je to aplikace třetí strany a podle recenzí občas přestane fungovat.
4. **Fallback bez hacků:** nastav jen tapety z `dist/` a zamykačku vyčisti ručně (sekce 2). Výsledek je v klidu na 80 %.

> Lock screen jsem zatím nezkoušel na reálném zařízení. Kdyby po aplikaci zůstal prázdný nebo nešel odemknout, otevři `manifest.xml` a jako první změň `fontFamily` na `"miui"` (font) a `uiVersion` v `build.py`. Zpátky se dostaneš vždycky tak, že v Motivech nastavíš výchozí motiv.

## 2. Nastavení v MIUI 14 (bez PC)

**Zamykací obrazovka – úklid**
- Nastavení > Always-on display a zamykací obrazovka: vypni **Glance / Wallpaper carousel** (je to ten bordel s reklamními tapetami na zamykačce).
- Tamtéž: AOD → nejjednodušší styl hodin, zapínání „po klepnutí“ (šetří baterii).
- Zamykací obrazovka > vypni „Probudit při oznámení“, pokud tě to ruší.

**Reklamy a smetí**
- Nastavení > Hesla a zabezpečení > Autorizace a odvolání → odvolat **msa**.
- Nastavení > Hesla a zabezpečení > Soukromí > Reklamní služby → vypnout personalizované reklamy.
- V appkách Zabezpečení, Čistič, Správce souborů, Motivy a Stahování: v nastavení vypni „Doporučení“ / „Získat doporučení“.

**Plocha a UI**
- Nastavení > Oznámení a ovládací centrum → **Styl ovládacího centra: nový** (oddělí oznámení od přepínačů a vypadá líp).
- Stavový řádek: nech zobrazovat jen nutné ikony (rychlost sítě vypnout, baterie jako ikona bez %, procenta máš na zamykačce).
- Plocha: Nastavení > Plocha → schovat názvy aplikací, mřížka 5×6, vypnout **App vault** (karta vlevo).
- Ikony: Motivy > Profil > Komponenty → ikony z jiného motivu. Případně launcher s podporou icon packů (např. Lawnchair, Nova). Gesta na celou obrazovku můžou s cizím launcherem na MIUI zlobit, takže je po přepnutí vyzkoušej.
- Font: ve výchozím stavu je MiSans, ten je v pohodě a stačí ho nechat. Vlastní fonty MIUI na globálu taky resetuje.

**Vývojářské možnosti** (7× klepni na Verze MIUI)
- Měřítko animací 0.5× = UI působí výrazně rychleji.
- „Vypnout optimalizace MIUI“ **nedoporučuju**, na MIUI 14 to rozbíjí oprávnění.

## 3. Přes PC (ADB, bez rootu)
`adb_tweaks.ps1` (u sebe) – debloat (reklamy, telemetrie, Glance, Facebook služby…) + animace 0.5×. Vratné přes `-Restore`. Před spuštěním si ten seznam projdi a co používáš (Mi Music, Mi Video), smaž z něj.

## 4. „Brutus předělávka“ – custom ROM
Jestli tě MIUI pořád bude štvát, tak skutečné řešení je **odemknout bootloader** (Mi Unlock, čeká se 168 h) a flashnout ROM pro **rosemary** (Note 10S, MediaTek; pozor, ne mojito/sunny = obyčejný Note 10). Komunitní buildy hledej pro rosemary/secret/maltose. Dostaneš čistý Android bez reklam a MIUI theme omezení. Mínusy: smaže se celý telefon, bankovní appky a Google Wallet můžou blbnout (Play Integrity) a je to práce na večer. Pro záložní telefon to ale klidně stojí za to.

## 5. iOShell – vlastní appka (zamykačka, notifikace, ovládací centrum jako iPhone)
Motiv tohle neumí, proto je to appka ve složce `ioshell/` (Kotlin + Compose). Specifikace vzhledu a gest je v `IOS_SPEC.md`.

- **Zamykačka:** tvoje fotka, hodiny (font, tloušťka, barva, průhlednost, velikost), 2 tlačítka dole (dlouhý stisk).
  Zespodu nahoru = odemknout, doleva = foťák, podržet prázdné místo = nastavení. Systémová zamykačka MIUI (PIN/otisk) zůstává pod ní.
- **Notifikace jako iOS:** Počet / Balíček / Seznam (i stažením dvěma prsty), skupiny, doleva = Možnosti/Vymazat,
  podržet = náhled + Odpovědět. Po odemčení jdou do historie, nahoru uprostřed = Centrum oznámení.
- **Stažení shora (odemčený telefon):** vlevo/uprostřed = Centrum oznámení, vpravo = Ovládací centrum.
- **Wi-Fi/BT/data jedním klepnutím** jen se Shizuku (Play Store), jinak se otevře systémový panel.

Instalace: telefon kabelem s laděním USB, pak `powershell -ExecutionPolicy Bypass -File ioshell\instalovat.ps1`.
Skript appku sestaví, nahraje a povolí oprávnění přes ADB. Ručně zbývá MIUI Autostart (tlačítko v appce) a Shizuku.
