package com.yesmenn.creates.compat.electroenergetics;

import com.yesmenn.creates.YesmennCreates;
import com.yesmenn.creates.compat.electroenergetics.surfacewire.FlatWireMaterial;
import com.yesmenn.creates.compat.electroenergetics.surfacewire.SurfaceWireItem;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/** Items that only exist when Create: Electro Energetics is installed. */
public final class EEItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(YesmennCreates.MODID);

    private static final Map<FlatWireMaterial, DeferredItem<SurfaceWireItem>> FLAT_WIRES = new EnumMap<>(FlatWireMaterial.class);

    public static final DeferredItem<SurfaceWireItem> COPPER_FLAT_WIRE = flatWire(FlatWireMaterial.COPPER);
    public static final DeferredItem<SurfaceWireItem> ELECTRUM_FLAT_WIRE = flatWire(FlatWireMaterial.ELECTRUM);
    public static final DeferredItem<SurfaceWireItem> IRON_FLAT_WIRE = flatWire(FlatWireMaterial.IRON);
    public static final DeferredItem<SurfaceWireItem> INSULATED_COPPER_FLAT_WIRE = flatWire(FlatWireMaterial.INSULATED_COPPER);
    public static final DeferredItem<SurfaceWireItem> INSULATED_ELECTRUM_FLAT_WIRE = flatWire(FlatWireMaterial.INSULATED_ELECTRUM);
    public static final DeferredItem<SurfaceWireItem> INSULATED_IRON_FLAT_WIRE = flatWire(FlatWireMaterial.INSULATED_IRON);
    public static final DeferredItem<SurfaceWireItem> INVISIBLE_FLAT_WIRE = flatWire(FlatWireMaterial.INVISIBLE);

    private static DeferredItem<SurfaceWireItem> flatWire(FlatWireMaterial material) {
        DeferredItem<SurfaceWireItem> item = ITEMS.register(material.itemName(),
                () -> new SurfaceWireItem(new Item.Properties(), material));
        FLAT_WIRES.put(material, item);
        return item;
    }

    public static Item forMaterial(FlatWireMaterial material) {
        return FLAT_WIRES.get(material).get();
    }

    static void addToCreativeTab(CreativeModeTab.Output output) {
        FLAT_WIRES.values().forEach(item -> output.accept(item.get()));
    }

    private EEItems() {}
}
