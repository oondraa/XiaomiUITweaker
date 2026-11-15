# XiaomiUITweaker

Zamykačka, notifikace a ovládací centrum ve stylu iPhonu pro Xiaomi / MIUI 14 (testováno na Redmi Note 10S, Android 13) – bez rootu.

## Co umí
- **Zamykačka** přes systémovou zamykačku MIUI (PIN/otisk zůstává): vlastní fotka, hodiny (9 fontů, tloušťka, barva,
  průhlednost, velikost), 2 tlačítka dole (svítilna/fotoaparát), přejetí nahoru = odemknout, doleva = fotoaparát.
- **Notifikace jako iOS**: Počet / Balíček / Seznam (i dvěma prsty), skupiny po aplikacích a konverzacích,
  přejetí doleva = Vymazat, podržení = náhled + Odpovědět, historie (Centrum oznámení).
- **Přehrávač** na zamykačce (obal alba, posuvník, ovládání) + velký obal po klepnutí.
- **Stažení shora** (služba přístupnosti): vlevo Centrum oznámení, vpravo Ovládací centrum (dlaždice se vybírají v appce).
- **iOS bannery** místo systémových vyskakovacích notifikací.
- **AirPods**: baterie L/P/pouzdro (z Bluetooth vysílání) + okno po otevření pouzdra, řádek baterie na zamykačce.
- Wi-Fi/BT/data jedním klepnutím přes [Shizuku](https://shizuku.rikka.app/), jinak se otevře systémový panel.

## Instalace
Telefon kabelem s laděním USB (+ „Instalovat přes USB“ a „Ladění USB (Nastavení zabezpečení)“ na MIUI), pak:

```powershell
powershell -ExecutionPolicy Bypass -File ioshell\instalovat.ps1
```

Skript appku sestaví, nainstaluje a přes ADB povolí všechna oprávnění (překrytí, notifikace, přístupnost, MIUI autostart…).

## Další soubory
- `IOS_SPEC.md` – popis iOS mechanik a rozměrů, podle kterých je UI postavené
- `NAVOD.md` – úpravy MIUI bez appky (motiv, ADB debloat), `adb_tweaks.ps1`
- `TODO.md` – co zbývá

Není to oficiální produkt Applu ani Xiaomi; žádné Apple fonty ani ikony nejsou použité (viz `FONTS.md`).
