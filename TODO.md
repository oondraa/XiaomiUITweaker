# iOShell – stav a co zbývá (15. 11. 2026, večer)

## Hotovo
- Zamykačka:
  - tvoje fotka, nové fonty, předkreslená kopie (žádné blikání černé ani MIUI)
  - odjezd nahoru přes kopii, takže MIUI nepřehraje animaci „minimalizace“
  - PIN rovnou po přejetí (MIUI ho ukáže samo, automatické gesto bylo škodlivé, je pryč)
- Notifikace:
  - jen „Vymazat“, karta se zasekne v jedné poloze
  - fotka člověka jen u zpráv, jinak ikona appky
  - jméno odesílatele u IG DM
- Hudba: karta na zamykačce, velký obal po klepnutí; napojení přes notifikaci přehrávače (MIUI neposílá změny sezení)
- Ovládací centrum:
  - podle screenshotu z iPhonu, posunuté doprostřed, obal alba
  - dlaždice se vybírají a řadí v appce
  - bez Shizuku se zavře a otevře systémový panel
- iOS bannery (přepínač v appce): vypne systémové heads-up přes `heads_up_notifications_enabled` (WRITE_SECURE_SETTINGS dává instalační skript)
- AirPods: BLE sken (jen při rozsvíceném displeji), okno po otevření pouzdra, řádek baterie pod hodinami

## Ověřit u uživatele
- Animace odemčení otiskem (odjezd kopie)
- iOS bannery: zapnout v appce → přijde zpráva → banner shora, systémový se neukáže?
- AirPods Pro 3: neznámé ID modelu → zobrazí se jako „AirPods Pro“. Funguje dekódování baterie? (Pro 3 může mít jiný formát)

## Nápady na další kolo
- Widgety pod hodinami (počasí, kalendář), „skleněné“ hodiny jako iOS 26 (napady/my-ios-26-lockscreen…)
- Seznam Wi-Fi sítí přímo v CC (potřebuje polohu), jméno BT zařízení
- Úprava zamykačky přímo na zamykačce (podržet → editor jako iOS) místo v appce
- Smazat dočasný log `NotifParser` (Instagram) a `MediaRepo`
