# Test Report

## Abschlusslauf 2026-08-03

Ausgeführt durch `tools/build-release.ps1`:

- `gradlew test assembleDebug`
- Mobile- und Wear-`assembleRelease`
- Release-Build für technisches Testface und 23 ausgelieferte WFF-Pakete
- offizieller Google-WFF-Validator auf Commit `702e9bdf…`
- APK-Inhaltsprüfung auf `classes*.dex`

Ergebnis: **erfolgreich**. Gradle meldete 2.156 Tasks, davon 1.195 ausgeführt
und 961 aktuell, in 4 Minuten 27 Sekunden. Zehn JUnit-XML-Suites enthielten
34 Tests: 0 Fehler, 0 Fehlschläge, 0 übersprungen.

Abgedeckt sind unter anderem Parsing bekannter/fehlender/unbekannter/falsch
typisierter AAPS-Felder, mg/dl und mmol/l, Trends, Deltaformatierung,
Zeitstempel/Future-Rejection/Frische, IOB/COB/Basal/Pumpe, Vertragserkennung,
Schema-1-zu-2-Migration, DataStore-Wiederherstellung, alle 27 Provider,
Ranged-Value-Ausgaben, stale-Sperren, CWF-Sicherheit, Generator-Degradation und
Screenshot-Differenzmetriken.

## WFF und APKs

- 24 von 24 `watchface.xml`: gültig gegen WFF v1
- 24 von 24 WFF-Release-APKs: kein DEX, damit codefrei
- alle WFF-Pakete: VersionName 0.3.0, VersionCode 3
- ausgeliefertes Paket: 23 Watchface-APKs, 2 App-APKs, Dokumente und Lizenzen
- `SHA256SUMS.txt`: 44 Einträge, 0 Abweichungen bei erneuter Prüfung
- Mobile- und Wear-Debug-APK fordern keine `INTERNET`-Berechtigung an
- Debug-Testdatenreceiver fehlt nachweislich im Wear-Release-Manifest
- App-/WFF-DIY-Artefakte verwenden denselben Android-Debug-Zertifikatsschlüssel;
  dies ist ausdrücklich keine Produktionssignatur

## Android-15-Telefontest

Auf dem API-35-Phone-Emulator wurde ein Payload mit den exakt belegten
AAPS-Broadcastfeldern an `info.nightscout.androidaps.status` gesendet. Der
Zustand wurde vor der Data-Layer-Operation persistiert. Ohne gekoppelte Uhr
endete die Übertragung nach dem gesetzten Timeout als `unavailable`, ohne ANR
oder Fatal Exception. Nach Force-Stop und Prozessneustart war der 991-Byte-
DataStore-Zustand weiterhin vorhanden und die Diagnoseansicht zeigte Vertrag,
Messzeit und Verbindungsstatus korrekt. Screenshots liegen unter
`docs/test-artifacts/android-15/`.

## Wear-OS-6-Emulatortest

Ziel: runder offizieller Wear-OS-6-Emulator, 454×454 Pixel.

- Wear-App und 23 Watchface-Pakete erfolgreich installiert
- ohne Zustand: `—`, `keine Daten`, null Telefonverbindungen, read-only sichtbar
- vollständiger synthetischer Zustand im Debug-Build gespeichert und von den
  Complication-Providern gelesen
- 23 aktive und 23 echte Doze/AOD-Screenshots aufgenommen
- vor jedem AOD-Bild `mWakefulness=Dozing` und `Display State=DOZE*` geprüft
- `capture-report.json` enthält 23 Zeilen; sämtliche PNG-Hashes stimmen
- automatisierte Sichtbarkeitsprüfung: kein aktives Bild unter 1 Prozent
  nicht-schwarzer Pixel; Minimum 3,1637 Prozent bei AIMICO
- AAPS V4 nach abgeschlossener asynchroner Providerbindung erneut aufgenommen;
  sichtbar sind Glukose, Trend, IOB, COB, Verlauf und Status

Goldens mit Daten liegen unter
`docs/test-artifacts/wear-os-6/watchfaces-data/`; die vorherigen No-Data-
Regressionsbilder bleiben getrennt erhalten.

## Nicht als bestanden behauptet

- echte Google-Play-Services-Paarung zwischen Telefon und Uhr; der offizielle
  Pairing Assistant erfordert interaktive Companion-App-/Play-Store-Einrichtung
- Bluetooth-Unterbrechung/Wiederverbindung und mehrere gekoppelte Uhren
- Samsung Galaxy Watch mit One UI Watch 8 sowie zweites Nicht-Samsung-Gerät
- pixelgleiche Original-vs.-Port-Aufnahmen auf identischer realer Hardware
- produktive Release-Signatur und Store-/öffentliche Release-Freigabe
- PinkFloydTheWall; rechtlich blockiert und nicht gebaut

Diese offenen Punkte verhindern die Behauptung, die Version-1-Abnahmekriterien
seien vollständig erfüllt. Der vorhandene Stand ist ein gebautes und breit
automatisiert/emuliert geprüftes DIY-Vorschaupaket.
