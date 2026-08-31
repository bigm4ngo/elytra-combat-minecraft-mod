# Elytra Combat

A server-authoritative Fabric mod for Minecraft 26.1.1. Taking configured damage while wearing an elytra disables that exact elytra for a configurable period. If the player was flying, flight ends immediately with velocity preserved - and the real trouble begins.

Clients do not need the mod when joining a dedicated server. Install it normally in a client instance to use it in single-player. Mod Menu is optional and provides the configuration screen.

## Features

**Elytra disable on hit.** When its wearer takes damage matched by the [damage filter](#configuration), the worn elytra stops working for `disable_duration_seconds`. Hits never extend an ongoing cooldown, and the countdown runs in real time - it matches the vanilla cooldown overlay exactly, whether the elytra is worn, carried, or stored.

**Elytra durability damage.** A physical hit that starts a fresh cooldown can also damage the elytra itself: `percent` of max durability, a `flat` amount, or scaled with the damage taken.

**Freefall shock.** If the disable happens mid-flight, the victim gets an immediate shock: a jarring sound, a violent snap of the view, pulsing darkness, and nausea that ramps to full strength and stays there for the whole fall. Falling off something later while wearing a disabled elytra is an ordinary fall and does not repeat the shock.

**Decaying tumble.** The mid-flight disable also starts a random decaying camera spin (a random direction and pitch drift, chosen once and faded out over the fall). Its magnitude is derived from the speed the player had at the moment of the disable, capped at 8 deg/tick, so a slow target tumbles gently while a fast one tumbles violently - and the tumble never exceeds what the disable-moment speed allows. It uses the vanilla `/rotate` packet, so it works for clients without the mod.

**G-force damage (toggleable).** The mod models G load as a function of downward speed: `Gs = downward speed (blocks/tick) * speed_to_gs`. With defaults, vanilla terminal velocity (~3.92 blocks/tick) is about 47 Gs, a load reached after roughly 13 blocks of free fall. Each G above `threshold_gs` deals `damage_per_gs_per_second` damage per second as kinetic damage, accumulated and applied in half-second batches. This applies to any free fall made while wearing a disabled elytra, including ordinary falls.

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
    "match_player_owned_entity_damage": false
  },
  "absorption_bypasses_disable": true,
  "freefall": {
    "nausea_strength": 4,
    "nausea_duration_seconds": 12,
    "darkness": true,
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
- `damage_filter`: the entries selected by `damage_types` and the three attribution switches form one matched set. `allowlist` triggers on that set; `denylist` triggers on everything outside it. The default empty denylist therefore accepts every damage source.
- `absorption_bypasses_disable`: when true, a hit that damages only absorption hearts causes neither disable nor durability loss; a hit that reaches real health always triggers.
- `freefall`:
  - `nausea_strength`: 0 (disabled) to 10; the applied amplifier is strength minus one.
  - `nausea_duration_seconds`: length of one nausea application (4-120). It is re-applied seamlessly while the victim keeps falling, so a value of 12 already covers most falls.
  - `darkness`, `shock_sound`, `view_snap`: toggles for the individual shock effects.
  - `spin_intensity`: 0 disables the tumble; up to 3.0 scales the decaying spin within the speed-derived cap.
- `g_force`: set `enabled` to false to turn G-force damage off entirely, or tune `speed_to_gs`, `threshold_gs`, and `damage_per_gs_per_second`.
- `durability_damage`: mode can be `percent`, `flat`, or `damage_scaled`. Percentage uses the elytra's maximum durability and rounds up; damage scaling uses final health plus absorption damage (0.1 to 20 durability per damage point). Poison, instant damage, status/magic damage, fire, drowning, starvation, freezing, falls, suffocation, and void damage never damage the elytra.

## Permissions

On a dedicated server the config belongs to the server. `/elytracombat reload` applies JSON changes without a restart and requires gamemaster-level permission (every default operator has it), so only operators can change the live config. The Mod Menu screen edits the same local file and applies it immediately in single-player; on a remote server its page is read-only and tells the player to ask an operator.

## Building

Minecraft 26.1.1 requires Java 25. Run `./gradlew build`; the distributable jar is produced under `build/libs` and unit tests run as part of the build.

## License

[MIT](LICENSE)
