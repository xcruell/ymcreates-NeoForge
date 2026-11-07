package com.yesmenn.creates.compat.electroenergetics.surfacewire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Kind of flat wire: a conductor, optionally with a rubber insulation (thicker). Electrical values come from
 * {@link FlatWireConfig}; an insulated wire has the resistance of its conductor.
 */
public enum FlatWireMaterial implements StringRepresentable {
    COPPER("copper", 1, FlatWireConfig.COPPER_RESISTANCE, false, false),
    ELECTRUM("electrum", 1, FlatWireConfig.ELECTRUM_RESISTANCE, false, false),
    IRON("iron", 1, FlatWireConfig.IRON_RESISTANCE, false, false),
    INSULATED_COPPER("insulated_copper", 2, FlatWireConfig.COPPER_RESISTANCE, true, false),
    INSULATED_ELECTRUM("insulated_electrum", 2, FlatWireConfig.ELECTRUM_RESISTANCE, true, false),
    INSULATED_IRON("insulated_iron", 2, FlatWireConfig.IRON_RESISTANCE, true, false),
    /** Copper values, but only drawn while an invisible flat wire is held. */
    INVISIBLE("invisible", 1, FlatWireConfig.COPPER_RESISTANCE, false, true);

    /** Name used before 0.0.9 for the insulated copper wire. */
    private static final String LEGACY_INSULATED = "insulated";

    public static final Codec<FlatWireMaterial> CODEC = Codec.STRING.comapFlatMap(FlatWireMaterial::byName,
            FlatWireMaterial::getSerializedName);

    private final String name;
    private final int thicknessPixels;
    private final ModConfigSpec.DoubleValue resistance;
    private final boolean insulated;
    private final boolean hidden;

    FlatWireMaterial(String name, int thicknessPixels, ModConfigSpec.DoubleValue resistance, boolean insulated,
                     boolean hidden) {
        this.name = name;
        this.thicknessPixels = thicknessPixels;
        this.resistance = resistance;
        this.insulated = insulated;
        this.hidden = hidden;
    }

    private static DataResult<FlatWireMaterial> byName(String name) {
        if (LEGACY_INSULATED.equals(name)) {
            return DataResult.success(INSULATED_COPPER);
        }
        for (FlatWireMaterial material : values()) {
            if (material.name.equals(name)) {
                return DataResult.success(material);
            }
        }
        return DataResult.error(() -> "Unknown flat wire material: " + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** Registry name of the item. */
    public String itemName() {
        return name + "_flat_wire";
    }

    /** Edge length of the wire's square cross-section in pixels. */
    public int thicknessPixels() {
        return thicknessPixels;
    }

    /** Half the thickness in grid units (1/32 block). */
    public int halfThickness() {
        return thicknessPixels;
    }

    /** Resistance in ohms of one block (= one meter) of this wire. */
    public double resistancePerBlock() {
        return FlatWireConfig.get(resistance);
    }

    public boolean insulated() {
        return insulated;
    }

    /** Only rendered (and pickable) while a wire of this material is held. */
    public boolean hidden() {
        return hidden;
    }

    /** Voltage the insulation withstands, in volts (only meaningful when {@link #insulated()}). */
    public double maxInsulationVoltage() {
        return FlatWireConfig.get(FlatWireConfig.INSULATED_MAX_VOLTAGE);
    }

    /** Block atlas texture of the wire. */
    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath("yesmenncreates", "block/" + itemName());
    }
}
