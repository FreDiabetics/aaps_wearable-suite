# Persistent Background Operation

This document describes the lifecycle contract for Sugarlicious Mobile, Sugarlicious Wear and the Dexcom G7 Watch Collector.

## Architecture

### Mobile

Sugarlicious Mobile remains the phone-side bridge. It does not contain a direct Dexcom G7 BLE collector.

```text
Dexcom G7 app -> AndroidAPS -> Sugarlicious Mobile -> Wear Data Layer -> Sugarlicious Wear
```

`PersistentBridgeService` is the existing foreground service for the user-visible background bridge/notification. It returns `START_STICKY` and is restored after `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`. The bridge has no user-facing disable switch; while Sugarlicious is installed its desired product state is therefore always enabled. A real Android Force Stop remains authoritative.

AAPS broadcasts are processed by the manifest `AapsStatusReceiver`. Valid state is persisted before Wear Data Layer I/O so a temporarily disconnected watch cannot invalidate phone-side data. The service notification keeps stale/no-data states explicit rather than making an old glucose value appear current.

### Wear

Phone-to-watch delivery remains event-driven through `StateDataLayerService`, a `WearableListenerService`. Sugarlicious does not add a second always-running Wear keep-alive service. Google Play services can deliver registered Data Layer events without the Wear Activity being visible.

The Wear Activity's 30-second refresh job is UI-only and is cancelled in `onStop()`; it is not part of background data transport.

### G7 Watch Collector

The direct G7 collector remains a separate Wear OS application/service and is independent from phone reachability and canonical source selection.

```text
explicit user start
    -> persisted collectorEnabled=true
    -> G7CollectorService collection cycle
    -> BLE/auth/read
    -> persisted reading/state
    -> exact/inexact allow-while-idle reconnect alarm
    -> next collection cycle
```

The service is intentionally event-driven rather than permanently resident between five-minute sensor windows. While a collection cycle is active it is a connected-device foreground service with an ongoing low-importance notification. Between cycles the persisted enable state plus scheduled reconnect represents the desired active collector state.

`collectorEnabled=false` is written only by explicit collector stop or deliberate sensor reconfiguration. Technical disconnects, process recreation, phone loss, source changes, boot and app replacement do not convert a user-enabled collector into a user-disabled collector.

## Lifecycle restore

### G7 enabled

- Activity closed: state remains enabled.
- Process recreated during a running service: `START_STICKY` re-enters the persisted enabled state.
- Boot: `G7BootReceiver` restores only when `collectorEnabled=true`.
- App update: `MY_PACKAGE_REPLACED` follows the same restore policy.
- Reconnect alarm: starts a cycle only while the persisted state remains enabled.
- Phone unavailable: no collector disable side effect.
- Canonical source changes to phone: no collector disable side effect.

### G7 disabled

- Boot does not start the collector.
- App update does not start the collector.
- A reconnect receiver ignores the event.
- Source selection cannot enable the collector.

## Source selection separation

The historical `SET_SOURCE` broadcast is retained for compatibility, but it no longer owns collector lifecycle. It may resume a collector only when both conditions are true:

1. the source signal selects direct G7, and
2. the persisted collector state is already enabled.

Leaving the direct-G7 display source never calls `G7CollectorService.stop()` and never writes `collectorEnabled=false`.

Canonical display/source resolution remains separate and unchanged.

## Battery optimization access

The G7 Watch UI reads the actual system state through `PowerManager.isIgnoringBatteryOptimizations()`.

`Dauerbetrieb freigeben` attempts real Wear/Android system surfaces in this order:

1. app-specific request for battery-optimization exemption,
2. battery-optimization settings list,
3. application details,
4. general system settings.

No local preference is used to fake a grant. Returning to the Activity triggers a fresh system-state read. If no system settings activity can be opened, the user receives visible feedback instead of a silent no-op.

## Battery policy

No permanent polling loop, keep-alive loop or phone-driven collector loop is added.

- Mobile background state is event/flow driven.
- Wear Data Layer state is event driven.
- G7 reconnect remains scheduled around sensor windows.
- The existing bounded collection-cycle wake lock remains limited to active collector work.
- Source changes no longer create stop/start churn.
- Existing BLE reconnect/backoff behavior is unchanged by this lifecycle work.

## Force Stop

An explicit Android/Wear OS **Force Stop** is not bypassed. Android may suppress receivers, alarms and service restarts until the user launches the application again. Sugarlicious does not attempt to defeat this system behavior.

## Manual lifecycle test matrix

| Scenario | Expected result |
| --- | --- |
| Open Mobile, then close UI | AAPS -> Mobile -> Wear flow continues without reopening Mobile |
| Open Wear, then close UI | Data Layer updates continue without Wear Activity being visible |
| Enable G7 collector, leave Activity | `collectorEnabled` remains true; scheduled collection continues |
| Turn display off / AOD / Doze | Collector remains enabled; scheduled reconnect remains platform-managed |
| Phone out of range | Direct Watch collector remains enabled and continues independently |
| Phone returns | Collector remains enabled; canonical source selection is independent |
| Reboot watch with collector enabled | Collector restores from persisted state |
| Reboot watch with collector disabled | Collector remains disabled |
| Update G7 Watch app with collector enabled | `MY_PACKAGE_REPLACED` restores collector lifecycle |
| Brief Bluetooth off/on | Technical unavailability does not write `collectorEnabled=false`; normal recovery path applies |
| Change display source away from direct G7 | Collector enable state is unchanged |
| Change display source to direct G7 while collector disabled | Collector remains disabled until explicit user start |
| Use `Dauerbetrieb freigeben` | A real system settings/request surface opens where supported; UI reflects actual grant on resume |
| Force Stop from Android settings | System Force Stop is respected; no unsupported bypass is attempted |

## Data-integrity invariants

Lifecycle restoration must never:

- rewrite `measuredAt`, `receivedAt` or sensor time to make data fresh,
- invent a glucose value,
- convert stale/no-source data into current data,
- delete bond/shared-key/session data for a recoverable lifecycle event,
- let a source-selection event overwrite the user's collector enable decision.

Resolver/source semantics are intentionally outside this lifecycle change.
