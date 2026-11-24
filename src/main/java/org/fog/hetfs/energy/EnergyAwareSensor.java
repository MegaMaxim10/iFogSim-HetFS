package org.fog.hetfs.energy;

import org.fog.entities.Sensor;

/**
 * A sensor that tracks energy consumption via a BatteryModel.
 * Stops transmitting when the battery is exhausted. Supports idle energy drain.
 */
public class EnergyAwareSensor extends Sensor {
    private BatteryModel battery;
    private long totalTuplesTransmitted;
    private long totalTransmissionsAttempted;

    /**
     * Creates a new energy-aware sensor with the specified parameters.
     *
     * @param sensorName the sensor name
     * @param transmissionTime the transmission interval in milliseconds
     * @param appId the application ID
     * @param userId the user ID
     * @param gatewayDeviceId the gateway device ID
     * @param battery the battery model for this sensor
     */
    public EnergyAwareSensor(String sensorName, double transmissionTime, int appId,
                            int userId, int gatewayDeviceId, BatteryModel battery) {
        super(sensorName, transmissionTime, appId, userId, gatewayDeviceId);
        this.battery = battery;
        this.totalTuplesTransmitted = 0;
        this.totalTransmissionsAttempted = 0;
    }

    /**
     * Gets the battery model for this sensor.
     *
     * @return the BatteryModel
     */
    public BatteryModel getBattery() {
        return battery;
    }

    /**
     * Sets a new battery model for this sensor.
     *
     * @param battery the new BatteryModel
     */
    public void setBattery(BatteryModel battery) {
        this.battery = battery;
    }

    /**
     * Gets the total number of tuples successfully transmitted.
     *
     * @return the transmission count
     */
    public long getTotalTuplesTransmitted() {
        return totalTuplesTransmitted;
    }

    /**
     * Gets the total number of transmission attempts (including failures).
     *
     * @return the attempt count
     */
    public long getTotalTransmissionsAttempted() {
        return totalTransmissionsAttempted;
    }

    /**
     * Gets the transmission success rate as a fraction.
     *
     * @return the success rate between 0 and 1
     */
    public double getTransmissionSuccessRate() {
        return totalTransmissionsAttempted > 0 ?
                (double) totalTuplesTransmitted / totalTransmissionsAttempted : 0.0;
    }

    /**
     * Checks if this sensor can continue transmitting.
     *
     * @return true if battery is not exhausted
     */
    public boolean isOperational() {
        return !battery.isExhausted();
    }

    /**
     * Attempts to transmit and consumes energy if successful.
     * Called at each transmission interval.
     *
     * @return true if transmission succeeded, false if battery exhausted
     */
    public boolean transmit() {
        totalTransmissionsAttempted++;

        if (battery.consumeTransmit()) {
            totalTuplesTransmitted++;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Consumes idle energy for the specified duration.
     * Should be called periodically to model standby power consumption.
     *
     * @param durationMs the duration in milliseconds
     */
    public void updateEnergyConsumption(double durationMs) {
        battery.consumeIdle(durationMs);
    }

    /**
     * Gets the estimated remaining operating time for this sensor.
     *
     * @return the estimated time in seconds until battery exhaustion
     */
    public double getEstimatedRemainingLifetime() {
        return battery.getEstimatedRemainingLifetimeSec();
    }

    /**
     * Recharges the battery to full capacity.
     */
    public void recharge() {
        battery.recharge();
        totalTuplesTransmitted = 0;
        totalTransmissionsAttempted = 0;
    }

    @Override
    public String toString() {
        return String.format("EnergyAwareSensor(name=%s, battery=%.2f%%, transmitted=%d/%d, lifetime=%.2fs)",
                getName(), battery.getRemainingFraction() * 100,
                totalTuplesTransmitted, totalTransmissionsAttempted,
                getEstimatedRemainingLifetime());
    }
}
