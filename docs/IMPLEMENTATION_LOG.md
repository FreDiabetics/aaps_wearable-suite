# Implementation Log

## 2026-08-02, initiales Arbeitspaket

- Geändert: Projektaufbau, Kernmodell, AAPS-Adapter, Wear-Protokoll, mobile/watch Apps, DataStore, Complication, WFF-Testmodul und Audit-Dokumente.
- Zweck: reproduzierbare Phase-0-Baseline plus minimaler read-only End-to-End-Prototyp.
- Umsetzung: AAPS-Broadcast wird defensiv normalisiert; nur der letzte Zustand wird per DataItem übertragen und auf der Uhr gespeichert; stale Glukose wird nicht als Wert angezeigt.
- Tests: Unit-Tests und Debug-Assemblies erfolgreich; separates WFF-Release erfolgreich und ohne DEX verifiziert. Details in `TEST_REPORT.md`.
- Während der Prüfung behoben: AGP-9 Built-in-Kotlin-Konfiguration, SDK-Lokalisierung, nicht verfügbare JDK-17-Toolchain, Android-freier Adaptertestpfad und korrekte `ComponentName`-Übergabe an den Complication-UpdateRequester.
- Einschränkungen: siehe `KNOWN_LIMITATIONS.md`.
- Nächster Schritt: Gradle-Wrapper erzeugen, Tests/Build ausführen, Fehler beheben; danach AAPS V4 starten.

## 2026-08-02, Ausbau 0.2.0

- Geändert: gemeinsamer Paketname, kompletter Tizen-Broadcastadapter, Capability-/Validator-/Dev-/Stable-Adapter, mobile DataStore-Persistenz, Wiederanfragepfad und 27 Provider.
- Zweck: den zuvor bekannten End-to-End-Paketblocker entfernen und den belegten aktuellen AAPS-Datenvertrag abdecken.
- Tests: Adapter-, Vertragserkennungs- und Protokolltests sowie Mobile-/Wear-Build erfolgreich; Providerzahl 27 aus Manifest geprüft.
- Einschränkungen: keine Hardware vorhanden; temporäres Ziel und voller Loopmodus fehlen im öffentlichen Broadcast.
- Nächster Schritt: AAPS-V4-WFF-Paket übernehmen/entkoppeln, danach AAPS V2 und visuelle Goldens.

## 2026-08-02, AAPS V4 Start

- Geändert: neues Ressourcenmodul `watchfaces/aaps-v4` mit fünf AAPS-Provider-Slots und Ambient-Varianten.
- Zweck: Beginn der manuellen AAPS-V4-Portierung ohne ausführbaren Watchface-Service.
- Tests: Release-Build erfolgreich; kein `classes.dex` im APK.
- Einschränkung: Layout ist noch nicht per Golden-Screenshot gegen das originale AAPS-V4-WFF abgenommen.
- AAPS V2: offizielles ZIP analysiert; Metadaten nennen Andrew Warrington und Philoul. Die spätere Repository-Prüfung hat die AGPL-3.0-Herkunft belegt.

## 2026-08-02, Stabilisierung, originale AAPS V4 und AAPS V2

- Geändert: zentrale Formatierung; Ranged-Value-Ausgabe für Reservoir und beide
  Batterien; globale Stale-Sperre für Therapiewerte und Bilder; aktive
  Wiederanfrage beim Öffnen der Uhr; fortlaufende Wear-Diagnose; DataStore- und
  Provider-Tests; originale AAPS-V4-Ressourcen; neues AAPS-V2-WFF; AGPL-
  Lizenztexte und Herkunftsnachweise; reproduzierbare WFF-/No-DEX-Prüfskripte
  und CI.
- Zweck: Neustart-/Reconnect-Verhalten absichern, niemals alte IOB-/COB-/Pumpen-
  Daten als aktuell zeigen und die ersten beiden Kernwatchfaces als echte,
  codefreie WFF-Pakete liefern.
- Umsetzung: AAPS V4 entspricht der Upstream-XML plus exakt fünf Provider-
  Ersetzungen. AAPS V2 bildet das 400×400-CWF mit dem WFF-Maximum von acht
  Complication-Slots nach. Der offizielle Google-Validator ist auf Commit
  `702e9bd` gepinnt.
- Tests: Formatter-, DataStore- und Provider-Robolectric-Tests bestanden;
  Wear-Debug-Build bestanden; alle drei WFF-v1-Dateien offiziell validiert;
  drei Release-APKs gebaut und ohne DEX bestätigt.
- Während der Prüfung behoben: Robolectric 4.13 war nicht JDK-25-kompatibel und
  wurde auf 4.16.1 aktualisiert; der Validator deckte zwei fehlerhafte Attribute
  im alten Test-WFF auf, die korrigiert wurden.
- Einschränkungen: reale Data-Layer-Paarung, AOD/Burn-in, Emulator-/Hardware-
  Screenshots und One UI Watch 8 sind weiterhin nicht ausgeführt.
- Nächster Schritt: Emulator-Paar aufsetzen, mobile Broadcastzustellung und
  Data Layer integriert prüfen, danach AAPS Standard/Circle/Digital Style und
  die Parser-/Generatorwerkzeuge vervollständigen.

## 2026-08-02, 21:40 +02:00, Migrations- und Screenshot-Werkzeuge

- Geändert: `tools/aaps-cwf-parser`, `tools/wff-generator`,
  `tools/screenshot-comparator`, Modulregistrierung und Migrationsdokumentation;
  Mobile-/Wear-Versionsstand auf 0.3.0 angehoben.
- Zweck: CWF-Eingaben sicher und nachvollziehbar analysieren, unvollständige
  automatische WFF-Ausgaben standardmäßig verhindern und spätere Golden-
  Screenshots quantitativ vergleichen.
- Umsetzung: ZIP-/Größen-/Typvalidierung, normalisiertes CWF-Modell,
  WFF-v1-Ausgabe mit maximal acht Slots und expliziter Degradationsfreigabe;
  PNG-Metriken MAE/RMSE/Pixelanteil/Maximalabweichung samt Differenzbild.
- Tests: vier Parser-, drei Generator- und drei Comparator-Tests bestanden.
  AAPS V2 real geparst; eine ausdrücklich degradierte Ausgabe erzeugt und mit
  dem offiziellen WFF-Validator erfolgreich geprüft.
- Bekannte Einschränkungen: automatische Konvertierung bildet dynamische
  Einstellungen/Twin-View nicht ab; Screenshot-Aufnahme selbst erfordert
  Emulator oder Uhr.
- Nächster Schritt: vollständiger Regressionsbuild; anschließend AAPS
  Standard, Circle und Digital Style untersuchen und portieren.

## 2026-08-02/03, vollständiger Port- und Emulatorlauf

- Geändert: 23 veröffentlichbare WFF-Module, vollständiges Community-Inventar,
  SHA-gepinnte Reparatur bekannter Quellarchive, Freitext-/Split-Zeit-Mappings,
  AAPS-Vertragsschema 2, Paketversionserkennung, Android-Paketvisibilität,
  Debug-Testdateneingang, Golden-Aufnahmeskript, Release-/Lizenzdokumentation.
- Zweck: sämtliche zulässig portierbaren AAPS-/Austausch-Watchfaces in
  codefreie Pakete überführen und den Datenweg bis zum WFF-Rendering prüfen.
- Umsetzung: Provider-App vor WFF installiert; 23 Favoriten mit synthetischem
  vollständigem Therapiezustand gebunden; je aktiver und echter Doze/AOD-Frame
  aufgenommen. AAPS V4 wurde nach abgeschlossener asynchroner Providerbindung
  gezielt erneut aufgenommen.
- Tests: Android-15-Broadcast/Persistenz/Prozessneustart ohne ANR; Wear-OS-6-
  Installation; 23 aktive + 23 AOD-Goldens; 23 Hashzeilen; alle aktiven Bilder
  mindestens 3,16 Prozent nicht-schwarze Pixel. Vollständiger Gradle-/Validator-
  /No-DEX-Lauf wird im Testbericht als eigener Abschlusslauf protokolliert.
- Behoben: Data-Layer-Warten im BroadcastReceiver blockierte ein Telefon ohne
  Uhr; nun persist-first plus vier Sekunden Timeout. WFF-Favoriten ohne
  Default-Provider wurden durch dokumentierte Installationsreihenfolge erkannt.
- Einschränkungen: echte gekoppelte Data-Layer-Hardware und One UI Watch 8
  fehlen; PinkFloydTheWall ist rechtlich blockiert; pixelgenaue Original-Goldens
  auf identischer Hardware bleiben offen.
