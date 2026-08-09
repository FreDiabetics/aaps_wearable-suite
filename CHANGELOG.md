# Changelog

## 0.6.1 - 2026-08-09

- Watchface-Karussell auf exakt einen Schritt pro Wischgeste begrenzt und
  dynamisch mittig ausgerichtet. Das aktive 100-dp-Zifferblatt sitzt jetzt
  mittig im Displayausschnitt und füllt ihn ohne das frühere Hochspringen.
- Die bisherige gezeichnete/PNG-basierte Uhren-Silhouette durch die vom
  Projektinhaber gelieferte `galaxy_watch_ultra_mockup_exact.svg` ersetzt. Im
  App-Ressourcenbaum existiert dafür nur die SVG; ihr eingebettetes Raster wird
  asynchron, offline und zwischengespeichert dekodiert.
- CGM-Graph auf lineare dynamische AAPS-orientierte Skalen korrigiert: Basal/TBR
  liegt in einer getrennten invertierten Cyan-Spur am oberen Rand, Basisbasal
  ist gestrichelt, Aktivität gelb und ihr zukünftiger Abschnitt gestrichelt.
  Predictions liegen rechts der Jetzt-Linie, CGM-Punkte besitzen eine knappe
  Kontur und der aktuelle Punkt nur eine stärkere Kontur statt eines Außenrings.
- IOB-/COB-Graphen ohne Überschrift und ohne Basalspur neu gezeichnet; SMBs aus
  dem öffentlichen AAPS-Enacted-Payload erscheinen als größenabhängige
  cyanfarbene Dreiecke. Modell-Schema 5 und Wear-Protokoll 6 transportieren
  diese Marker abwärtskompatibel.
- Notification-Graph vergrößert, Sugarlicious-Zusatztext entfernt und
  Zielbereichspunkte dort weiß dargestellt. Farbwahl um Alpha/Transparenz und
  `#AARRGGBB` erweitert; Rings-Ranged-Value und mobile Vorschau verstärkt.
- Watch-Menü zeigt Watchfaces vor Complications; Über-Bereich und Kontakt-
  Buttons sind zentriert. Mobile 0.6.1/Code 9, Wear 0.6.1/Code 11 und
  Sugarlicious Rings 0.6.1/Code 2.

## 0.6.0 - 2026-08-09

- Smartphone-Graphen vollständig interaktiv gemacht: stufenloses horizontales
  Verschieben mit einem Finger, Pinch-Zoom mit zwei Fingern, logarithmisch-
  dynamische Skalierung, abgerundete durchgehende Kontur und ausschließlich
  Zielgrenzen an der rechten Achse.
- CGM-Graph um AAPS-Basal/TBR im invertierten cyanfarbenen oberen Bereich,
  gestrichelte Basisrate, Ausblendfläche, echte rechtsseitige Predictions,
  Punktkonturen und eine ausdrücklich als Anzeige-Schätzung behandelte
  IOB-Abklingkurve ergänzt. Zeitnahe Duplikate werden quellenpriorisiert
  zusammengeführt; AndroidAPS gewinnt gegenüber xDrip+.
- Farben systemweit nach klaren Rollen getrennt, HSV-/Hex-Farbwähler und
  augenschonenden hellen Modus ergänzt. Zielbalken, Glukosewert und CGM-Punkte
  lassen sich unabhängig konfigurieren; Graphfarben werden zur Uhr und zu
  Bild-/Graph-Complications übertragen.
- Watchface-Karussell ohne umgebende Kachel neu aufgebaut: feste zentrierte
  Galaxy-Watch-Ultra-Silhouette, bewegte Watchfaces, kompakte Statuspille und
  Bearbeitung durch Antippen des Watchfaces. Technische Endnutzer-Tiles,
  Hamburger-/Overflow-Menüs und der Nightscout-Backfill wurden entfernt.
- xDrip+ als optionale, rein lokale Glukosequelle ergänzt. Automatisch bevorzugt
  aktuelle AAPS-Daten; xDrip+ übernimmt nur bei fehlendem/veraltetem AAPS oder
  ausdrücklicher Auswahl. Therapieinformationen bleiben AAPS-only.
- Laufende Benachrichtigung zeigt aktuellen Glukosewert, Trend, Alter und einen
  abgerundeten Minigraphen. Der optionale Android-16-/One-UI-Live-Modus nutzt
  weiterhin einen offiziellen Standard-Benachrichtigungsstil.
- Alle 27 Complication-Provider im Wear-Manifest registriert und mit statischer
  Vorschau versehen. Provider 02 unterstützt `SHORT_TEXT` und `RANGED_VALUE`.
- Vier analoge Sugarlicious-WFFs bereitgestellt: Analog, Orbit, Rings und Graph;
  jeweils codefrei, mit kräftigen eigenständigen Baton-Zeigern, AOD, Graph- und
  Ranged-Value-Slots. Keine Apple-Ressourcen oder kopierte Apple-Geometrie.
- Mobile 0.6.0/Code 8, Wear 0.6.0/Code 10, Modell-Schema 4,
  Wear-Protokoll 5 und Watch-Konfiguration 2.

## 0.5.1 - 2026-08-07

- Smartphone- und Wear-Oberfläche auf neutrale Grautöne mit dem Grün des
  FreDiabetics-Icons als Systemakzent umgestellt; farbige Kartenumrandungen
  entfernt und Menüs, Auswahlfelder sowie Navigation pillenförmig abgerundet.
- Die gelieferten farbigen und monochromen FreDiabetics-Assets integriert.
  Das farbige Asset dient der sichtbaren Marke, das monochrome Asset dem vom
  System eingefärbten Benachrichtigungssymbol.
- Eine normale, laufende Hintergrund-Benachrichtigung als Standard ergänzt.
  Der zugehörige `specialUse`-Foreground-Service hält den ausschließlich
  lokalen, lesenden AndroidAPS-Empfang und die Wear-Verbindung sichtbar aktiv
  und wird nach Neustart beziehungsweise App-Update erneut angefordert.
- In den Inline-Einstellungen einen optionalen Live-Benachrichtigungsmodus für
  Android 16/One UI 8.5 ergänzt. Er fordert den offiziellen Promoted-Ongoing-
  Status an, zeigt nur Verbindungsdiagnose ohne Therapiewerte und fällt auf
  älteren oder nicht freigeschalteten Systemen auf die normale Benachrichtigung
  zurück.
- Mobile- und Wear-Version auf 0.5.1/Code 7 angehoben; Service-, Neustart-,
  Einstellungs-, Berechtigungs- und Palettenregressionen ergänzt.

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
