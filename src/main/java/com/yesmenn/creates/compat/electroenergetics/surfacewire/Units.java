package com.yesmenn.creates.compat.electroenergetics.surfacewire;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Formats electrical values with SI prefixes for tooltips. */
public final class Units {
    private static final String[] PREFIXES = {"µ", "m", "", "k", "M", "G"};
    private static final int NO_PREFIX = 2;

    private Units() {}

    public static Component resistancePerMeter(double ohmsPerMeter) {
        return Component.literal(format(ohmsPerMeter, "Ω/m"));
    }

    public static Component voltage(double volts) {
        return Component.literal(format(volts, "V"));
    }

    static String format(double value, String unit) {
        if (value == 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return (Double.isInfinite(value) ? "∞ " : "0 ") + unit;
        }
        int index = NO_PREFIX;
        double scaled = value;
        while (Math.abs(scaled) >= 1000 && index < PREFIXES.length - 1) {
            scaled /= 1000;
            index++;
        }
        while (Math.abs(scaled) < 1 && index > 0) {
            scaled *= 1000;
            index--;
        }
        String number = Math.abs(scaled) >= 100 ? String.format(Locale.ROOT, "%.0f", scaled)
                : Math.abs(scaled) >= 10 ? String.format(Locale.ROOT, "%.1f", scaled)
                : String.format(Locale.ROOT, "%.2f", scaled);
        return number + " " + PREFIXES[index] + unit;
    }
}
