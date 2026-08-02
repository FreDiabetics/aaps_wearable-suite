# Datenschutz und Sicherheit

- ausschließlich lokaler AndroidAPS-Broadcast und lokaler Wear Data Layer
- keine Internet-Datenquelle, Cloud, Telemetrie, Werbung oder Analytics
- keine Therapie-, Pumpen- oder Loopbefehle im Modell oder Protokoll
- gespeichert wird nur der letzte normalisierte Zustand; die Uhr hält zusätzlich
  höchstens sechs Stunden Glukoseverlauf für lokale Diagramme
- Backups und Klartext-Netzwerkverkehr sind für beide Apps deaktiviert
- Diagnosedaten enthalten Status, Zeitpunkte, Version und Verbindungszahl, aber
  keine vollständigen Gesundheits-Payloads
- der Debug-Testdatenempfänger wird nur in Debug-Builds kompiliert; er enthält
  ausschließlich synthetische Daten und existiert nicht in Release-Varianten

Die Anwendung ist eine Anzeigehilfe und kein Medizinprodukt für
Therapieentscheidungen. Bei widersprüchlichen oder veralteten Angaben ist die
primäre AndroidAPS-/CGM-Anzeige maßgeblich.
