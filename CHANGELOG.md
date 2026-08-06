# Changelog

## 0.5.0 - 2026-08-06

- Sichtbaren Produktnamen systemweit auf **Sugarlicious** umgestellt; stabile
  Application-ID zur Update- und Complication-Kompatibilität beibehalten.
- FreDiabetics-Logo als Material-Adaptive-Icon, Smartphone-Header,
  Wear-App-Branding und App-Info integriert; Kontaktmail und GitHub direkt in
  der Einstellungs-Kachel verlinkt.
- Wear-App als runde, scrollbare Tile-Oberfläche in der festen Smartphone-
  Palette umgesetzt, einschließlich sicherer Frischelogik, Verbindung und
  Read-only-Hinweis.
- `Sugarlicious Digital` und `Sugarlicious Analog` als getrennte codefreie
  WFF-v1-Pakete mit je acht Slots und AOD erstellt. Die analogen Zeiger sind
  eigene Originalgrafiken; keine Apple-Ressource wurde verwendet.
- Emulator-only Testdatenempfänger im Debug-Quellset und reproduzierbare
  Wear-/Watchface-Goldens ergänzt; Release-APK enthält den Empfänger nicht.

## 0.4.0 - 2026-08-05

- Smartphone-App als flaches, kachelbasiertes Dashboard nach der vorgegebenen
  Bildvorlage umgesetzt: Übersicht, Verlauf, Daten und Inline-Einstellungen.
- Feste Bild-Palette und Nightscout-Data-Toolkit-Graphprinzipien verwendet;
  keine von GlucoDataHandler übernommene Optik und keine grenzwertabhängige
  Farbwahl für den Glukosewert.
- Reale AAPS-`predBGs` aus Suggested/Enacted fehlertolerant normalisiert; keine
  eigenen oder erfundenen Prognosewerte.
- Lokal empfangene Glukose-, IOB-, COB- und Basal-Anzeigepunkte auf höchstens
  24 Stunden beziehungsweise 300 Punkte begrenzt und im letzten Zustand
  gespeichert.
- Datenmodell und Wear-Protokoll auf Schema 3 erweitert; Mobile- und Wear-App
  auf 0.4.0/Code 5 angehoben.
- Canvas-Regressionstests für Glukose-, Prognose-, IOB- und COB-Graphen sowie
  Tests für Verlaufspuffer, Prognoseparser und Inline-Einstellungen ergänzt.

## 0.1.0 - 2026-08-02

- AndroidAPS- und GlucoDataHandler-Baselines fixiert und Audits angelegt.
- Modulares Android-/Wear-Projekt erstellt.
- Read-only AAPS-Broadcastadapter, versioniertes Modell/Protokoll, Wear Data Layer, DataStore, Glukose-Complication und WFF-Testwatchface implementiert.

## 0.2.0 - 2026-08-02

- Gemeinsame Mobile-/Wear-Application-ID und Wiederanfrage über MessageClient umgesetzt.
- Vollständigen aktuellen AAPS-Tizen-Statusvertrag normalisiert.
- 27 Complication-Provider einschließlich Bild- und Verlaufstypen implementiert.
- Projekt nach Übernahme der AAPS-V4-Ressourcen als AGPL-3.0 deklariert;
  vollständiger Lizenztext und Herkunftsnachweis ergänzt.

## 0.3.0 - 2026-08-02

- Originales AAPS-V4-WFF mit fünf lokalen Provider-Komponenten übernommen.
- AAPS V2 aus dem offiziell AGPL-lizenzierten Austausch-ZIP als codefreies WFF
  mit acht Complication-Slots portiert.
- Alle alten Therapieanzeigen zentral gegen veraltete Zustände gesperrt.
- Ranged-Value-Typen für Reservoir und Batterien korrigiert.
- Wear-Wiederanfrage, fortlaufende Diagnose, Formatter-, DataStore- und
  27-Provider-Tests ergänzt.
- Offiziellen, gepinnten WFF-Validator und No-DEX-Prüfung in CI aufgenommen.
- Sicheren AAPS-CWF-Parser, strikt degradationsbewussten WFF-Generator und
  messbaren PNG-Screenshot-Comparator implementiert und getestet.
- AndroidAPS-Dev-Baseline nach Upstream-Wechsel auf `18101c8` erneut auditiert;
  öffentlicher Status-Broadcast blieb unverändert.
- Alle zehn priorisierten Kernwatchfaces und 13 weitere Community-Watchfaces
  als getrennte codefreie WFF-Pakete fertiggestellt; PinkFloydTheWall aus
  Rechtsgründen ausgeschlossen.
- Wear-OS-6-Emulatortest für 23 aktive und 23 Doze/AOD-Darstellungen mit
  synthetischen Daten, Hashbericht und Nicht-Schwarz-Prüfung ergänzt.
- Android-15-Broadcast-, Prozessneustart- und DataStore-Diagnosetest ausgeführt;
  blockierendes Data-Layer-Warten ohne Uhr durch persist-first und Timeout behoben.
- Protokoll/Modell auf Schema 2 migriert, echte AAPS-Paketversion separat vom
  Payloadvertrag erkannt und Android-Paketvisibilität ergänzt.
- Reproduzierbares DIY-Vorschaupaket mit SHA-256-Manifest vorbereitet.

## 0.3.1 - 2026-08-05

- Smartphone-Diagnoseansicht beobachtet den laufenden AAPS-Empfang jetzt
  während sie sichtbar bleibt und aktualisiert sich ohne erneutes Öffnen.
- Der WFF-Validator erkennt und repariert einen unvollständigen, eindeutig
  begrenzten temporären Git-Cache und prüft native Git-Fehler explizit.
- AndroidAPS-Dev-Kontrollbaseline auf `e1068e7` aktualisiert; der öffentlich
  bereitgestellte Statusvertrag blieb gegenüber der Release-Baseline gleich.
- Reale lokale Übertragung mit AndroidAPS 4.0.0-dev-b auf Samsung-Smartphone und
  Galaxy Watch mit One UI Watch 8 bestätigt.
- Regressionstest für eine Diagnosedatenänderung bei dauerhaft geöffneter
  Activity ergänzt.
- Realen End-to-End-Datenfluss mit AndroidAPS `4.0.0-dev-b`, physischem
  Samsung-Smartphone und Samsung-Watch mit One UI Watch 8 belegt.
- AndroidAPS-`dev` erneut bei `e1068e77` geprüft; der verwendete öffentliche
  `TizenPlugin`-Broadcast ist gegenüber `18101c8a` byteidentisch.
