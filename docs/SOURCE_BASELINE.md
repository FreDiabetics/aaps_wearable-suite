# AndroidAPS Source Baseline

## Kontrollstand vom 2026-08-05

Für das Dashboard-/Protokoll-3-Arbeitspaket wurde `refs/heads/dev` am
2026-08-05 um 23:33 +02:00 erneut abgefragt. Der SHA blieb unverändert; damit
ist auch der reproduzierbare 0.4.0-Build dem folgenden Stand zugeordnet.

- Repository: `nightscout/AndroidAPS`
- Branch: `dev`
- aktueller öffentlicher Commit:
  `e1068e77db4f801c046340c8313cd7a2856f4e7c`
- Commit-Datum: 2026-08-05 21:02:28 +02:00
- Nachricht: `:core:keys remove JVM dependency`
- Gegenüber der Release-Baseline `18101c8a…`: 41 Commits voraus.

Der am realen Telefon installierte lokale Dev-Build meldet
`4.0.0-dev-b`/VersionCode 1500 und stammt aus dem lokalen Merge
`8fd6b2782d01c0474f9f89307b1b69ae04692ddb`; dessen Upstream-Seite war
`d9aba62e62f8e7dc7f02d436f14904ba00c8eaf5`.

Geprüft wurden insbesondere `wear`, der neue Samsung-Watchface-Katalog,
Complications und der externe Status-Broadcast. Der Git-Blob von
`plugins/sync/src/main/kotlin/app/aaps/plugins/sync/tizen/TizenPlugin.kt` ist
sowohl bei `18101c8a…` als auch bei `e1068e77…`
`86e4337f037e403cb402cfb68d26a00475b63a1d`. Der von diesem Projekt gelesene
Broadcastvertrag hat sich somit nicht verändert. Die neuen Wear-Änderungen
betreffen unter anderem Samsung-Watchface-Auswahl und AAPS-interne Wear-UI,
nicht den externen Adapter.

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
