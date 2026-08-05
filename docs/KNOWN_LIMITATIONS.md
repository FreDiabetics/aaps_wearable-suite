# Known Limitations

- Der öffentliche AAPS-Broadcast liefert Zielgrenzen, aber keinen verlässlich
  gekennzeichneten temporären Zielzustand und keinen vollständigen
  offen/geschlossen/pausiert-Loopmodus. Suggested/Enacted wird nur angezeigt.
- Der Broadcast besitzt keine kryptografische Absenderauthentisierung. Die App
  prüft Paketinstallation, Wertebereiche, Pflichtfelder und Zeitstempel, kann
  aber einen lokal absichtlich gefälschten Broadcast nicht sicher unterscheiden.
- Eine echte gekoppelte Telefon-Uhr-Data-Layer-Verbindung wurde auf Samsung-
  Hardware mit One UI Watch 8 erfolgreich geprüft. Gezielter Bluetooth-Ausfall,
  Wiederverbindung und mehrere gleichzeitig gekoppelte Uhren sind noch offen.
- Wear OS 6 im runden 454×454-Emulator und One UI Watch 8 auf einer Samsung
  SM-L705F sind installiert getestet. Ein zweites reales Nicht-Samsung-Gerät
  war nicht verfügbar und wird nicht als bestanden behauptet.
- 23 Watchfaces besitzen aktive und echte Doze/AOD-Goldens mit synthetischen
  Daten. Das belegt Rendering und Daten-Slots, aber noch keinen pixelgenauen
  1:1-Vergleich mit jedem Original auf identischer Hardware.
- WFF unterstützt höchstens acht Complication-Slots. CWF-`dynPref`, `dynData`,
  Twin-View und manche analogen/animierten Logiken sind nur bestmöglich ersetzt;
  Details stehen in der Paritätsmatrix.
- Die Provider-App muss vor den separaten WFF-Paketen installiert werden. Bei
  umgekehrter Reihenfolge können bereits angelegte Favoriten `NoDataSource`
  behalten; das Watchface muss dann entfernt und erneut installiert werden.
- PinkFloydTheWall wird wegen ungeklärter Rechte an Drittmotiven/Marke nicht
  gebaut oder verteilt.
- Das DIY-Vorschaupaket ist entwicklersigniert, nicht für Store-Veröffentlichung
  produktionssigniert und deshalb noch kein freigegebenes Version-1-Release.
