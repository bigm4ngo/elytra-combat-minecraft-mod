# Elytra Combat

A server-authoritative Fabric mod for Minecraft 26.1.1. Taking configured damage while wearing an elytra disables that exact elytra for a configurable period. If the player was flying, flight ends immediately with velocity preserved - and the real trouble begins.

Clients do not need the mod when joining a dedicated server. Install it normally in a client instance to use it in single-player. Mod Menu is optional and provides the configuration screen.

## Features

**Elytra disable on hit.** When its wearer takes damage matched by the [damage filter](#configuration), the worn elytra stops working for `disable_duration_seconds`. Hits never extend an ongoing cooldown, and the countdown runs in real time - it matches the vanilla cooldown overlay exactly, whether the elytra is worn, carried, or stored.

**Elytra durability damage.** A physical hit that starts a fresh cooldown can also damage the elytra itself: `percent` of max durability, a `flat` amount, or scaled with the damage taken.

**G-force.** The mod watches every player every tick and turns sudden changes in velocity into a G load - game-scale numbers, not real-world ones. It applies to all elytra flight and falls: being shot down, hard landings, snapping a fresh elytra open mid-fall, and above all intense flight maneuvers. Crossing the effect threshold pulses darkness and nausea; crossing the damage threshold deals trauma through its own damage type, which can disable a working elytra all on its own - no hit required. See [How G-force works](#how-g-force-works).

**Freefall shock.** When the disable happens mid-flight (by damage or by G-force alone), the victim gets an immediate shock: a jarring sound, a violent snap of the view, and the first wave of nausea and darkness. From there the G-force system keeps the effects alive for as long as the load stays high.

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
| Arrow hit that starts the fall | ~0.5 blocks/tick | **~18 Gs smoothed spike** |
| Violent elytra maneuver (full 90° snap) | ~1.0 blocks/tick | **~35 Gs smoothed spike - hurts and disables** |
| Re-engaging a fresh elytra mid-fall | 1.5 - 2.5 blocks/tick | **~50 - 90 Gs smoothed spike** |
| Landing out of terminal velocity | 3.92 blocks/tick | **~137 Gs smoothed spike (392 raw), ~2.2 hearts burst** |

The raw per-tick change is smoothed with an exponential average (35% of each new tick mixed in), so single-tick spikes read as short bursts instead of flickers and one weird packet cannot fake a blackout.

**Who is measured.** Every gliding player, and every airborne player wearing an elytra whose fall has reached flight-like speed (about an 8 block drop). Walking, hopping, climbing, swimming, and short drops stay outside the model - a hop never reads as a blackout.

**When effects apply.** The moment the smoothed load crosses `g_force.effect_threshold_gs` (default 5 Gs), darkness and nausea start and keep refreshing while the load stays high. A steady glide sits around 2 Gs and never triggers; a hard pull-up, a rocket-boosted climb gone wrong, or the acceleration of a fresh free fall does. Once the load drops - terminal velocity reached, flight stabilized - the effects finish on their own within seconds. `nausea_duration_seconds` (default 3) is the length of one application: vanilla ramps nausea in over 7.5 seconds and will not render shorter instances at all, so the monitor floors applications at 4.5 seconds, which holds the warp at ~60% strength while the load persists and still ends it within a couple of seconds of stabilizing.

**When it hurts.** Above `g_force.threshold_gs` (default 25 Gs) the load deals damage through the `elytra_combat:g_force` damage type. Sustained free fall (~8 Gs) never reaches it - trauma is for sudden events only. And because that damage is a regular hurt, it runs through the same disable pipeline as any hit: a maneuver or landing violent enough to hurt also **disables the elytra**, even though nothing shot the player down. The G damage counts as physical trauma, so it can wear durability too.

**Filtering G damage.** G-force damage appears as `elytra_combat:g_force` in the damage filter. The default empty denylist triggers on it; add that id to `damage_types` if G-force should black out players but never disable their elytras.

Practical tuning:
- **Harsher impacts:** raise `damage_per_gs_per_second` (0.8 - 1.0 turns hard landings into serious trauma) or lower `threshold_gs`.
- **Touchier blackout:** lower `g_force.effect_threshold_gs`; 0 keeps darkness and nausea up whenever the player is gliding or falling fast at all, 500 effectively disables both.
- **Softer:** raise `threshold_gs` or lower `damage_per_gs_per_second`; `enabled: false` turns the damage off entirely (darkness and nausea keep working - they read the same load).
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
    "damage_per_gs_per_second": 0.4,
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
  - `nausea_duration_seconds` (1-120): length of one nausea application. Vanilla's blend mechanics are noted under [How G-force works](#how-g-force-works): applications are floored at 4.5 seconds so the distortion is visible at all, and the effect re-applies seamlessly while the load persists, so the setting mostly controls how strong the warp is and how fast it dies down once the player stabilizes.
  - `darkness`: toggles the pulsing blackout effect, which follows the G load while it is above `g_force.effect_threshold_gs`.
  - `shock_sound`, `view_snap`: toggles for the individual shock effects of a mid-flight disable.
  - `spin_intensity`: 0 disables the tumble; up to 3.0 scales the decaying spin within its speed-derived cap. At intensity 1.0 a player who was shot down at full elytra speed can spin at up to ~118 deg/s, fading linearly to a full stop over eight seconds.
- `g_force`: set `enabled` to false to turn G-force damage off entirely, or tune the model - see [How G-force works](#how-g-force-works) for worked examples of `delta_to_gs`, `threshold_gs`, `damage_per_gs_per_second`, and `effect_threshold_gs`.
- `durability_damage`: mode can be `percent`, `flat`, or `damage_scaled`. Percentage uses the elytra's maximum durability and rounds up; damage scaling uses final health plus absorption damage (0.1 to 20 durability per damage point). Poison, instant damage, status/magic damage, fire, drowning, starvation, freezing, falls, suffocation, and void damage never damage the elytra.

Upgrading from 1.2.2: the G-force keys `darkness_threshold_gs` and `settle_seconds` under `freefall` are gone - the first became `g_force.effect_threshold_gs` and now also gates nausea, the second was dropped because the always-on monitor no longer needs a settle window. `delta_to_gs` moved from 40 (real-world scale) to 100 (game scale) and `threshold_gs` from 15 to 25; old values are ignored and the new defaults apply. `nausea_duration_seconds` moved from 12 to 3 and its minimum from 4 to 1. Every change is explained in [CHANGELOG.md](CHANGELOG.md).

## Version history

See [CHANGELOG.md](CHANGELOG.md).

## Permissions

On a dedicated server the config belongs to the server. `/elytracombat reload` applies JSON changes without a restart and requires gamemaster-level permission (every default operator has it), so only operators can change the live config. The Mod Menu screen edits the same local file and applies it immediately in single-player; on a remote server its page is read-only and tells the player to ask an operator.

## Building

Minecraft 26.1.1 requires Java 25. Run `./gradlew build`; the distributable jar is produced under `build/libs` and unit tests run as part of the build.

## License

[MIT](LICENSE)
