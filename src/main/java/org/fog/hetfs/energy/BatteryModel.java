package org.fog.hetfs.energy;

/**
 * Models the battery of a sensor device with energy consumption tracking.
 * Supports transmission cost, idle drain, and remaining charge queries.
 */
public class BatteryModel {
    private double capacityMJ;
    private double currentChargeMJ;
    private double transmitCostMJPerTuple;
    private double idlePowerW;

    /**
     * Creates a new battery model with the specified parameters.
     *
     * @param capacityMJ the total battery capacity in megajoules
     * @param transmitCostMJPerTuple the energy cost per transmitted tuple in megajoules
     * @param idlePowerW the idle power consumption in watts
     */
    public BatteryModel(double capacityMJ, double transmitCostMJPerTuple, double idlePowerW) {
        this.capacityMJ = capacityMJ;
        this.currentChargeMJ = capacityMJ;
        this.transmitCostMJPerTuple = transmitCostMJPerTuple;
        this.idlePowerW = idlePowerW;
    }

    /**
     * Gets the total battery capacity in megajoules.
     *
     * @return the capacity in megajoules
     */
    public double getCapacityMJ() {
        return capacityMJ;
    }

    /**
     * Gets the current battery charge in megajoules.
     *
     * @return the current charge in megajoules
     */
    public double getCurrentChargeMJ() {
        return currentChargeMJ;
    }

    /**
     * Sets the current battery charge.
     *
     * @param chargeMJ the new charge in megajoules
     */
    public void setCurrentChargeMJ(double chargeMJ) {
        this.currentChargeMJ = Math.max(0, Math.min(capacityMJ, chargeMJ));
    }

    /**
     * Gets the energy cost per transmitted tuple in megajoules.
     *
     * @return the transmission cost in megajoules
     */
    public double getTransmitCostMJPerTuple() {
        return transmitCostMJPerTuple;
    }

    /**
     * Sets the energy cost per transmitted tuple.
     *
     * @param costMJ the new transmission cost in megajoules
     */
    public void setTransmitCostMJPerTuple(double costMJ) {
        this.transmitCostMJPerTuple = Math.max(0, costMJ);
    }

    /**
     * Gets the idle power consumption in watts.
     *
     * @return the idle power in watts
     */
    public double getIdlePowerW() {
        return idlePowerW;
    }

    /**
     * Sets the idle power consumption.
     *
     * @param powerW the new idle power in watts
     */
    public void setIdlePowerW(double powerW) {
        this.idlePowerW = Math.max(0, powerW);
    }

    /**
     * Consumes energy for a single transmission.
     * Returns true if the transmission succeeded (battery had sufficient charge).
     *
     * @return true if transmission was successful, false if battery exhausted
     */
    public boolean consumeTransmit() {
        if (currentChargeMJ >= transmitCostMJPerTuple) {
            currentChargeMJ -= transmitCostMJPerTuple;
            return true;
        }
        return false;
    }

    /**
     * Consumes energy for idle operation over a given duration.
     *
     * @param durationMs the duration in milliseconds
     */
    public void consumeIdle(double durationMs) {
        double durationSec = durationMs / 1000.0;
        double energyUsedJ = idlePowerW * durationSec;
        double energyUsedMJ = energyUsedJ / 1_000_000.0;
        currentChargeMJ = Math.max(0, currentChargeMJ - energyUsedMJ);
    }

    /**
     * Gets the remaining charge as a fraction of capacity (0.0 to 1.0).
     *
     * @return the remaining charge fraction
     */
    public double getRemainingFraction() {
        return capacityMJ > 0 ? currentChargeMJ / capacityMJ : 0.0;
    }

    /**
     * Checks if the battery is exhausted.
     *
     * @return true if the battery charge is zero or below
     */
    public boolean isExhausted() {
        return currentChargeMJ <= 0;
    }

    /**
     * Checks if the battery has sufficient charge for a transmission.
     *
     * @return true if enough charge is available for one transmission
     */
    public boolean canTransmit() {
        return currentChargeMJ >= transmitCostMJPerTuple;
    }

    /**
     * Resets the battery to full charge.
     */
    public void recharge() {
        currentChargeMJ = capacityMJ;
    }

    /**
     * Gets the estimated remaining transmission count at current charge level.
     *
     * @return the number of transmissions that can be made
     */
    public long getEstimatedRemainingTransmissions() {
        return transmitCostMJPerTuple > 0 ? (long) (currentChargeMJ / transmitCostMJPerTuple) : 0;
    }

    /**
     * Gets the estimated remaining operating time in seconds at idle.
     *
     * @return the remaining operating time in seconds
     */
    public double getEstimatedRemainingLifetimeSec() {
        if (idlePowerW <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        double energyJoules = currentChargeMJ * 1_000_000.0;
        return energyJoules / idlePowerW;
    }

    @Override
    public String toString() {
        return String.format("BatteryModel(capacity=%.2fMJ, current=%.2fMJ, fraction=%.2f%%, " +
                        "transmitCost=%.4fMJ, idlePower=%.2fW)",
                capacityMJ, currentChargeMJ, getRemainingFraction() * 100,
                transmitCostMJPerTuple, idlePowerW);
    }
}
