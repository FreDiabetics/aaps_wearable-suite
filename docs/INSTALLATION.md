# Installation und Nutzung

## Was die Anwendung kann

Sie zeigt lokal von AndroidAPS gesendete Glukose-, Trend-, Delta-, IOB-, COB-,
Basal-, Profil-, Pumpen- und Batteriedaten auf Wear OS an. Sie kann keine
Therapie auslösen oder ändern. Die 27 Provider lassen sich auch in fremden
Watchfaces verwenden; deren Layout wird dann vom jeweiligen Watchface bestimmt.

## Voraussetzungen

- AndroidAPS mit aktivem Plugin **External Companion Apps** (historisch
  `TizenPlugin`)
- Android-Telefon mit Google Play Services for Wear OS
- gekoppelte Wear-OS-Uhr mit Wear OS 4 oder neuer; Zieltest ist Wear OS 6
- Installation aus unbekannter Quelle/ADB für das DIY-Vorschaupaket

## Reihenfolge

1. `apps/sugarlicious-mobile-debug.apk` auf dem Telefon installieren.
2. `apps/sugarlicious-wear-debug.apk` auf der gekoppelten Uhr installieren.
3. AndroidAPS öffnen und unter Konfiguration **External Companion Apps**
   aktivieren.
4. AndroidAPS einen aktuellen Status erzeugen lassen und die Mobile Bridge
   öffnen. Dort müssen AAPS-Version, Empfangszeit und Datenvertrag erscheinen.
5. Erst danach ein oder mehrere APKs aus `watchfaces/` auf der Uhr installieren.
6. Auf der Uhr das gewünschte Watchface auswählen. Falls Slots leer bleiben,
   das betreffende Watchface-Paket entfernen und nach der Wear-App erneut
   installieren oder die AAPS-Complications einmal manuell zuweisen.

## Erwartete Anzeige

- bis 6 Minuten Messalter: `aktuell`
- über 6 bis 12 Minuten: `verzögert`
- älter als 12 Minuten: `veraltet`, Therapiewerte werden als `—` verborgen
- kein oder unplausibel zukünftiger Messzeitpunkt: `keine Daten`

Die Messzeit, nicht die Empfangszeit, entscheidet über die Frische. Ein alter
Wert wird nie unmarkiert als aktuell weitergeführt.

## Ohne eigenes Watchface

In der Watchface-Konfiguration können die Provider `1 Glucose compact` bis
`27 Full AAPS status` einzeln ausgewählt werden. Text, Titel und Ranged Value
sind semantisch; Farbe, Schrift und Anordnung bestimmt das fremde Watchface.
Für kontrollierte Optik stehen die Bild-/Graph-Provider und die mitgelieferten
WFF-Pakete bereit.

Die beiden neuen Originalpakete heißen `sugarlicious-digital.apk` und
`sugarlicious-analog.apk`. Digital zeigt Zeit, Glukose/Trend/Delta, Graph und
vier Status-Tiles. Analog zeigt Glukose im oberen Tile, schlanke eigene Zeiger
und sechs kompakte Statusbereiche. Alle acht Slots sind über die normale
Watchface-Konfiguration austauschbar.

## DIY-Hinweis

Die bereitgestellten App-APKs sind Debug-/Entwicklerbuilds. Vor einer dauerhaften
Weitergabe sollte das Repository mit einem eigenen privaten Release-Key gebaut
werden. Niemals fremde Signierschlüssel in das Repository einchecken.
