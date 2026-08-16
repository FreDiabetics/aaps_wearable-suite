# xDrip+ G7 authentication (`libkeks`)

The sources below are derived from `NightscoutFoundation/xDrip`, commit
`66eb3a17063a21b8dff344719e5e72a7decbc1a6`:

- `dexcom-g7/src/main/java/jamorham/keks/**`
- `dexcom-g7/src/main/java/jamorham/libkeks/**`
- `dexcom-g7/src/main/java/com/eveningoutpost/dexdrip/plugin/IPluginDA.java`

Original author notices (`JamOrHam`) are retained in the source. Sugarlicious
integrates the formerly Android-library-based code into a JVM module and wraps
it with its own typed collector API. The upstream project is licensed under
GNU GPL v3; GPL v3 code may be combined with this GNU AGPL v3 project under
section 13 of GPL v3. See the root `LICENSE` and the upstream source at
<https://github.com/NightscoutFoundation/xDrip>.

The default G7 certificate material is the public one-time setup material from
the xDrip project documentation. It is isolated from the collector state machine
so updated public material can be supplied without rewriting the BLE flow.

The local `jamorham.keks.util.Log` implementation is deliberately silent because
upstream debug messages can contain raw authentication packets. Sugarlicious
exposes only stable, non-secret collector status and error codes.
