# Elytra Combat

A server-authoritative Fabric mod for Minecraft **1.21 through 26.2**. Taking configured damage while wearing an elytra disables that exact elytra for a configurable period. If the player was flying, flight ends immediately with velocity preserved - and the real trouble begins.

Clients do not need the mod when joining a dedicated server. Install it normally in a client instance to use it in single-player. Mod Menu is optional and provides the configuration screen.

## Downloads and compatibility

One jar per API generation; each jar runs on every version in its range. Java 21 is enough for all 1.21.x jars, Java 25 for the 26.x jars.

| Jar | Minecraft versions | Java | Fabric API | Mod Menu (optional) |
|---|---|---|---|---|
| `elytra-combat-1.3.3+mc1.21.1` | 1.21, 1.21.1 | >=21 | 0.116.17+1.21.1 | 11.0.x |
| `elytra-combat-1.3.3+mc1.21.2-1.21.4` | 1.21.2, 1.21.3, 1.21.4 | >=21 | 0.106.1+1.21.2 | 12.0.x - 13.0.x |
| `elytra-combat-1.3.3+mc1.21.5-1.21.10` | 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10 | >=21 | 0.128.2+1.21.5 | 14.0.x - 16.0.x |
| `elytra-combat-1.3.3+mc1.21.11` | 1.21.11 | >=21 | 0.141.6+1.21.11 | 17.0.x |
| `elytra-combat-1.3.3+mc26.1.x` | 26.1, 26.1.1, 26.1.2 | >=25 | 0.155.2+26.1.2 | 18.0.x |
| `elytra-combat-1.3.3+mc26.2` | 26.2 | >=25 | 0.159.0+26.2 | 20.0.x |

Any mod-menu version suggested in the jar's metadata works; newer versions of Mod Menu for the same Minecraft line are fine too. On 1.21/1.21.1 the item-cooldown component does not exist yet, so disabled elytras use a plain cooldown and no original cooldown is restored when the disable expires.

## Building from source

`./gradlew build` builds the default target (26.1.x). Every jar has its own target id:

```
./gradlew -Ptarget=mc1211  build   # 1.21.1        -> build/libs/elytra-combat-1.3.3+mc1.21.1.jar
./gradlew -Ptarget=mc1212  build   # 1.21.2-1.21.4
./gradlew -Ptarget=mc1215  build   # 1.21.5-1.21.10
./gradlew -Ptarget=mc12111 build   # 1.21.11
./gradlew -Ptarget=mc2612  build   # 26.1.x
./gradlew -Ptarget=mc262   build   # 26.2
```

The `v`-suffixed targets (`mc1214v`, `mc12110v`, `mc2611v`) are verification builds: they compile an already-shipped flavor against the other endpoint of its range to prove the range claim, and produce no jar. All targets are built and released by CI on the `v*` tag (see `.github/workflows/release.yml`).

## Features

**Elytra disable on hit.** When its wearer takes damage matched by the [damage filter](#configuration), the worn elytra stops working for `disable_duration_seconds`. Hits never extend an ongoing cooldown, and the countdown runs in real time - it matches the vanilla cooldown overlay exactly, whether the elytra is worn, carried, or stored.

**Elytra durability damage.** A physical hit that starts a fresh cooldown can also damage the elytra itself: `percent` of max durability, a `flat` amount, or scaled with the damage taken.

**G-force.** The mod watches every player every tick and turns sudden changes in velocity into a G load - game-scale numbers, not real-world ones. It applies to all elytra flight and falls: being shot down, hard landings, snapping a fresh elytra open mid-fall, and above all intense flight maneuvers. Crossing the effect threshold pulses darkness and nausea; crossing the damage threshold deals trauma through its own damage type, which can disable a working elytra all on its own - no hit required. See [How G-force works](#how-g-force-works).

**Freefall shock.** When the disable happens mid-flight (by damage or by G-force alone), the victim gets an immediate shock: a jarring sound, a violent snap of the view, and the first wave of nausea and darkness. From there the G-force system keeps the effects alive for as long as the player has not stabilised, and drops them once they do.

**Decaying tumble.** The mid-flight disable also starts a random decaying camera spin - a random direction and pitch drift, chosen once and faded out over exactly eight seconds. The fade is linear and gradual: no early collapse, no abrupt cutoff, the spin eases to a full stop at the end of the window. Its magnitude is derived from the speed the player had at the moment of the disable, so a slow target tumbles gently while a fast one tumbles faster, and no tumble can exceed the hard cap of 6 deg/tick (120 deg/s). It uses the vanilla `/rotate` packet, so it works for clients without the mod.

**Death by G-force.** Trauma from extreme G load uses its own damage type with a matching death message: "*player* blacked out from extreme G-forces."

## How G-force works

There is no built-in G-force in Minecraft, so the mod derives one from acceleration - the sudden change in velocity between game ticks, not the speed itself - and converts it on a scale that makes sense for the game:

```
G load  = |velocity change per tick| × delta_to_gs      (smoothed over a few ticks)
damage per second = (G load − threshold_gs) × damage_per_gs_per_second   (only above the threshold)
```

With the default `delta_to_gs = 100`, one block/tick of sudden change reads as 100 Gs. That puts everyday events in a readable range:

| Event | Velocity change | G load (`delta_to_gs = 100`) |
|---|---|---|
| Steady glide | ~0.02 blocks/tick per tick | **~2 Gs - quiet** |
| First ticks of free fall | 0.078 blocks/tick per tick | **~8 Gs, fading to 0 at terminal velocity** |
| Rocket boost (climb, turn, cruise) | - | **exempt - no G-force while boosting** |
| Ground takeoff (jump + glide start) | ~0.6 blocks/tick | **exempt - the takeoff grace ignores it** |
| Arrow hit that starts the fall | ~0.5 blocks/tick | **~18 Gs smoothed spike** |
| Violent elytra maneuver (full 90° snap) | ~1.0 blocks/tick | **~35 Gs smoothed spike - hurts and disables** |
| Re-engaging a fresh elytra mid-fall | 1.5 - 2.5 blocks/tick | **~50 - 90 Gs smoothed spike** |
| Landing out of terminal velocity | 3.92 blocks/tick | **~137 Gs smoothed spike (392 raw), lethal burst** |

The raw per-tick change is smoothed with an exponential average (35% of each new tick mixed in), so single-tick spikes read as short bursts instead of flickers and one weird packet cannot fake a blackout.

**Who is measured.** Every gliding player, and every airborne player wearing an elytra whose fall has reached flight-like speed (about an 8 block drop). Walking, hopping, climbing, swimming, and short drops stay outside the model - a hop never reads as a blackout.

**When effects apply.** The moment the smoothed load crosses `g_force.effect_threshold_gs` (default 5 Gs), darkness and nausea start, and the nausea keeps refreshing while the player has not stabilised - high load, or simply still falling unstabilised. A steady glide sits around 2 Gs and never triggers; a hard pull-up, a rocket-boosted climb gone wrong, or the acceleration of a fresh free fall does. When the player stabilises - the load drops below the threshold and they land or glide it off - the sustained nausea is cleared about half a second later, so a stabilizing pilot shakes it off almost immediately, while a victim still tumbling keeps it refreshed for the whole fall. `nausea_duration_seconds` (default 3) is the length of one nausea application; vanilla will not render instances shorter than its blend-out window, so applications are floored at 4.5 seconds, which keeps the warp visible at short settings.

**The rocket exemption.** While a firework rocket is boosting a player, G-force does not apply to them at all: no darkness, no nausea, no damage - boost climbs, rocket turns, and boost landings are exempt, and the load model is pinned to zero during the boost so violent maneuvers leave no residue after it burns out. For non-rocket takeoffs, a low-speed glide start still opens a short (0.75 s) grace window, because the jump and its redirect onto the glide path would otherwise read as a 20 - 30 G spike that no pilot actually feels. Re-entering flight at speed - the fresh-elytra swap mid-fall - is not a takeoff and still counts.

**When it hurts.** Above `g_force.threshold_gs` (default 25 Gs) the load deals damage through the `elytra_combat:g_force` damage type. Sustained free fall (~8 Gs) never reaches it - trauma is for sudden events only. And because that damage is a regular hurt, it runs through the same disable pipeline as any hit: a maneuver or landing violent enough to hurt also **disables the elytra**, even though nothing shot the player down. The G damage counts as physical trauma, so it can wear durability too. At the default `damage_per_gs_per_second` of 5.0 a violent maneuver costs a couple of hearts, a 20-block landing about seven, and a terminal-velocity landing is simply death.

**Filtering G damage.** G-force damage appears as `elytra_combat:g_force` in the damage filter. The default empty denylist triggers on it; add that id to `damage_types` if G-force should black out players but never disable their elytras.

Practical tuning:
- **Harsher impacts:** raise `damage_per_gs_per_second` further or lower `threshold_gs`; the default 5.0 already makes hard landings lethal.
- **Touchier blackout:** lower `g_force.effect_threshold_gs`; 0 keeps darkness and nausea up whenever the player is gliding or falling fast at all, 500 effectively disables both.
- **Softer:** drop `damage_per_gs_per_second` toward 0.5, raise `threshold_gs`; `enabled: false` turns the damage off entirely (darkness and nausea keep working - they read the same load).
- **Do not touch** `delta_to_gs` unless you want to change the whole scale - it shifts every number above at once. 100 is the game-scale conversion; 40 was the old real-world one.

## Configuration

The first launch creates `config/elytra-combat.json`:

```json
{
  "disable_duration_seconds": 30,
  "damage_filter": {
    "mode": "denylist",
    "damage_types": [],
    "match_direct_player_damage": false,
    "match_indirect_player_damage": false,
    "match_player_owned_entity_damage": false,
    "ignore_fall_damage": true
  },
  "absorption_bypasses_disable": true,
  "freefall": {
    "nausea_strength": 4,
    "nausea_duration_seconds": 3,
    "darkness": true,
    "shock_sound": true,
    "view_snap": true,
    "spin_intensity": 1.0
  },
  "g_force": {
    "enabled": true,
    "delta_to_gs": 100.0,
    "threshold_gs": 25.0,
    "damage_per_gs_per_second": 5.0,
    "effect_threshold_gs": 5.0
  },
  "durability_damage": {
    "enabled": true,
    "mode": "percent",
    "percent": 10.0,
    "flat": 30,
    "damage_scale": 2.0
  }
}
```

- `disable_duration_seconds`: how long a disabled elytra stays unusable (1 to ~3.4 years, technically).
- `damage_filter`:
  - The entries selected by `damage_types` and the three attribution switches form one matched set. `allowlist` triggers on that set; `denylist` triggers on everything outside it. The default empty denylist therefore accepts every damage source *except* the one below.
  - `ignore_fall_damage` (default true): fall damage never disables an elytra. Landing hard is punished by vanilla fall damage and the G-force system instead. Note that crashing into a wall while gliding is *not* fall damage - it is kinetic damage and still disables the elytra, and G-force trauma is its own `elytra_combat:g_force` type, also unaffected by this switch.
- `absorption_bypasses_disable`: when true, a hit that damages only absorption hearts causes neither disable nor durability loss; a hit that reaches real health always triggers.
- `freefall`:
  - `nausea_strength`: 0 (disabled) to 10; the applied amplifier is strength minus one.
  - `nausea_duration_seconds` (1-120): length of one nausea application. The effect re-applies seamlessly while the player has not stabilised - high load, or still falling unstabilised - and is cleared about half a second after they land or glide it off, so the setting is the length of each application, not a countdown to relief. Applications are floored at 4.5 seconds because vanilla cannot render shorter ones - see [How G-force works](#how-g-force-works).
  - `darkness`: toggles the pulsing blackout effect, which follows the G load while it is above `g_force.effect_threshold_gs`.
  - `shock_sound`, `view_snap`: toggles for the individual shock effects of a mid-flight disable.
  - `spin_intensity`: 0 disables the tumble; up to 3.0 scales the decaying spin within its speed-derived cap. At intensity 1.0 a player who was shot down at full elytra speed can spin at up to ~118 deg/s, fading linearly to a full stop over eight seconds.
- `g_force`: set `enabled` to false to turn G-force damage off entirely, or tune the model - see [How G-force works](#how-g-force-works) for worked examples of `delta_to_gs`, `threshold_gs`, `damage_per_gs_per_second`, and `effect_threshold_gs`.
- `durability_damage`: mode can be `percent`, `flat`, or `damage_scaled`. Percentage uses the elytra's maximum durability and rounds up; damage scaling uses final health plus absorption damage (0.1 to 20 durability per damage point). Poison, instant damage, status/magic damage, fire, drowning, starvation, freezing, falls, suffocation, and void damage never damage the elytra.

Upgrading from 1.3.1: nausea now also keeps refreshing while the player is simply still falling unstabilised (not only while the load is high), and rockets grant full G-force immunity while boosting. No config keys changed.

Upgrading from 1.3.0: `damage_per_gs_per_second` default moved from 0.4 to 5.0 - G-force trauma is now genuinely dangerous by default, so revisit it if you had tuned it. Nausea now refreshes while the player has not stabilised and is cleared shortly after they do (instead of running out on its own), and ground takeoffs are exempt from the G model.

Upgrading from 1.2.2: the G-force keys `darkness_threshold_gs` and `settle_seconds` under `freefall` are gone - the first became `g_force.effect_threshold_gs` and now also gates nausea, the second was dropped because the always-on monitor no longer needs a settle window. `delta_to_gs` moved from 40 (real-world scale) to 100 (game scale) and `threshold_gs` from 15 to 25; old values are ignored and the new defaults apply. `nausea_duration_seconds` moved from 12 to 3 and its minimum from 4 to 1. Every change is explained in [CHANGELOG.md](CHANGELOG.md).

## Version history

See [CHANGELOG.md](CHANGELOG.md).

## Permissions

On a dedicated server the config belongs to the server. `/elytracombat reload` applies JSON changes without a restart and requires gamemaster-level permission (every default operator has it), so only operators can change the live config. The Mod Menu screen edits the same local file and applies it immediately in single-player; on a remote server its page is read-only and tells the player to ask an operator.

## Building

Minecraft 26.1.1 requires Java 25. Run `./gradlew build`; the distributable jar is produced under `build/libs` and unit tests run as part of the build.

## License

[MIT](LICENSE)
