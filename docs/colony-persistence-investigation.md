# Prevent stale colony saves when building equipment is missing

## Problem and cause

TerraFirmaColonies 0.1.3.4 serializes optional kitchen, glassblower and stone
smeltery equipment positions without checking for `null`. Registry-produced,
unfinished buildings have no such positions yet. A kitchen with a pot but no
VAT, or a stone smeltery with a forge but no bellows, also fails to serialize.

The exception propagates through `RegisteredStructureManager.write` into
`Colony.write`. In MineColonies 1.1.1237, `Colony.getColonyTag` catches it and
returns the previously cached whole-colony tag. Forge can save that stale tag
while reporting `Saved the game`. Buildings, research and citizen/request state
share this colony snapshot; chunks and entity data are saved separately.
Repeated world saves cannot update the colony snapshot while serialization fails.

The same exception reaches `BackUpHelper.saveColonies` during shutdown and can
prevent the fresh per-colony backup/ZIP from being written. This was reproduced
with the original JARs; see [the stack trace](colony-persistence-stacktrace.txt).

The main overworld state is in `world/data/capabilities.dat`, under
`data/minecolonies:colonymanager/colonies`. Minecraft 1.20.1 autosaves every 6000
ticks (about five minutes at 20 TPS), but stale-cache fallback can roll a colony
back much further. The interval was inspected in bytecode; tests use explicit
`save-all flush`, not a timed autosave observation.

## Change

- Omit absent optional equipment positions when writing NBT.
- Read absent, wrong-type or incomplete position tags as `null`; preserve valid
  legacy tags, including an explicitly stored `(0, 0, 0)`.
- Read kitchen ovens using the compound element type used by the writer.
- Replace baker/kitchen oven and stone-smeltery drying-position lists on load,
  preventing duplicate/stale entries when an object is deserialized again.
- Return the kitchen's VAT position from `getVatPos`, independently of its pot.

NBT key names and valid coordinate formats remain unchanged. This prevents
future failures; it cannot reconstruct progress already rolled back or lists
already lost. MineColonies' stale-cache/error-reporting and backup-file atomicity
are outside this patch.

## Reproduction and evidence

Runtime: Minecraft 1.20.1, Forge 47.4.18, Java 17, MineColonies
1.1.1237-snapshot, TerraFirmaColonies 0.1.3.4. Supporting mod hashes are in
[the original results](colony-persistence-results.json). The actual Gradle
MineColonies dependency also resolves to 1.1.1237; README's 1.1.1170 is outdated.
No dependency or wrapper versions were changed.

Seven dedicated-server JVM launches tested the original JARs:

| Scenario | Original JAR result |
|---|---|
| Baseline save/load | One building, no added research |
| Add unfinished kitchen and completed research; flush | Save succeeds; disk still contains baseline |
| Stop and reload | New building/research missing; backup error logged during stop |
| Mutate, flush, kill Java, reload | Same stale colony snapshot restored |
| Set both kitchen equipment positions; mutate, flush, reload | New building and completed research persist |

There were 48 passing assertions. Baseline assertions explicitly expect the
defect; passing them does not mean that version is safe. Source, installed
bytecode and transformed runtime agree on the failure path.

The regression lab runs the normal **built and reobfuscated project JAR** in an
independent world. It checks all three unfinished building types, equipment
round trips, repeated loads, absent/malformed positions and whole-colony
save/reload after stop and a forced process kill. Raw logs/NBT are retained
locally; compact results are exported for review.

The fixed JAR passed five dedicated-server JVM launches and **68 assertions**:

| Fixed-JAR scenario | Result |
|---|---|
| All three optional equipment sets absent | All buildings serialize; absent keys remain absent |
| Populated equipment, repeated deserialization, malformed tags | Valid positions preserved; lists replaced; absent/invalid positions remain null |
| Flush and stop with three unfinished buildings and completed research | Fresh whole-colony snapshot written; no persistence/shutdown error |
| New JVM after stop | Colony name, all four building records and completed research restored |
| Flush, forcibly kill the owned Java process, new JVM | Same saved colony state and absent equipment restored |

See [fixed results](colony-persistence-fixed-results.json). Both before/after
evidence includes saved NBT decoded independently of the test mod, log hashes,
and runtime mod hashes. The fixed JAR SHA-256 is
`a110ae8b5d717bf7382f9dc7632007e30b28a6926187119fe3591406f883ad68`.
`gradlew --offline build` completed successfully, including `reobfJar`; the
project has no JUnit sources, so Gradle's `test NO-SOURCE` is not a test pass.

Instructions: [tools/persistence-lab](../tools/persistence-lab/README.md).
Never install the test-only mod on a production server.

## Coverage limits and gameplay validation

The automated tests use registry-produced buildings with initialized modules,
but insert them through a fixture and finish research through its API. They
exercise real transformed serializers, Forge save files, commands and process
reloads. They do not claim player construction, university work-cycle or AI
coverage.

Before marking gameplay behavior verified, use a separate client/server test
profile with the built JAR and **without the probe mod**:

1. Use the Build Tool to establish a colony and register a kitchen, glassblower
   and stone smeltery. Save/restart while each is still unfinished.
2. Finish the buildings through normal builder orders. Check the kitchen before
   a VAT is available and the smeltery before all equipment is detected.
   Save/restart; confirm building records and levels persist.
3. Complete research through a working university. Record its exact ID/state,
   save, stop and start the server; confirm it remains completed.
4. Assign a baker, chef, glassblower and stone smelter where supported. Supply
   one known working recipe and sufficient ingredients/fuel. Record output
   before/after restart, equipment recognition and any citizen exceptions.
5. With equipment registered, run a recipe, flush and kill only that disposable
   server process. Restart and repeat the checks. Compare progress to the
   completed save, not to changes made after it.
6. Check logs for `Something went wrong persisting colony:`, building
   `serializeNBT` exceptions and `Exception stopping the server`.

Manual gameplay checks are pending. WarNTaxes is absent from the minimal lab;
full-modpack interactions and specific remote-server incidents are not proven.
Killing after flush is not a disk-failure or mid-write power-loss test. This
patch does not claim to fix every citizen AI problem or make chunk/colony saves
transactional.
