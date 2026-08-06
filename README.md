# Sugarlicious

Sugarlicious ist eine eigenständige, strikt lesende Android-/Wear-OS-Anwendung für lokal von
AndroidAPS bereitgestellte Statusdaten. Keine Therapiekommandos, keine Cloud,
keine Telemetrie und keine Werbung. Das Projekt steht unter GNU AGPL v3.

## Enthalten

- Mobile Bridge für den offiziellen lokalen AAPS-Status-Broadcast
- versioniertes, fehlertolerantes Datenmodell und Wear-Data-Layer-Protokoll
- DataStore-Persistenz und klare Kennzeichnung aktueller/verzögerter/veralteter Daten
- kachelbasiertes Smartphone-Dashboard mit echten AAPS-Graphdaten, flacher
  Navigation und direkt erreichbaren Anzeigeeinstellungen
- neutrale graue Tile-Oberfläche mit FreDiabetics-Grün als Systemakzent,
  pillenförmigen Menüs und ohne farbige Kartenumrandungen
- normale dauerhafte Hintergrund-Benachrichtigung sowie optionaler
  Android-16-/One-UI-8.5-Live-Status ohne Gesundheitswerte
- 27 frei auswählbare Complication-Provider
- 25 veröffentlichbare, codefreie WFF-v1-Watchface-Pakete plus technisches Testface
- zwei originale Sugarlicious-Watchfaces: Digital mit Graph-Tile und Analog mit
  eigenständig gezeichneten, schlanken Zeigern
- sichere CWF-Analyse, degradationsbewusster WFF-Generator und PNG-Vergleichswerkzeug
- CI, gepinnter offizieller WFF-Validator, No-DEX-Prüfung und DIY-Release-Skript

Der Datenfluss lautet:

`AndroidAPS External Companion Apps → Mobile Bridge → DataClient → Watch DataStore → Complications → WFF`

Der aktuelle AndroidAPS-Kontrollstand ist `dev` bei
`7fc8205e9a73259cec2982fc199f3d2055f84347`. Die reproduzierbare 0.3.0-
Release-Baseline bleibt `18101c8a2c0204a08d417f3d5fbac3e9ceae380f`.
Details und der geprüfte Delta-Einfluss stehen in `docs/SOURCE_BASELINE.md`.

Die Smartphone- und Wear-App tragen das FreDiabetics-Logo und dieselbe neutrale
Graustufen-Tile-Palette. Das Icon-Grün kennzeichnet Systemaktionen und aktive
Auswahlen; Datenserien behalten ihre semantischen Graphfarben. Die Dashboard-
Optik folgt der für das Projekt gelieferten Bildvorlage. Wo die
Vorlage technische Graphdetails offenlässt, werden die Darstellungsprinzipien
des lokal geprüften Nightscout Data Toolkits verwendet. GlucoDataHandler ist
ausschließlich eine Referenz für Provider-/Hintergrundlogik und keine
Designquelle.

## In Android Studio öffnen

In Android Studio **File → New → Project from Version Control** wählen und
folgende Repository-Adresse verwenden:

```text
https://github.com/FreDiabetics/aaps_wearable-suite.git
```

Als Zielordner einen neuen, leeren Ordner auswählen und anschließend die
Gradle-Synchronisierung abwarten. Alternativ kann das Projekt zuerst geklont
werden:

```powershell
git clone https://github.com/FreDiabetics/aaps_wearable-suite.git
```

## Bauen

Voraussetzungen sind JDK 21 und Android SDK 36.

```powershell
.\gradlew.bat test assembleDebug
pwsh -File tools\wff-validator\validate.ps1
pwsh -File tools\verify-codefree-watchfaces.ps1
pwsh -File tools\build-release.ps1
```

Das letzte Kommando erzeugt ein prüfbares DIY-Vorschaupaket unter `dist/` mit
SHA-256-Liste. Mobile-/Wear-APK und WFF-Pakete sind dort nur entwicklersigniert;
vor einer öffentlichen Store-/Release-Veröffentlichung ist ein eigener
signierter Release-Build erforderlich.

Installation und Nutzung: `docs/INSTALLATION.md`. Verifizierte Tests und offen
gebliebene Hardwarepunkte: `docs/TEST_REPORT.md` und
`docs/KNOWN_LIMITATIONS.md`.

PinkFloydTheWall ist wegen ungeklärter Rechte an Drittmotiven bewusst nicht
enthalten. Nichtkommerzielle Nutzung ersetzt keine erforderliche Erlaubnis.

Kontakt: `typ1.diafreddy@gmail.com` ·
[GitHub](https://github.com/FreDiabetics/aaps_wearable-suite)
