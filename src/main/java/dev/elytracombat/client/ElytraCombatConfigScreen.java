package dev.elytracombat.client;

import dev.elytracombat.config.ConfigManager;
import dev.elytracombat.config.ElytraCombatConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ElytraCombatConfigScreen extends Screen {
    private final Screen parent;
    private final ElytraCombatConfig config;
    private EditBox duration;
    private EditBox percent;
    private EditBox flat;
    private EditBox scale;
    private StringWidget error;

    public ElytraCombatConfigScreen(Screen parent) {
        super(Component.literal("Elytra Combat"));
        this.parent = parent;
        this.config = ConfigManager.copyCurrent();
    }

    @Override
    protected void init() {
        if (minecraft.level != null && !minecraft.isLocalServer()) {
            initRemoteNotice();
            return;
        }

        int left = width / 2 - 155;
        addRenderableOnly(new StringWidget(left, 7, 310, 12, title, font));

        duration = numberField(left, 25, "Disable duration (seconds)", Integer.toString(config.disableDurationSeconds));
        addRenderableWidget(CycleButton.onOffBuilder(config.absorptionBypassesDisable)
                .create(left, 47, 310, 20, Component.literal("Absorption hearts bypass"), (button, value) ->
                        config.absorptionBypassesDisable = value));
        addRenderableWidget(CycleButton.onOffBuilder(config.durabilityDamage.enabled)
                .create(left, 69, 310, 20, Component.literal("Elytra durability damage"), (button, value) ->
                        config.durabilityDamage.enabled = value));
        addRenderableWidget(CycleButton.builder(value -> Component.literal(switch (value) {
                            case "percent" -> "Percent";
                            case "flat" -> "Flat";
                            case "damage_scaled" -> "Scaled with damage";
                            default -> value;
                        }), config.durabilityDamage.mode)
                .withValues(List.of("percent", "flat", "damage_scaled"))
                .create(left, 91, 310, 20, Component.literal("Durability mode"), (button, value) ->
                        config.durabilityDamage.mode = value));

        addRenderableOnly(new StringWidget(left, 118, 65, 10, Component.literal("Percent"), font));
        percent = new EditBox(font, left + 65, 113, 65, 20, Component.literal("Percent"));
        percent.setValue(Double.toString(config.durabilityDamage.percent));
        addRenderableWidget(percent);

        addRenderableOnly(new StringWidget(left + 150, 118, 45, 10, Component.literal("Flat"), font));
        flat = new EditBox(font, left + 195, 113, 115, 20, Component.literal("Flat durability"));
        flat.setValue(Integer.toString(config.durabilityDamage.flat));
        addRenderableWidget(flat);

        scale = numberField(left, 135, "Damage scale (0.1-20)", Double.toString(config.durabilityDamage.damageScale));

        addRenderableWidget(Button.builder(Component.literal("Damage source filter..."), button ->
                        minecraft.setScreen(new DamageFilterScreen(this, config)))
                .bounds(left, 159, 310, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Freefall & G-Force..."), button ->
                        minecraft.setScreen(new FreefallScreen(this, config)))
                .bounds(left, 181, 310, 20).build());

        error = new StringWidget(left, 205, 310, 10, Component.empty(), font);
        addRenderableOnly(error);
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
                .bounds(left, 217, 152, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(left + 158, 217, 152, 20).build());
    }

    private EditBox numberField(int left, int y, String label, String value) {
        addRenderableOnly(new StringWidget(left, y + 5, 165, 10, Component.literal(label), font));
        EditBox field = new EditBox(font, left + 170, y, 140, 20, Component.literal(label));
        field.setValue(value);
        addRenderableWidget(field);
        return field;
    }

    private void initRemoteNotice() {
        int left = width / 2 - 155;
        addRenderableOnly(new StringWidget(left, height / 2 - 35, 310, 12,
                Component.literal("This multiplayer server owns its Elytra Combat config."), font));
        addRenderableOnly(new StringWidget(left, height / 2 - 17, 310, 12,
                Component.literal("Edit config/elytra-combat.json on the server, then run"), font));
        addRenderableOnly(new StringWidget(left, height / 2 + 1, 310, 12,
                Component.literal("/elytracombat reload as an operator."), font));
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width / 2 - 75, height / 2 + 30, 150, 20).build());
    }

    private void save() {
        try {
            config.disableDurationSeconds = Integer.parseInt(duration.getValue().trim());
            config.durabilityDamage.percent = Double.parseDouble(percent.getValue().trim());
            config.durabilityDamage.flat = Integer.parseInt(flat.getValue().trim());
            config.durabilityDamage.damageScale = Double.parseDouble(scale.getValue().trim());
            ConfigManager.saveAndApply(config);
            minecraft.setScreen(parent);
        } catch (NumberFormatException exception) {
            error.setMessage(Component.literal("Use valid whole/decimal numbers."));
        } catch (IOException | IllegalArgumentException exception) {
            error.setMessage(Component.literal(exception.getMessage()));
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static final class FreefallScreen extends Screen {
        private final Screen parent;
        private final ElytraCombatConfig config;
        private final ElytraCombatConfig.Freefall originalFreefall;
        private final ElytraCombatConfig.GForce originalGForce;
        private EditBox nauseaStrength;
        private EditBox nauseaDuration;
        private EditBox spinIntensity;
        private EditBox effectThreshold;
        private EditBox threshold;
        private EditBox damagePerGs;
        private EditBox deltaToGs;
        private StringWidget error;

        private FreefallScreen(Screen parent, ElytraCombatConfig config) {
            super(Component.literal("Elytra Combat Freefall & G-Force"));
            this.parent = parent;
            this.config = config;
            this.originalFreefall = ConfigManager.copy(config.freefall, ElytraCombatConfig.Freefall.class);
            this.originalGForce = ConfigManager.copy(config.gForce, ElytraCombatConfig.GForce.class);
        }

        @Override
        protected void init() {
            int left = width / 2 - 155;
            addRenderableOnly(new StringWidget(left, 7, 310, 12, title, font));

            addRenderableOnly(new StringWidget(left, 30, 75, 10, Component.literal("Nausea strength"), font));
            nauseaStrength = new EditBox(font, left + 75, 25, 75, 20, Component.literal("Nausea strength"));
            nauseaStrength.setValue(Integer.toString(config.freefall.nauseaStrength));
            addRenderableWidget(nauseaStrength);

            addRenderableOnly(new StringWidget(left + 155, 30, 85, 10, Component.literal("Nausea duration"), font));
            nauseaDuration = new EditBox(font, left + 240, 25, 70, 20, Component.literal("Nausea duration"));
            nauseaDuration.setValue(Integer.toString(config.freefall.nauseaDurationSeconds));
            addRenderableWidget(nauseaDuration);

            addRenderableWidget(CycleButton.onOffBuilder(config.freefall.darkness)
                    .create(left, 47, 310, 20, Component.literal("Darkness pulses"), (button, value) ->
                            config.freefall.darkness = value));
            addRenderableWidget(CycleButton.onOffBuilder(config.freefall.shockSound)
                    .create(left, 69, 310, 20, Component.literal("Shock sound"), (button, value) ->
                            config.freefall.shockSound = value));
            addRenderableWidget(CycleButton.onOffBuilder(config.freefall.viewSnap)
                    .create(left, 91, 310, 20, Component.literal("View snap on disable"), (button, value) ->
                            config.freefall.viewSnap = value));
            spinIntensity = numberField(left, 113, "Spin intensity (0-3)", Double.toString(config.freefall.spinIntensity));
            addRenderableWidget(CycleButton.onOffBuilder(config.gForce.enabled)
                    .create(left, 135, 310, 20, Component.literal("G-force damage"), (button, value) ->
                            config.gForce.enabled = value));

            effectThreshold = numberField(left, 157, "Effect min Gs (0-500)", Double.toString(config.gForce.effectThresholdGs));

            addRenderableOnly(new StringWidget(left, 184, 55, 10, Component.literal("Threshold"), font));
            threshold = new EditBox(font, left + 55, 179, 45, 20, Component.literal("Threshold"));
            threshold.setValue(Double.toString(config.gForce.thresholdGs));
            addRenderableWidget(threshold);

            addRenderableOnly(new StringWidget(left + 100, 184, 55, 10, Component.literal("Dmg/Gs/s"), font));
            damagePerGs = new EditBox(font, left + 155, 179, 45, 20, Component.literal("Damage per Gs"));
            damagePerGs.setValue(Double.toString(config.gForce.damagePerGsPerSecond));
            addRenderableWidget(damagePerGs);

            addRenderableOnly(new StringWidget(left + 200, 184, 45, 10, Component.literal("Delta/Gs"), font));
            deltaToGs = new EditBox(font, left + 245, 179, 65, 20, Component.literal("Delta to Gs"));
            deltaToGs.setValue(Double.toString(config.gForce.deltaToGs));
            addRenderableWidget(deltaToGs);

            error = new StringWidget(left, 205, 310, 10, Component.empty(), font);
            addRenderableOnly(error);
            addRenderableWidget(Button.builder(Component.literal("Done"), button -> done())
                    .bounds(left, 217, 152, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> cancel())
                    .bounds(left + 158, 217, 152, 20).build());
        }

        private EditBox numberField(int left, int y, String label, String value) {
            addRenderableOnly(new StringWidget(left, y + 5, 165, 10, Component.literal(label), font));
            EditBox field = new EditBox(font, left + 170, y, 140, 20, Component.literal(label));
            field.setValue(value);
            addRenderableWidget(field);
            return field;
        }

        private void done() {
            try {
                config.freefall.nauseaStrength = Integer.parseInt(nauseaStrength.getValue().trim());
                config.freefall.nauseaDurationSeconds = Integer.parseInt(nauseaDuration.getValue().trim());
                config.freefall.spinIntensity = Double.parseDouble(spinIntensity.getValue().trim());
                config.gForce.effectThresholdGs = Double.parseDouble(effectThreshold.getValue().trim());
                config.gForce.thresholdGs = Double.parseDouble(threshold.getValue().trim());
                config.gForce.damagePerGsPerSecond = Double.parseDouble(damagePerGs.getValue().trim());
                config.gForce.deltaToGs = Double.parseDouble(deltaToGs.getValue().trim());
                config.validate();
                minecraft.setScreen(parent);
            } catch (NumberFormatException exception) {
                error.setMessage(Component.literal("Use valid whole/decimal numbers."));
            } catch (IllegalArgumentException exception) {
                error.setMessage(Component.literal(exception.getMessage()));
            }
        }

        private void cancel() {
            config.freefall = originalFreefall;
            config.gForce = originalGForce;
            minecraft.setScreen(parent);
        }

        @Override
        public void onClose() {
            cancel();
        }
    }

    private static final class DamageFilterScreen extends Screen {
        private final Screen parent;
        private final ElytraCombatConfig config;
        private final ElytraCombatConfig.DamageFilter originalFilter;
        private EditBox damageTypes;
        private StringWidget error;

        private DamageFilterScreen(Screen parent, ElytraCombatConfig config) {
            super(Component.literal("Elytra Combat Damage Filter"));
            this.parent = parent;
            this.config = config;
            this.originalFilter = ConfigManager.copy(config.damageFilter, ElytraCombatConfig.DamageFilter.class);
        }

        @Override
        protected void init() {
            int left = width / 2 - 155;
            addRenderableOnly(new StringWidget(left, 12, 310, 12, title, font));
            addRenderableWidget(CycleButton.builder(value -> Component.literal(value), config.damageFilter.mode)
                    .withValues(List.of("allowlist", "denylist"))
                    .create(left, 34, 310, 20, Component.literal("Filter mode"), (button, value) ->
                            config.damageFilter.mode = value));

            addRenderableOnly(new StringWidget(left, 61, 310, 10,
                    Component.literal("Damage type IDs (comma-separated)"), font));
            damageTypes = new EditBox(font, left, 73, 310, 20, Component.literal("Damage type IDs"));
            damageTypes.setMaxLength(2048);
            damageTypes.setValue(String.join(", ", config.damageFilter.damageTypes));
            addRenderableWidget(damageTypes);

            addRenderableWidget(CycleButton.onOffBuilder(config.damageFilter.matchDirectPlayerDamage)
                    .create(left, 99, 310, 20, Component.literal("Match direct player damage"), (button, value) ->
                            config.damageFilter.matchDirectPlayerDamage = value));
            addRenderableWidget(CycleButton.onOffBuilder(config.damageFilter.matchIndirectPlayerDamage)
                    .create(left, 123, 310, 20, Component.literal("Match indirect player damage"), (button, value) ->
                            config.damageFilter.matchIndirectPlayerDamage = value));
            addRenderableWidget(CycleButton.onOffBuilder(config.damageFilter.matchPlayerOwnedEntityDamage)
                    .create(left, 147, 310, 20, Component.literal("Match player-owned entity damage"), (button, value) ->
                            config.damageFilter.matchPlayerOwnedEntityDamage = value));
            addRenderableWidget(CycleButton.onOffBuilder(config.damageFilter.ignoreFallDamage)
                    .create(left, 171, 310, 20, Component.literal("Fall damage never disables"), (button, value) ->
                            config.damageFilter.ignoreFallDamage = value));

            error = new StringWidget(left, 197, 310, 10, Component.empty(), font);
            addRenderableOnly(error);
            addRenderableWidget(Button.builder(Component.literal("Done"), button -> done())
                    .bounds(left, 212, 152, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> cancel())
                    .bounds(left + 158, 212, 152, 20).build());
        }

        private void done() {
            List<String> values = new ArrayList<>();
            if (!damageTypes.getValue().isBlank()) {
                Arrays.stream(damageTypes.getValue().split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .forEach(values::add);
            }
            config.damageFilter.damageTypes = values;
            try {
                config.validate();
                minecraft.setScreen(parent);
            } catch (IllegalArgumentException exception) {
                error.setMessage(Component.literal(exception.getMessage()));
            }
        }

        private void cancel() {
            config.damageFilter = originalFilter;
            minecraft.setScreen(parent);
        }

        @Override
        public void onClose() {
            cancel();
        }
    }
}
