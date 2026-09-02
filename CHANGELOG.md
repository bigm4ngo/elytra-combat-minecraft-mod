# Version history

All notable changes to Elytra Combat are documented here. Releases before 1.2.0 predate this document.

## 1.3.2 - 2026-09-02

Bug fix and the rocket exemption.

### Fixed: nausea was not being applied

- Two problems stacked up. First, the mid-flight shock applied its nausea outside the tick loop, and the "clear after stabilising" counter - which had usually already spent itself while the player flew quietly - stripped the fresh nausea on the very next tick. Applying nausea now resets that counter, so the shock nausea survives to be handed over to the monitor.
- Second, the nausea refresh was gated purely on the G load, which fades as a fall approaches terminal velocity - so the nausea died mid-fall even though the player was still tumbling. Refresh now also covers **still falling unstabilised**: while a player wearing an elytra is falling faster than flight speed, the nausea keeps resetting regardless of the load. It is cleared only once they land or glide it off (about half a second later), exactly the "refreshed if not stabilised, cleared when stabilised" behavior.

### Rockets are immune to G-force

- While a firework rocket is boosting a player, G-force no longer applies to them at all: no darkness, no nausea, no damage - boost climbs, rocket turns, and boost landings are all exempt. The load model is also pinned to zero during the boost, so violent rocket maneuvers leave no residual load once the boost burns out. The separate ground-takeoff grace window remains for non-rocket takeoffs.

## 1.3.1 - 2026-09-02

Playtest follow-ups to the G-force rework.

### Nausea lifecycle restored

- `nausea_duration_seconds` is the length of one nausea application again. The effect is refreshed seamlessly while the player has still not stabilised (the load stays above the effect threshold), and it is **cleared** about half a second after the load drops or the player lands - instead of being left to run out on its own. Stabilising flight or touching ground now ends the nausea almost immediately, like the pre-1.3.0 behavior.
- Applications are still floored at 4.5 seconds under the hood: vanilla cannot render shorter nausea instances at all (anything at or under its 60-tick blend-out window bleeds to zero). With the 3-second default the nausea therefore lasts as long as the instability does, plus a half-second clear delay.

### Takeoff exception

- Starting a glide from the ground no longer triggers G-force effects. The jump and its redirect onto the glide path read as a 20-30 G spike within a few ticks, so low-speed glide starts (below flight speed) now open a 0.75-second grace window in which no G effects or damage apply. Re-entering flight at speed - the fresh-elytra swap mid-fall - is unaffected and still counts.

### Damage rate

- `g_force.damage_per_gs_per_second` default raised from 0.4 to **5.0**. At the default threshold of 25 Gs that means a violent maneuver costs a couple of hearts, a 20-block landing about seven, and a terminal-velocity landing is outright death. Drop toward 0.5 if that is too brutal.

## 1.3.0 - 2026-09-02

The G-force system becomes an always-on part of elytra flight instead of a consequence of being disabled, on numbers scaled to the game rather than the real world.

### G-force reworked: measured everywhere, felt everywhere

- The G monitor now watches **every player, every tick** - not just players inside a free fall session. Every gliding player and every airborne elytra wearer whose fall has reached flight-like speed (roughly an 8 block drop or more) is measured continuously.
- **Intense flight maneuvers now trigger G-force effects.** A violent pull-up, a full 90-degree snap, or any maneuver that pushes the smoothed load past the threshold pulses darkness and nausea - even though the elytra was never disabled and nothing hit the player.
- **Being shot, hit, or knocked around while flying** spikes the load exactly like before, but the effects are no longer tied to the disable event: any threshold crossing applies, wherever it comes from.
- **G-force damage can now disable the elytra on its own.** Trauma above `g_force.threshold_gs` runs through the regular hurt pipeline as the new `elytra_combat:g_force` damage type, so a maneuver or landing violent enough to hurt also starts a fresh disable - and its mid-flight shock if the player was flying. It also counts as physical trauma for durability damage.
- New scale: `delta_to_gs` default moved from 40 (the real-world conversion) to **100** - one block/tick of sudden change reads as 100 Gs. Vanilla gravity during a fall reads ~8 Gs, an arrow's knockback ~18, hard maneuvers ~35, and a terminal-velocity landing ~137 smoothed. `threshold_gs` default moved from 15 to 25; sustained free fall still never hurts.
- Darkness and nausea are now gated by the new `g_force.effect_threshold_gs` (default 5 Gs), replacing `freefall.darkness_threshold_gs` and applying to **both** effects. Steady gliding sits around 2 Gs and stays clean.
- The settle window (`freefall.settle_seconds`) is gone. With the monitor always running, the spikes at landing and re-engaging flight are measured anyway, and no replacement window is needed.

### Nausea: quick to end, reset while it lasts

- `freefall.nausea_duration_seconds` default dropped from 12 to **3**, and its minimum from 4 to 1.
- Nausea re-applies seamlessly while the G load stays above the effect threshold, and ends within a couple of seconds of the load clearing - stabilize the flight and the distortion fades almost immediately; keep falling unstabilized and it keeps resetting.
- Vanilla's blend mechanics are worked with, not around: nausea instances are floored at 4.5 seconds (shorter ones can never render any distortion), which holds the warp at ~60% strength with the 3-second default.

### Camera spin: longer, gradually fading

- The tumble no longer decays exponentially with a hard cutoff - which made it spin hard, collapse early, then visibly snap off. The fade is now **linear across a fixed eight-second window**, chosen so every tumble reaches exactly zero at the end of it: the amount of spin is unchanged, but it keeps playing and eases out gradually instead of dying at a point.

### Death message

- Dying to G-force trauma now shows a dedicated death message: "*player* blacked out from extreme G-forces." (new `elytra_combat:g_force` damage type with its own lang entry).

### Configuration

- `freefall.darkness_threshold_gs` → `g_force.effect_threshold_gs` (now also gates nausea).
- `freefall.settle_seconds` removed.
- `g_force.delta_to_gs`: 40 → 100 (valid range now 1-500). `g_force.threshold_gs`: 15 → 25.
- `freefall.nausea_duration_seconds`: 12 → 3 (valid range now 1-120).
- Old keys are ignored on load and defaults apply; revisit any tuned values.

## 1.2.2 - 2026-09-01

- **Tamer tumble.** The camera spin's hard cap halved from 240 deg/s to 120 deg/s, with pitch kept subtler; the spin a victim sees matches the violence of the event without throwing aim hopelessly far.
- **Acceleration-based G-force.** The model now measures the sudden *change* in velocity between ticks (`speed_to_gs` → `delta_to_gs`), the way a body actually feels G load. Steady free fall stops registering as terminal velocity is reached, while the hit that starts the fall, the landing, and re-engaging flight spike hard. Landing bursts are counted again.
- **Nausea winds down with the fall.** Sessions gained a short settle window: nausea is replaced by an instance that blends out over it instead of running its full configured length, so the distortion ends shortly after landing or stabilizing flight rather than lingering.
- Darkness is gated on the measured G load (`freefall.darkness_threshold_gs`, default 1.0): the blackout starts with the shot and fades as the fall nears terminal velocity.

## 1.2.1 - 2026-08-31

- Fixed a fatal hit freshly re-disabling an elytra that death cleanup had just cleared.
- Fixed landing with a disabled elytra swapped in mid-air counting as a mid-flight disable on touchdown.
- Stronger camera spin (peak raised to 240 deg/s at terminal velocity).
- Darkness is now gated on the G-force session instead of applying for the whole fall regardless of load.

## 1.2.0 - 2026-08-31

- **Freefall shock.** Disabling an elytra mid-flight now starts a session: a jarring sound, a violent snap of the view, and nausea for the duration of the fall.
- **Decaying camera tumble.** The disable samples a random decaying spin, derived from the victim's speed at the moment of the disable and delivered through vanilla rotation packets, so it works for clients without the mod.
- **G-force damage.** Falls register a G load from speed; above `threshold_gs` the load deals trauma on top of vanilla fall damage, so hard landings hurt even when the fall itself was survivable.
