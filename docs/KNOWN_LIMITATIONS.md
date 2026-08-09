# Known Limitations

- Der Foreground-Service erhöht mit einer sichtbaren laufenden Benachrichtigung
  die Hintergrundpriorität und fordert nach Neustart beziehungsweise App-Update
  einen Wiederanlauf an. Android kann den Prozess bei Systemdruck trotzdem
  beenden; ein erzwungener App-Stopp durch den Nutzer verhindert den Neustart.
  Die Funktion ist daher kein absolutes „unkillable“-Versprechen.
- Der Live-Status verwendet die offizielle Android-16-Promoted-Ongoing-
  Schnittstelle und einen Standardstil mit Glukosewert und Minigraph. Ob One UI
  ihn tatsächlich als Live-Benachrichtigung hervorhebt und wo der Minigraph
  angeordnet wird, entscheidet System/OEM. Die Logik ist per API-36-Test
  abgedeckt, aber in diesem Arbeitspaket nicht visuell auf realer
  One-UI-8.5-Hardware abgenommen.
- Der Foreground-Service-Typ `specialUse` benötigt bei einer späteren
  Google-Play-Veröffentlichung eine passende Deklaration und Überprüfung in der
  Play Console. Das lokale DIY-/ADB-Paket ist davon nicht blockiert.

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
- 25 Watchfaces besitzen aktive und echte Doze/AOD-Goldens mit synthetischen
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
- Stock-AAPS und xDrip+ liefern in den verwendeten öffentlichen Broadcasts
  keinen vollständigen historischen Graphen. Sugarlicious verwendet bewusst
  keinen Nightscout-Backfill und baut den lokalen Verlauf aus neu empfangenen
  Statusmeldungen auf. Fehlende CGM-Werte werden niemals erfunden oder
  interpoliert.
- xDrip+ muss seine lokale Broadcast-Ausgabe ausdrücklich aktiviert haben. Der
  Vertrag liefert Glukose, Trend und Messzeit, aber keine verlässlichen AAPS-
  Therapieinformationen. In `Automatisch` bleibt ein aktueller AAPS-Zustand
  deshalb vorrangig; die explizite xDrip+-Auswahl unterdrückt AAPS-Glukose.
- Der öffentliche AAPS-Vertrag liefert keine verlässliche, vollständige
  Insulinaktivitätskurve. Die gelbe Kurve im Zielband ist ausschließlich
  eine Display-Schätzung aus dem positiven IOB-Abfall benachbarter Messpunkte;
  der zukünftige Abschnitt ist gestrichelt. Bei unzureichender Datenbasis wird
  sie nicht gezeichnet. Sie darf nicht für Therapieentscheidungen verwendet
  werden.
- Der öffentliche Enacted-Status kann eine einzelne abgegebene SMB-Menge samt
  Zeit enthalten. Sugarlicious sammelt daraus lokale Marker ab Empfang; nach
  einer Neuinstallation entsteht daraus keine vollständige historische SMB-
  Reihe und fehlende Marker werden nicht rekonstruiert.
- `galaxy_watch_ultra_mockup_exact.svg` ist technisch eine SVG-Hülle mit einem
  eingebetteten, C2PA-signierten Rasterbild und kein Pfad-Vektor. Im Projekt
  wird nur diese SVG gespeichert; ihre Rasterdaten werden offline asynchron
  dekodiert. Sehr starke Vergrößerung gewinnt deshalb keine echte
  Vektorauflösung. Herkunft und Veröffentlichungsprüfung stehen in
  `LICENSES/USER_SUPPLIED_ASSETS.md`.
- AAPS-`predBGs` werden nur dargestellt, wenn sie im Suggested-/Enacted-Payload
  vorhanden und gültig sind. Die App berechnet bewusst keine Ersatzprognosen.
- Die Bildvorlage zeigt einen 24-Stunden-Insulin-Gesamtwert und einen Uhrenakku.
  Beides ist im verwendeten öffentlichen Broadcast nicht zuverlässig
  verfügbar; die App zeigt stattdessen Profil beziehungsweise belegten
  Verbindungs-/Telefonstatus und erfindet keine Werte.
- Der aktuelle 0.4.0-Build wurde auf dem physischen Smartphone installiert.
  Der visuelle Vergleich auf genau diesem Gerät war blockiert, weil es während
  der Aufnahme gesperrt war. Die Oberfläche wurde stattdessen vollständig auf
  einem API-35-Phone-Emulator mit 1080×2400 Pixeln und 420 dpi geprüft; das
  ersetzt nicht den noch offenen physischen Vergleich.
- Die beiden Sugarlicious-0.5.0-Watchfaces wurden aktiv und in Doze/AOD auf
  Wear OS 6 (480×480 rund) geprüft. Der erneute visuelle Realgerätetest dieser
  neuen Designs auf One UI Watch 8 ist noch offen und wird nicht behauptet.
- Die drei neuen 0.6.0-Varianten Orbit, Rings und Graph sowie das überarbeitete
  Analog-Watchface bauen, validieren und sind codefrei. Ein neuer aktiver/AOD-
  Screenshotlauf auf der realen Galaxy Watch Ultra wurde in diesem
  Arbeitspaket noch nicht ausgeführt und wird nicht behauptet.
- Die Analogzeiger sind bewusst eigenständig gezeichnet. Eine exakte Kopie von
  Apple-Watch-Ressourcen oder Apple-Trade-Dress ist weder enthalten noch als
  Zielparität deklariert.
