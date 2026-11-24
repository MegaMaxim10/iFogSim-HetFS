package org.fog.hetfs.energy;

/**
 * Battery of an IoT sensor (module D7).
 *
 * <p>iFogSim's {@code Sensor} has a data-generation rate and a tuple size but no energy
 * model, so sensor lifetime and the effect of exhausted sensors on the application
 * are outside its scope (objective O6 in the companion paper). The model here is a
 * linear charge budget: every transmission costs {@code txFixedJ + txPerByteJ * bytes}
 * joules, and the sensor draws {@code idlePowerW} watts between transmissions
 * (simulation time is in milliseconds). Non-linear effects (rate-capacity effect,
 * recovery, temperature) are not modelled.
 */
public final class BatteryModel {

    private final double capacityJ;
    private final double txFixedJ;
    private final double txPerByteJ;
    private final double idlePowerW;
    private double remainingJ;
    private double lastUpdateMs;
    private double exhaustedAtMs = Double.NaN;
    private double transmitJ;
    private double idleJ;
    private long transmissions;

    public BatteryModel(double capacityJ, double txFixedJ, double txPerByteJ, double idlePowerW) {
        if (capacityJ <= 0 || txFixedJ < 0 || txPerByteJ < 0 || idlePowerW < 0) {
            throw new IllegalArgumentException("invalid battery parameters");
        }
        this.capacityJ = capacityJ;
        this.txFixedJ = txFixedJ;
        this.txPerByteJ = txPerByteJ;
        this.idlePowerW = idlePowerW;
        this.remainingJ = capacityJ;
    }

    /** Capacity of a cell given in mAh at a nominal voltage. */
    public static double joules(double milliampHours, double volts) {
        return milliampHours / 1000.0 * 3600.0 * volts;
    }

    /** Accounts for the idle drain up to {@code nowMs}. */
    public void advanceTo(double nowMs) {
        if (nowMs <= lastUpdateMs || isExhausted()) {
            lastUpdateMs = Math.max(lastUpdateMs, nowMs);
            return;
        }
        double need = idlePowerW * (nowMs - lastUpdateMs) / 1000.0;
        if (need >= remainingJ) {
            double reached = idlePowerW == 0 ? nowMs : lastUpdateMs + remainingJ / idlePowerW * 1000.0;
            idleJ += remainingJ;
            remainingJ = 0;
            exhaustedAtMs = reached;
        } else {
            idleJ += need;
            remainingJ -= need;
        }
        lastUpdateMs = nowMs;
    }

    /**
     * Draws the energy of a transmission of {@code bytes} at {@code nowMs}.
     *
     * @return {@code false} if the battery cannot supply it (the sensor is then exhausted)
     */
    public boolean transmit(double nowMs, double bytes) {
        advanceTo(nowMs);
        if (isExhausted()) {
            return false;
        }
        double cost = txFixedJ + txPerByteJ * bytes;
        if (cost > remainingJ) {
            transmitJ += remainingJ;
            remainingJ = 0;
            exhaustedAtMs = nowMs;
            return false;
        }
        remainingJ -= cost;
        transmitJ += cost;
        transmissions++;
        if (remainingJ == 0) {
            exhaustedAtMs = nowMs;
        }
        return true;
    }

    public boolean isExhausted() {
        return remainingJ <= 0;
    }

    public double getRemainingJ() {
        return remainingJ;
    }

    public double getRemainingFraction() {
        return remainingJ / capacityJ;
    }

    public double getCapacityJ() {
        return capacityJ;
    }

    /** Simulation time at which the battery ran out, or NaN. */
    public double getExhaustedAtMs() {
        return exhaustedAtMs;
    }

    public double getTransmitEnergyJ() {
        return transmitJ;
    }

    public double getIdleEnergyJ() {
        return idleJ;
    }

    public long getTransmissions() {
        return transmissions;
    }

    /**
     * Lifetime predicted from the average drain observed up to {@code nowMs}:
     * exhaustion time if already exhausted, otherwise {@code nowMs * capacity / consumed}.
     */
    public double projectedLifetimeMs(double nowMs) {
        if (isExhausted()) {
            return exhaustedAtMs;
        }
        double used = capacityJ - remainingJ;
        return used <= 0 ? Double.POSITIVE_INFINITY : nowMs * capacityJ / used;
    }
}
