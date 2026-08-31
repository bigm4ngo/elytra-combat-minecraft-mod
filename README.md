# Elytra Combat

A server-authoritative Fabric mod for Minecraft 26.1.1. Taking configured damage while wearing an elytra disables that exact elytra for a configurable period. If the player was flying, flight ends immediately with velocity preserved - and the real trouble begins.

Clients do not need the mod when joining a dedicated server. Install it normally in a client instance to use it in single-player. Mod Menu is optional and provides the configuration screen.

## Features

**Elytra disable on hit.** When its wearer takes damage matched by the [damage filter](#configuration), the worn elytra stops working for `disable_duration_seconds`. Hits never extend an ongoing cooldown, and the countdown runs in real time - it matches the vanilla cooldown overlay exactly, whether the elytra is worn, carried, or stored.

**Elytra durability damage.** A physical hit that starts a fresh cooldown can also damage the elytra itself: `percent` of max durability, a `flat` amount, or scaled with the damage taken.

**Freefall shock.** If the disable happens mid-flight, the victim gets an immediate shock: a jarring sound, a violent snap of the view, and nausea that ramps to full strength and stays there for the whole fall. Darkness pulses are part of the shock too, but they are gated on the fall's G load (see `darkness_threshold_gs`), so slow falls stay clear while fast ones black out. Falling off something later while wearing a disabled elytra is an ordinary fall and does not repeat the shock.

**Decaying tumble.** The mid-flight disable also starts a random decaying camera spin - a random direction and pitch drift, chosen once and faded out over the fall. Its magnitude is derived from the speed the player had at the moment of the disable, so a slow target tumbles gently while a fast one tumbles violently, and no tumble can exceed the hard cap of 12 deg/tick (240 deg/s). It uses the vanilla `/rotate` packet, so it works for clients without the mod.

**G-force damage (toggleable).** The mod models G load as a function of downward speed. See [How G-force works](#how-g-force-works) below for the full explanation. It applies to any free fall made while wearing a disabled elytra, including ordinary falls.

## How G-force works

There is no built-in G-force in Minecraft, so the mod derives one from the only thing that matters during an uncontrolled fall: how fast you are falling.

```
G load  = downward speed (blocks/tick) × speed_to_gs
damage per second = (G load − threshold_gs) × damage_per_gs_per_second   (only above the threshold)
```

The conversion factor exists because Minecraft's speed unit is *blocks per game tick* (20 ticks per second) and 1 block = 1 meter, while G-force is measured in multiples of Earth's gravity. `speed_to_gs` is the bridge between the two - it is not a physical constant, it is a gameplay dial:

| Downward speed | Speed in km/h | G load (`speed_to_gs = 12`) |
|---|---|---|
| 0.5 blocks/tick | 36 km/h | 6 Gs |
| 1.0 blocks/tick | 72 km/h | 12 Gs |
| 2.0 blocks/tick | 144 km/h | 24 Gs |
| 2.08 blocks/tick | 150 km/h | **25 Gs - default threshold reached (~45 blocks of fall)** |
| 3.0 blocks/tick | 216 km/h | 36 Gs |
| 3.92 blocks/tick | 282 km/h | **~47 Gs - vanilla terminal velocity** |

A falling player accelerates by 0.08 blocks/tick each tick, so the G load - and with it the damage - ramps up continuously through the fall. The default threshold of 25 Gs is reached after roughly **45 blocks** of free fall (about 2 seconds); anything shorter is free. At terminal velocity the defaults deal about **8.8 damage (4.4 hearts) per second**, on top of vanilla fall damage when you land.

Practical tuning:
- **Harsher:** raise `damage_per_gs_per_second` (0.8 - 1.0 turns a terminal-velocity fall into near-lethal trauma).
- **Punish from lower heights:** lower `threshold_gs` (15 Gs starts hurting after ~14 blocks).
- **Softer:** raise `threshold_gs` or lower `damage_per_gs_per_second`; `enabled: false` turns the system off.
- **Do not touch** `speed_to_gs` unless you want to change the whole scale - it shifts every number above at once. Raising it makes the same fall count as more Gs; lowering it makes the fall gentler.

The G load is also reused elsewhere: darkness pulses only apply once the fall exceeds `freefall.darkness_threshold_gs` Gs.

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
    "nausea_duration_seconds": 12,
    "darkness": true,
    "darkness_threshold_gs": 20.0,
    "shock_sound": true,
    "view_snap": true,
    "spin_intensity": 1.0
  },
  "g_force": {
    "enabled": true,
    "speed_to_gs": 12.0,
    "threshold_gs": 25.0,
    "damage_per_gs_per_second": 0.4
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
  - `ignore_fall_damage` (default true): fall damage never disables an elytra. Landing hard is punished by vanilla fall damage and the G-force system instead, and this keeps the trauma of a fall the wearer was already shot down in from disabling a backup elytra swapped in mid-fall. Note that crashing into a wall while gliding is *not* fall damage - it is kinetic damage and still disables the elytra.
- `absorption_bypasses_disable`: when true, a hit that damages only absorption hearts causes neither disable nor durability loss; a hit that reaches real health always triggers.
- `freefall`:
  - `nausea_strength`: 0 (disabled) to 10; the applied amplifier is strength minus one.
  - `nausea_duration_seconds`: length of one nausea application (4-120). It is re-applied seamlessly while the victim keeps falling, so a value of 12 already covers most falls.
  - `darkness` + `darkness_threshold_gs`: toggles the pulsing blackout effect, and the minimum G load the fall must reach before it applies (0 applies darkness on every mid-flight disable, 500 effectively disables it). With the default 20.0 the pulses start after roughly 25 blocks of falling.
  - `shock_sound`, `view_snap`: toggles for the individual shock effects.
  - `spin_intensity`: 0 disables the tumble; up to 3.0 scales the decaying spin within its speed-derived cap. At intensity 1.0 a player who was shot down at full elytra speed can spin at up to ~235 deg/s, slowing as the fall continues.
- `g_force`: set `enabled` to false to turn G-force damage off entirely, or tune the model - see [How G-force works](#how-g-force-works) for worked examples of `speed_to_gs`, `threshold_gs`, and `damage_per_gs_per_second`.
- `durability_damage`: mode can be `percent`, `flat`, or `damage_scaled`. Percentage uses the elytra's maximum durability and rounds up; damage scaling uses final health plus absorption damage (0.1 to 20 durability per damage point). Poison, instant damage, status/magic damage, fire, drowning, starvation, freezing, falls, suffocation, and void damage never damage the elytra.

## Permissions

On a dedicated server the config belongs to the server. `/elytracombat reload` applies JSON changes without a restart and requires gamemaster-level permission (every default operator has it), so only operators can change the live config. The Mod Menu screen edits the same local file and applies it immediately in single-player; on a remote server its page is read-only and tells the player to ask an operator.

## Building

Minecraft 26.1.1 requires Java 25. Run `./gradlew build`; the distributable jar is produced under `build/libs` and unit tests run as part of the build.

## License

[MIT](LICENSE)
