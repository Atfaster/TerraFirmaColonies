# Ready-food service regression

The cook inherits a furnace worker startup check. With an empty registered
furnace list, that check returns before dispatching ready-food service. TFC
restaurants containing Firmalife ovens therefore cannot use this service.

The mixin handles only the empty-furnace case, retaining the arrival check and
the cook's existing food/menu/recipient selection. It does not implement
Firmalife cooking, change blueprints, change self-service, or modify other jobs.
When a vanilla furnace is registered, the original startup path remains intact.

## Reproduce on Windows

Requires Python 3.9+, Java 17 (`JAVA_HOME`), and an existing Forge 47.4.20 server
installation containing MineColonies 1.1.1237, TFC 3.2.27, Firmalife 2.1.28 and
TerraFirmaColonies 0.1.3.4. Build the patch with `gradlew.bat build` first.

```powershell
python tools/waiter-lab/run.py --base-server C:/path/to/server --lab-root run/waiter-baseline
python tools/waiter-lab/run.py --base-server C:/path/to/server --lab-root run/waiter-fixed --patched-jar build/libs/terrafirmacolonies-0.1.3.4.jar
```

Each lab root must be new. The runner copies server dependencies/configuration
into it, creates a disposable world and binds to localhost (port 25586; override
with `--port`). It never opens the source world or installs anything in the
source server. It preserves the test world, log, mod hashes and JSON results.

For a controlled comparison, `--patched-jar` overlays only the new mixin class
and its registration onto the original TerraFirmaColonies JAR. Other differences
between a local build and the installed mod are excluded from this comparison.

The probe calls transformed production methods inside real Forge using fixture
citizens, a real hut inventory and real menu/furnace modules. It first asserts
that the chunk and both citizens are visible to building queries. It then checks
40 cases: levels 1–5, fuel selected/unselected, and food in the waiter, in the hut,
absent, or excluded from the menu. The recipient lives in a level-5 home.

The fixed waiter must dispatch serving and actually transfer food without
duplication, or dispatch gathering when food is in the hut. An empty stock or
excluded menu must not dispatch serving. The baseline must remain blocked.
Every case has zero registered furnaces. Assertion failure makes the runner exit
nonzero. `cases.json` and `run-result.json` contain the results.

This is a method integration regression, not a natural AI/pathfinding playthrough.
It does not test the full gathering/navigation loop, existing-furnace cooking,
player serving, or prove that a remote server's self-service problem is fixed.
