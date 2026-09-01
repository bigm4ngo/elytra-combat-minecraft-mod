# Elytra Combat

A server-authoritative Fabric mod for Minecraft 26.1.1. Taking configured damage while wearing an elytra disables that exact elytra for a configurable period. If the player was flying, flight ends immediately with velocity preserved - and the real trouble begins.

Clients do not need the mod when joining a dedicated server. Install it normally in a client instance to use it in single-player. Mod Menu is optional and provides the configuration screen.

## Features

**Elytra disable on hit.** When its wearer takes damage matched by the [damage filter](#configuration), the worn elytra stops working for `disable_duration_seconds`. Hits never extend an ongoing cooldown, and the countdown runs in real time - it matches the vanilla cooldown overlay exactly, whether the elytra is worn, carried, or stored.

**Elytra durability damage.** A physical hit that starts a fresh cooldown can also damage the elytra itself: `percent` of max durability, a `flat` amount, or scaled with the damage taken.

**Freefall shock.** If the disable happens mid-flight, the victim gets an immediate shock: a jarring sound, a violent snap of the view, and nausea that ramps to full strength and stays there for the whole fall. The blackout hits right with the shot and clears up as the fall approaches terminal velocity (see [How G-force works](#how-g-force-works)), and a hard landing flashes it briefly. Falling off something later while wearing a disabled elytra is an ordinary fall and does not repeat the shock.

**Decaying tumble.** The mid-flight disable also starts a random decaying camera spin - a random direction and pitch drift, chosen once and faded out over the fall. Its magnitude is derived from the speed the player had at the moment of the disable, so a slow target tumbles gently while a fast one tumbles faster, and no tumble can exceed the hard cap of 6 deg/tick (120 deg/s). It uses the vanilla `/rotate` packet, so it works for clients without the mod.

**G-force damage (toggleable).** The mod models G load from the sudden change in velocity between ticks. Steady free fall stops registering as terminal velocity is reached, while abrupt events - the hit itself, the landing, snapping a fresh elytra open mid-fall - spike hard. See [How G-force works](#how-g-force-works) below for the full explanation. It applies to any fall made while wearing a disabled elytra, including ordinary ones, and keeps measuring through the short settle window that follows a landing.

## How G-force works

There is no built-in G-force in Minecraft, so the mod derives one the way a body actually feels it: from acceleration - the sudden change in velocity between game ticks, not the speed itself.

```
G load  = |velocity change per tick| × delta_to_gs      (smoothed over a few ticks)
damage per second = (G load − threshold_gs) × damage_per_gs_per_second   (only above the threshold)
```

The consequence of measuring change instead of speed: a steady fall cancels itself out. Vanilla gravity accelerates a fall by 0.0784 blocks/tick each tick at first, but air drag grows with speed, so the load starts around 3 Gs and fades to zero as terminal velocity (~3.92 blocks/tick) is reached - a skydiver in a stable fall feels nothing. Sudden events are the opposite: they cram a large velocity change into one tick.

| Event | Velocity change | G load (`delta_to_gs = 40`) |
|---|---|---|
| First ticks of free fall | 0.078 blocks/tick per tick | **~3.1 Gs, fading to 0 at terminal velocity** |
| Arrow hit that starts the fall | ~0.5 blocks/tick | **~7 Gs smoothed spike** |
| Re-engaging a fresh elytra mid-fall | 1.5 - 2.5 blocks/tick | **~20 - 35 Gs smoothed spike** |
| Landing out of terminal velocity | 3.92 blocks/tick | **~55 Gs smoothed spike (157 raw)** |

The raw per-tick change is smoothed with an exponential average (35% of each new tick mixed in), so single-tick spikes read as short bursts instead of flickers and one weird packet cannot fake a blackout.

What that means in play:

- **Being shot down** spikes the load with the knockback, so darkness lands with the hit. While the fall is still accelerating the load stays above `freefall.darkness_threshold_gs` (default 1.0 Gs) and the pulses keep refreshing; as terminal velocity cancels gravity the load fades below the threshold and the blackout eases off - by roughly 70% of terminal speed with defaults.
- **Landing** spikes the load again, so a hard impact flashes darkness for about two seconds and deals a burst of damage on top of vanilla fall damage (~0.7 hearts at terminal velocity with defaults). A soft landing does neither. Water and ladders count as impacts too - gentler than ground, but not free from terminal velocity.
- **Stabilizing flight with a fresh elytra** yanks the velocity from a fall onto the glide path, which spikes the load just like an impact. This is measured during the settle window that follows the fall, so swapping elytras mid-air is safe only if you do it gently.
- **Sustained falls never hurt**: the whole fall stays under the 15 G default threshold, so G-force damage is pure impact trauma on top of vanilla fall damage.

Practical tuning:
- **Harsher impacts:** raise `damage_per_gs_per_second` (0.8 - 1.0 turns a terminal-velocity landing into serious trauma) or lower `threshold_gs` (10 Gs makes mid-height landings bite too).
- **Longer blackout:** lower `freefall.darkness_threshold_gs`; 0 keeps darkness up for the whole session, 500 effectively disables it.
- **Longer aftermath:** raise `freefall.settle_seconds` to keep the nausea fade (and impact measurement) running longer after the fall ends.
- **Softer:** raise `threshold_gs` or lower `damage_per_gs_per_second`; `enabled: false` turns the damage off entirely (darkness keeps working - it reads the same load).
- **Do not touch** `delta_to_gs` unless you want to change the whole scale - it shifts every number above at once. 40 is close to the real-world conversion (1 G = 9.81 m/s²).

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
    "darkness_threshold_gs": 1.0,
    "shock_sound": true,
    "view_snap": true,
    "spin_intensity": 1.0,
    "settle_seconds": 2
  },
  "g_force": {
    "enabled": true,
    "delta_to_gs": 40.0,
    "threshold_gs": 15.0,
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
  - `nausea_duration_seconds`: length of one nausea application (4-120). It is re-applied seamlessly while the victim keeps falling; once the fall ends it is replaced by an instance sized to `settle_seconds` so the distortion blends out shortly after.
  - `darkness` + `darkness_threshold_gs`: toggles the pulsing blackout effect, and the minimum smoothed G load the fall must carry before it applies (0 keeps darkness up for the whole session, 500 effectively disables it). With the default 1.0 the blackout starts with the shot and fades as the fall nears terminal velocity.
  - `shock_sound`, `view_snap`: toggles for the individual shock effects.
  - `spin_intensity`: 0 disables the tumble; up to 3.0 scales the decaying spin within its speed-derived cap. At intensity 1.0 a player who was shot down at full elytra speed can spin at up to ~118 deg/s, slowing to ~16% within three seconds.
  - `settle_seconds` (1-10): how long the aftermath of a fall keeps measuring G spikes and fading effects after it ends - landing, water, climbing, or stabilizing flight on a fresh elytra.
- `g_force`: set `enabled` to false to turn G-force damage off entirely, or tune the model - see [How G-force works](#how-g-force-works) for worked examples of `delta_to_gs`, `threshold_gs`, and `damage_per_gs_per_second`.
- `durability_damage`: mode can be `percent`, `flat`, or `damage_scaled`. Percentage uses the elytra's maximum durability and rounds up; damage scaling uses final health plus absorption damage (0.1 to 20 durability per damage point). Poison, instant damage, status/magic damage, fire, drowning, starvation, freezing, falls, suffocation, and void damage never damage the elytra.

Upgrading from 1.2.1: the G-force key `speed_to_gs` was replaced by `delta_to_gs` (the model now measures velocity change, not speed). Old values are ignored and the new defaults apply; `threshold_gs` keeps its name but its meaning moved from fall speed to impact strength, so revisit it if you had tuned it.

## Permissions

On a dedicated server the config belongs to the server. `/elytracombat reload` applies JSON changes without a restart and requires gamemaster-level permission (every default operator has it), so only operators can change the live config. The Mod Menu screen edits the same local file and applies it immediately in single-player; on a remote server its page is read-only and tells the player to ask an operator.

## Building

Minecraft 26.1.1 requires Java 25. Run `./gradlew build`; the distributable jar is produced under `build/libs` and unit tests run as part of the build.

## License

[MIT](LICENSE)
