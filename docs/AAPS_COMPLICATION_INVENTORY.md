# AAPS Complication Inventory

Die aktuelle AAPS-Baseline manifestiert 23 Provider:

| Gruppe | Typen |
|---|---|
| LongStatus, LongStatusFlipped | LONG_TEXT |
| Sgv, SgvLarge, SgvExt1, SgvExt2 | SHORT_TEXT |
| BgGraph | SMALL_IMAGE, LARGE_IMAGE |
| BrCobIob und Ext1/2, BrTt, Target, BrIob, Br | SHORT_TEXT |
| CobIob, CobIcon, CobDetailed, IobIcon, IobDetailed | SHORT_TEXT |
| UploaderBattery | RANGED_VALUE, SHORT_TEXT, ICON |
| Wallpaper Light/Dark/Gray | LARGE_IMAGE |

Die unabhängige Wear-App manifestiert alle 27 geforderten Provider und deckt
`SHORT_TEXT`, `LONG_TEXT`, `RANGED_VALUE`, `SMALL_IMAGE` und `LARGE_IMAGE` ab.
Bildprovider rendern Glukose oder den auf der Uhr gehaltenen Verlauf. Primäre
Typen, Ranged Values, stale-Sperren und Preview-Daten sind automatisiert
getestet. Wear-OS-6-Goldens zeigen die Provider in den 23 WFF-Paketen.
