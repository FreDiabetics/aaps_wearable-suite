# AndroidAPS Source Baseline

## Reproduzierbare Referenz vom 2026-08-02

- Repository: `nightscout/AndroidAPS`
- Branch: `dev`
- endgültiger Commit: `18101c8a2c0204a08d417f3d5fbac3e9ceae380f`
- Commit-Datum: 2026-08-02 22:23:06 +02:00
- Nachricht: `Merge pull request #5041 from olorinmaia/wear/impl/loopstatus_mode_duration`
- Ermittelt mit `git ls-remote`; lokal exakt auf diesen Commit ausgecheckt.

Zu Beginn des Arbeitspakets war `dev` bei
`593e78fd475536b7ab1bd11c21522ff07d41c131` (`fix workflow`, 13:10:43
+02:00). Die erneute Prüfung vor dem Release ergab den neuen SHA. Der Bereich
`593e78f..18101c8` wurde vollständig nach Dateinamen und gezielt nach Inhalt
auditiert.

## Delta-Auswirkung

Betroffen waren interne AAPS-Wear-Modelle, Wear-Complications, die Darstellung
von Loop-Modus-Restdauer, COB-Eingabegrenzen, Client-Predictions und der
automatische AAPS-V4-Watchface-Push. `TizenPlugin.kt` und damit der von diesem
Projekt verwendete öffentliche Broadcastvertrag blieben unverändert. Es musste
kein Feldmapping geändert werden. Das eigene Protokoll ignoriert unbekannte
Felder und migriert Schema 1 nach Schema 2.

## Weitere gepinnte Quellen

- AndroidAPSdocs/Austauschplattform: `openaps/AndroidAPSdocs`, `master` bei
  `30622415cac9923b77aba8d9ee2d8f08972bf9bf`, AGPL-3.0
- GlucoDataHandler: `114460bc29973c78580095e2e9b0f212eed9df20`, MIT
- Google WFF-Validator: `702e9bdf050df800a6469cd2155c5c123fd54cb7`

Ein reproduzierbarer Build dieses Arbeitspakets ist ausschließlich diesen SHAs
zugeordnet. Vor einem späteren Arbeitspaket muss `origin/dev` erneut gegen
`18101c8…` geprüft werden.
