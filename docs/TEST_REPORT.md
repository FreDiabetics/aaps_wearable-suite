# Test Report

## Reale Samsung-Telefon-/Uhr-Prüfung 2026-08-05

- Smartphone: Samsung SM-S948B
- Uhr: Samsung SM-L705F, Android 16/API 36,
  `ro.build.version.oneui=80000` (One UI Watch 8)
- AndroidAPS auf Telefon und Uhr: `4.0.0-dev-b`, VersionCode 1500
- eigene Mobile-/Wear-App: `app.aapswear`; Mobile-Patch 0.3.1/Code 4
- offizielles AAPS-V4-WFF und eigenes AAPS-V4-WFF gleichzeitig installiert

Die mobile Bridge empfing einen gültigen
`info.nightscout.androidaps.status`-Broadcast, erkannte
`AAPS_EXTENDED_STATUS_V1`, meldete eine erreichbare Uhr und schloss den
Data-Layer-Versand mit `ok` ab. Die DataStore-Dateien auf Telefon und Uhr wurden
in derselben Minute aktualisiert. Beim erneuten Auslesen zeigte die Mobile-UI
AAPS-Version, Vertrag, Empfangs-/Messzeit, eine Uhr und `übertragen`.

Die zunächst beobachtete scheinbar leere Mobile-App war ein Anzeigeproblem:
Eine bereits sichtbare Activity beobachtete keine späteren Änderungen an den
Diagnose-SharedPreferences. Version 0.3.1 registriert den Listener nur im
sichtbaren Lifecycle. `MainActivityTest` reproduziert und prüft diesen Ablauf.
Gezielter Testlauf und Mobile-Debug-Build waren erfolgreich; das APK wurde als
Update auf dem realen Smartphone installiert und als 0.3.1/Code 4 bestätigt.

Dass offizielles und eigenes AAPS-V4-WFF dieselben Therapiewerte zeigen, ist
erwartet: Beide bilden dieselbe AAPS-V4-Oberfläche nach, verwenden aber ihre
jeweiligen Complication-Provider. Die übereinstimmenden aktuellen Werte passen
zum nachgewiesenen erfolgreichen Bridge-/Data-Layer-Zustand.

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

- gezielte Bluetooth-Unterbrechung/Wiederverbindung und mehrere gekoppelte Uhren
- zweites reales Wear-OS-Gerät ohne Samsung-Oberfläche
- pixelgleiche Original-vs.-Port-Aufnahmen auf identischer realer Hardware
- produktive Release-Signatur und Store-/öffentliche Release-Freigabe
- PinkFloydTheWall; rechtlich blockiert und nicht gebaut

Diese offenen Punkte verhindern die Behauptung, die Version-1-Abnahmekriterien
seien vollständig erfüllt. Der vorhandene Stand ist ein gebautes und breit
automatisiert/emuliert geprüftes DIY-Vorschaupaket.

## Abschlusslauf 2026-08-05

Nach dem Realgeräte-Fix wurde `tools/build-release.ps1` ohne übersprungene
Buildphase erneut ausgeführt. Ergebnis: **erfolgreich**.

- 2.163 Gradle-Tasks: 24 ausgeführt, 2.139 aktuell
- 11 JUnit-XML-Suites: 35 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen
- 24 von 24 `watchface.xml`: gültig mit dem auf Commit `702e9bdf…`
  gepinnten offiziellen Google-WFF-Validator
- 24 von 24 WFF-Release-APKs: ohne `classes*.dex`
- Mobile-Debug-APK 0.3.1/Code 4 zuvor erfolgreich auf SM-S948B installiert;
  vorhandener AAPS-Empfang und Data-Layer-Zustand blieben nach dem Update erhalten
- Paket: `aaps-wear-watchfaces-0.3.1-diy-preview.zip`; die daneben erzeugte
  `.sha256`-Datei enthält die Prüfsumme des vollständigen ZIP-Archivs

Der erste Versuch deckte einen unvollständigen temporären Git-Cache des
WFF-Validators auf. Nach der eng begrenzten Cache-Reparatur bestand sowohl der
isolierte Validatorlauf als auch der vollständige Release-Lauf. Die öffentliche
AndroidAPS-Dev-Referenz blieb währenddessen unverändert auf
`e1068e77db4f801c046340c8313cd7a2856f4e7c`.
