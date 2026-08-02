# Changelog

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
