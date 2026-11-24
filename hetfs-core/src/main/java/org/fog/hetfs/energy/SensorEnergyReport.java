package org.fog.hetfs.energy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.fog.entities.Sensor;
import org.fog.hetfs.core.HetFSSensor;

/** Energy summary of the battery-powered sensors of a simulation (module D7). */
public final class SensorEnergyReport {

    /** One line per sensor. */
    public static final class Entry {
        public final String sensor;
        public final long transmissions;
        public final double transmitJ;
        public final double idleJ;
        public final double remainingFraction;
        public final double exhaustedAtMs;
        public final double projectedLifetimeMs;

        Entry(String sensor, BatteryModel b, double nowMs) {
            this.sensor = sensor;
            this.transmissions = b.getTransmissions();
            this.transmitJ = b.getTransmitEnergyJ();
            this.idleJ = b.getIdleEnergyJ();
            this.remainingFraction = b.getRemainingFraction();
            this.exhaustedAtMs = b.getExhaustedAtMs();
            this.projectedLifetimeMs = b.projectedLifetimeMs(nowMs);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "%s: tx=%d E_tx=%.3f J E_idle=%.3f J remaining=%.2f%% exhausted=%s lifetime=%.1f s",
                    sensor, transmissions, transmitJ, idleJ, 100 * remainingFraction,
                    Double.isNaN(exhaustedAtMs) ? "no" : String.format(Locale.ROOT, "%.1f ms", exhaustedAtMs),
                    projectedLifetimeMs / 1000);
        }
    }

    private SensorEnergyReport() {
    }

    /** Brings every battery up to {@code nowMs} and summarises it; sensors without battery are skipped. */
    public static List<Entry> collect(List<? extends Sensor> sensors, double nowMs) {
        List<Entry> out = new ArrayList<>();
        for (Sensor s : sensors) {
            if (s instanceof HetFSSensor && ((HetFSSensor) s).getBattery() != null) {
                BatteryModel b = ((HetFSSensor) s).getBattery();
                b.advanceTo(nowMs);
                out.add(new Entry(s.getName(), b, nowMs));
            }
        }
        return out;
    }
}
