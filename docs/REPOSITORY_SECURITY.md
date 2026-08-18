# Repository-Schutz und Recovery

## Zielzustand für `main`

`main` ist der Integrationszweig und soll ausschließlich über Pull Requests verändert werden.
Empfohlene GitHub-Regeln:

- Pull Request vor Merge verpflichtend.
- Status-Check `verify` aus `.github/workflows/build.yml` verpflichtend und aktuell.
- Force-Push deaktivieren.
- Branch-Löschung deaktivieren.
- Conversation resolution vor Merge verlangen.
- CODEOWNERS berücksichtigen; sobald mindestens ein unabhängiger Maintainer vorhanden ist, mindestens eine Freigabe verlangen.
- Administratoren nicht von den Schutzregeln ausnehmen, sofern kein dokumentierter Recovery-Fall vorliegt.

Bei einem Single-Maintainer-Repository darf eine Pflichtfreigabe durch den eigenen Account nicht so konfiguriert werden, dass reguläre PRs nicht mehr mergebar sind. CI und PR-Pflicht bleiben trotzdem verbindlich.

## Tags und Releases

- Releases nur aus einem grünen Commit auf `main` erzeugen.
- Versions-Tags nicht verschieben oder wiederverwenden.
- Bevorzugt signierte/annotierte Tags verwenden.
- Release-Artefakte zusammen mit Prüfsummen veröffentlichen.
- Fehlerhafte Releases durch eine neue Version korrigieren, nicht durch stilles Ersetzen bestehender Tags oder Assets.

## Backup außerhalb dieses Repositorys

Ein Branch oder Tag im selben Repository schützt nicht vor Repository-Löschung. Mindestens ein Backup muss außerhalb von `FreDiabetics/sugarlicious` liegen.

Empfohlene Strategie:

1. Regelmäßig einen vollständigen Mirror erzeugen: `git clone --mirror` bzw. `git remote update`.
2. Den Mirror auf einem zweiten, getrennten Speicherziel sichern, z. B. privates Backup-Repository oder verschlüsseltes lokales/NAS-Backup.
3. Zusätzlich Releases, Tags und GitHub-Metadaten dokumentieren.
4. Monatlich prüfen, ob aus dem Mirror ein neues Repository vollständig wiederhergestellt werden kann.
5. Vor riskanten Repository-/Org-Änderungen einen aktuellen Mirror und einen Export der relevanten Einstellungen anlegen.

Recovery eines Git-Mirrors erfolgt grundsätzlich durch Anlegen eines neuen leeren Repositorys und anschließendes `git push --mirror` aus dem geprüften Backup.

## Secrets

- Keine API-Tokens, Passwörter, privaten Schlüssel, Keystores oder produktiven Zugangsdaten committen.
- Lokale Geheimnisse ausschließlich über nicht versionierte Dateien, Umgebungsvariablen oder GitHub Actions Secrets bereitstellen.
- Ein versehentlich veröffentlichter Secret-Wert gilt als kompromittiert: sofort widerrufen/rotieren; bloßes Löschen aus dem letzten Commit reicht nicht.
- Vor Releases und bei verdächtigen Änderungen Repository-Historie auf Secret-Muster prüfen.

## Notfalländerungen

Ein direkter Schreibzugriff auf `main` ist kein normaler Entwicklungsweg. Falls ein echter Recovery-Fall eine Ausnahme erfordert, muss anschließend ein nachvollziehbarer Commit/PR dokumentieren, warum die Ausnahme nötig war und welcher Zustand wiederhergestellt wurde.
