# Release Checklist

## Erfüllt

- [x] aktuelle AndroidAPS-Dev-Baseline und Delta dokumentiert
- [x] read-only Broadcastadapter ohne private Datenbank/API
- [x] versioniertes Modell, Capability-Erkennung und Payloadvalidierung
- [x] DataClient/MessageClient/CapabilityClient und Watch-DataStore
- [x] 27 Complication-Provider
- [x] 23 codefreie WFF-Pakete, Validator und AOD-Emulator-Goldens
- [x] 19 offizielle Community-Quellen inventarisiert und zugeordnet
- [x] AGPL-/MIT-Nachweise, Datenschutz, Installationsanleitung, CI
- [x] reproduzierbares DIY-ZIP mit SHA-256-Manifest
- [x] kein Internet-Permission, keine Cloud/Telemetrie/Therapiekommandos
- [x] realer Telefon-Uhr-Data-Layer-Einzeltest
- [x] Galaxy Watch / One UI Watch 8
- [x] öffentliches GitHub-Repository mit Android-Studio-Importanleitung

## Vor Version 1 zwingend offen

- [ ] Bluetooth-Unterbrechung/Reconnect und mehrere gekoppelte Uhren
- [ ] mindestens ein reales Nicht-Samsung-Wear-OS-Gerät
- [ ] Original-vs.-Port-Goldens mit kontrollierter identischer Testmatrix
- [ ] eigener Produktionssignierschlüssel und reproduzierbarer signierter Build
- [ ] Community-Review

## Bewusst ausgeschlossen

- [x] PinkFloydTheWall nicht veröffentlicht, solange Drittrechte ungeklärt sind
- [x] keinerlei Therapie-, Pumpen- oder Loopsteuerung
