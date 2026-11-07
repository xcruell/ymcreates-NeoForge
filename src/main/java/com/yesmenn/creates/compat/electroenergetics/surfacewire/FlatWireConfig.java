package com.yesmenn.creates.compat.electroenergetics.surfacewire;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config of the flat wires ({@code yesmenncreates-flat-wires.toml}, synced to clients). */
public final class FlatWireConfig {
    public static final ModConfigSpec SPEC;

    static final ModConfigSpec.DoubleValue COPPER_RESISTANCE;
    static final ModConfigSpec.DoubleValue ELECTRUM_RESISTANCE;
    static final ModConfigSpec.DoubleValue IRON_RESISTANCE;
    static final ModConfigSpec.DoubleValue INSULATED_MAX_VOLTAGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Resistance of one block (= one meter) of flat wire, in ohms (insulated wires use their conductor)").push("resistance");
        COPPER_RESISTANCE = builder.defineInRange("copper", 0.004, 1e-6, 1000.0);
        ELECTRUM_RESISTANCE = builder.defineInRange("electrum", 0.0015, 1e-6, 1000.0);
        IRON_RESISTANCE = builder.defineInRange("iron", 0.012, 1e-6, 1000.0);
        builder.pop();
        builder.comment("Insulated flat wires").push("insulation");
        INSULATED_MAX_VOLTAGE = builder.comment("Voltage the insulation withstands, in volts")
                .defineInRange("maxVoltage", 10000.0, 1.0, 1.0e9);
        builder.pop();
        SPEC = builder.build();
    }

    private FlatWireConfig() {}

    /** Config value, or its default while the config is not loaded yet (e.g. in the main menu). */
    static double get(ModConfigSpec.DoubleValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }
}
