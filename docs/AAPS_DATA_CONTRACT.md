# AAPS Data Contract Audit

## Beste lokale Schnittstelle

Für eine unabhängige App ist der bewusst veröffentlichte Broadcast
`info.nightscout.androidaps.status` die beste lokale read-only Schnittstelle.
Die umfangreichere interne AAPS-Wear-`EventData`-Kommunikation ist keine stabile
öffentliche API. Der Sender ist `plugins/sync/.../tizen/TizenPlugin.kt`; in der
AAPS-Oberfläche heißt das Plugin **External Companion Apps**.

Der Sender fragt manifestierte Empfänger ab, adressiert jedes Zielpaket explizit
und verwendet `FLAG_INCLUDE_STOPPED_PACKAGES`. Belegt sind `glucoseMgdl`,
`glucoseTimeStamp`, `units`, `slopeArrow`, `deltaMgdl`, `avgDeltaMgdl`, `high`,
`low`, `bolusIob`, `basalIob`, `iob`, `cob`, `futureCarbs`, `phoneBattery`,
`rigBattery`, Suggested/Enacted samt Zeit, `baseBasal`, `profile`, Temp-Basal-
Felder sowie optionale Pumpenbatterie, Reservoir und Pumpenstatus.

Der Adapter akzeptiert kompatible Zahlentypen, ignoriert unbekannte Felder,
verwirft Glukose außerhalb 20–1000 mg/dl, fehlende/ungültige Zeitstempel und
mehr als fünf Minuten zukünftige Messungen. Fehlende Felder werden `null` und
erzeugen keine Capability.

## Capability-Matrix

| Feldgruppe | Broadcast | Modell |
|---|---:|---:|
| Glukose, Einheit, Trend, Messzeit | ja | vollständig normalisiert |
| Delta, AvgDelta, Zielgrenzen | ja | optional |
| IOB, Bolus-/Basal-IOB, COB, zukünftige KH | ja | optional |
| Basal und temporäre Basalrate | ja | absolut/Prozent/Dauer |
| Suggested/Enacted | ja | Zeit und Rohpayload, kein Rückkanal |
| Profil, Pumpenstatus, Reservoir, Batterien | ja | optionale Capabilities |
| Temporäres Ziel als sicherer eigener Zustand | nein | nicht behauptet |
| kompletter Loopmodus/Modus-Endzeit | nein | nicht behauptet |

Der Broadcast enthält keine App-Version. Die Mobile-App deklariert per
`<queries>` die fünf aktuellen AAPS-/Pumpcontrol-/Client-Pakete und ermittelt
daraus VersionName/VersionCode. `sourceContract` enthält getrennt
`AAPS_EXTENDED_STATUS_V1` oder `AAPS_LEGACY_STATUS`. Schema 1 wird beim Empfang
auf Schema 2 migriert.
