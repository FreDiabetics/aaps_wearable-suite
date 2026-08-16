# Dexcom G7 Direct-to-Watch foundation

This document records the exact implementation boundary of the experimental Sugarlicious G7 Watch Collector. It is intentionally explicit about protocol gaps: production code never reports a successful authentication, connection, or sensor reading until the proprietary G7 exchange has been validated.

## Status summary

| Area | Status | Current boundary |
| --- | --- | --- |
| Independent Wear app | IMPLEMENTED | `:g7watch`, application ID `app.aapswear.g7watch` |
| Shared CGM model and source resolution | IMPLEMENTED | Generic `CgmReading`; a current local G7 reading wins, otherwise existing phone sources remain available |
| Reading identity, delta, trend, freshness, gaps | IMPLEMENTED | Pure, unit-tested domain functions |
| BLE scan and GATT isolation | SKELETON | Android scanner and GATT client exist; only a previously known address can be matched |
| G7 service UUID | RESEARCH REFERENCE | Service UUID is a research lead, not treated as a complete specification |
| Authentication and cryptography | BLOCKED BY G7 PROTOCOL RESEARCH | Interfaces and explicit failing implementation only |
| Packet parsing | BLOCKED BY G7 PROTOCOL RESEARCH | Explicit `G7ProtocolResearchRequired`; no production test values |
| Session and recovery model | IMPLEMENTED FOUNDATION | State transitions, bounded escalation and reconnect timing are unit tested |
| Automatic G7 reconnect | PARTIAL | Scheduler and boot/reconnect receivers exist; a real cycle cannot run before authentication and packet parsing are known |
| Local Watch database | IMPLEMENTED | Idempotent SQLite insert, latest/previous/range/pending/ACK APIs |
| Existing Sugarlicious complications | IMPLEMENTED FOUNDATION | Signature-protected local provider is queried centrally; a valid fresh G7 value can feed the existing complication layer |
| Store-and-forward sync | SKELETON | Batch and ACK abstraction exists; Wear Data Layer transport and phone ingestion remain TODO |
| Local alarm evaluation | PARTIAL | Thresholds, trends, signal loss, sensor error, hysteresis, acknowledgement, snooze and repeat policy are domain logic only |
| Alarm notification/persistence/sync | TODO / NOT IMPLEMENTED | Interfaces exist; channels, history and alarm event transport remain separate work |
| Collector ownership/handoff | SKELETON | Ownership state machine exists; protocol-safe Phone/Watch handoff is not implemented |
| Backfill | SKELETON | Interface and gap model only |
| Status UI | PARTIAL | Safe collector/session/reading/pending/error status; setup is visibly disabled |
| Alarm sounds | TODO / NOT IMPLEMENTED | Alarm sounds were intentionally not implemented |

## Project analysis and modules

The existing phone app (`:app-mobile`), Wear companion (`:app-wear`), complication module, shared model/protocol modules, persistence, Data Layer paths, and WFF packages remain intact. The foundation adds only:

- `:dexcom-g7`: platform-independent G7/CGM domain model, source resolver, session/recovery/scheduling, parser/crypto contracts, sync contract and alarm rules.
- `:g7watch`: independently installable Wear OS collector shell, BLE adapters, persistent collector state, local reading database, boot/reconnect entry points, read-only provider and status screen.

No therapy action, dosing, basal control, loop control, cloud upload, or remote treatment feature is introduced.

## Data architecture

```text
future validated G7 BLE packets
  -> G7PacketParser
  -> G7Reading
  -> CgmReading
  -> CgmReadingRepository (Watch SQLite first)
       -> signature-protected local provider
       -> existing Sugarlicious complication source resolver
       -> CgmAlarmEngine
       -> G7ReadingSyncManager (pending batch / ACK)
```

`CgmReading` stores stable identity, source, sensor/session identity, sensor timestamp, receive timestamp, glucose, delta, mapped trend/rate, prediction, sensor age, sequence and validity status. Deduplication uses the stable reading ID and the database primary key. Unsynchronised records are never removed by normal retention logic.

## Source resolver and complications

The resolver is central rather than duplicated per complication. A valid, current `DEXCOM_G7_WATCH` reading has priority on the Watch. If it is missing, invalid or too old, the existing AndroidAPS/xDrip path remains the fallback. The independent collector exposes only read access through a signature permission; the existing Wear app converts that value into the shared display state used by all Sugarlicious complications and graphs.

The collector does not directly render or address individual complications. It writes the repository first. A phone connection, sync failure or alarm failure is not a prerequisite for local complication data.

## BLE, GATT and authentication

Android-specific BLE code is isolated behind `G7Scanner`, `G7DeviceMatcher` and `G7GattClient`. Scanning is low-power, time-bounded and restricted to the research service UUID. The app requests only scan/connect, notification, connected-device foreground service and boot permissions required by this foundation.

The fine-grained protocol state model contains scanning, discovery, notification, authentication rounds, challenge, certificate/key exchange, bonding, glucose, backfill, waiting and recovery states. These are architecture states only. `ResearchG7CryptoEngine` and `ResearchG7PacketParser` throw `G7ProtocolResearchRequired` for every unknown proprietary operation. They never return fake success.

## Session, reconnect, recovery and power

`G7SessionManager` persists non-secret sensor/collector/session state. Secrets are deliberately absent until their exact format is known; later storage must use Android Keystore protection. A successful future reading schedules the next expected five-minute window with a small pre-connect lead. The exact timing remains `TODO(G7-SCHEDULING)` and must be confirmed on real sensors.

Retry delays use bounded exponential backoff. Recovery escalates from reconnect and auth retry through rescan, address refresh, session reauth/reset, rebond and full handshake to user intervention. Non-recoverable research gaps clear the next reconnect time immediately, preventing an endless wakeup loop. Normal disconnect after a reading is represented by the waiting state rather than a sensor error.

Boot recovery reloads persisted state and starts only when the collector had been enabled. Phone loss never changes `WATCH` ownership. A real autonomous loop remains blocked until the G7 authentication and notification packet sequence is validated.

## Local storage and sync

The Watch database implements insert/insert-or-ignore, latest, previous, recent, range, pending and mark-synced operations. Each valid future reading must be written locally before any other consumer is invoked. The sync manager already batches pending records and applies only acknowledged IDs, making retries idempotent.

The actual Watch-to-phone transport, reconnect trigger, phone-side deduplication/ingestion, alarm event sync, settings sync and conflict revisions are `TODO / NOT IMPLEMENTED`.

## Alarm foundation

The domain engine supports very high, high, low, very low, rapid rise, rapid fall, signal loss and sensor error evaluation with validated threshold ordering. Active alarms retain their original trigger, threshold alarms resolve with hysteresis, and helper transitions cover acknowledge, snooze and repeat eligibility. Signal loss is based on reading age, not a BLE disconnect.

Notification channels, vibration execution, persistent alarm history, alarm event sync and UI actions are not yet wired. The notifier is an interface only. **ALARM-SOUNDS were intentionally not implemented.**

## Security and diagnostics

- No G7 keys, certificates, session secrets or pairing secrets are logged or persisted.
- No fake authentication, connection or reading can enter production state.
- The reading provider is read-only and signature protected.
- The collector UI exposes safe status/error text, never cryptographic details.
- Structured protocol logging and a full diagnostics snapshot are still TODO.

## Tests

Implemented domain tests cover adjacent-reading delta, shared trend mapping, local-source priority, Watch ownership during phone loss, reconnect scheduling, bounded recovery, non-recoverable stop behaviour, alarm deduplication/hysteresis and explicit parser/crypto failure. Android tests cover collector state restoration. Existing phone, Wear, complication and Watch Face Push suites verify integration regressions.

Hardware validation is still required for BLE permission prompts, scan matching, GATT discovery, foreground-service/boot behaviour, actual notification packets, autonomous cycles and Watch-to-phone delivery.

## G7 PROTOCOL RESEARCH TODO

- `TODO(G7-PROTOCOL)`: validated advertising/name matching, characteristic UUID map, descriptors, notification ordering, glucose/status packet framing and field semantics.
- `TODO(G7-AUTH)`: exact initial handshake, reconnect authentication rounds, retryability classification and transition rules.
- `TODO(G7-CRYPTO)`: certificate/key formats, EC/J-PAKE details, AES challenge inputs/outputs, validation rules and protected reusable session material.
- `TODO(G7-SESSION)`: which authentication/session elements may safely survive reconnect, process death and reboot.
- `TODO(G7-RECONNECT)`: confirmed known-address behaviour, address rotation, normal disconnect semantics and when reauth/reset/rebond/full handshake is required.
- `TODO(G7-SCHEDULING)`: sensor timing window and safe pre-connect/backoff values from hardware observation.
- `TODO(G7-BACKFILL)`: request format, response packet parsing, limits, ordering and gap reconciliation.
- `TODO(G7-COLLECTOR-HANDOFF)`: protocol-safe transfer between phone and Watch without competing sensor sessions.
- `TODO(G7-SENSOR-ERROR)` and `TODO(G7-SIGNAL-LOSS)`: validated sensor status mapping distinct from ordinary BLE disconnects.
- `TODO(G7-ALARMS)` and `TODO(G7-ALARM-SYNC)`: persistent runtime, notification delivery, history and acknowledged event transport.
- `TODO(ALARM-SOUNDS)`: intentionally deferred audio resources and sound selection.

Juggluco may be used only as a GPL-3.0 research reference for protocol understanding. No Juggluco implementation code has been copied into this foundation.
