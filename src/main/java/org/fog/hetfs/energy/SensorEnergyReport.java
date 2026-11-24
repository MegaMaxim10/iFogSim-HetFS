package org.fog.hetfs.energy;

import java.util.*;

/**
 * Collects and reports energy statistics from all EnergyAwareSensor instances.
 * Prints remaining charge, transmission statistics, and estimated lifetime for each sensor.
 */
public class SensorEnergyReport {
    private List<EnergyAwareSensor> sensors;

    /**
     * Creates a new sensor energy report.
     */
    public SensorEnergyReport() {
        this.sensors = new ArrayList<>();
    }

    /**
     * Adds a sensor to the report.
     *
     * @param sensor the EnergyAwareSensor to track
     */
    public void addSensor(EnergyAwareSensor sensor) {
        if (!sensors.contains(sensor)) {
            sensors.add(sensor);
        }
    }

    /**
     * Adds multiple sensors to the report.
     *
     * @param sensorList the list of sensors to track
     */
    public void addSensors(List<EnergyAwareSensor> sensorList) {
        for (EnergyAwareSensor sensor : sensorList) {
            addSensor(sensor);
        }
    }

    /**
     * Gets all tracked sensors.
     *
     * @return a list of EnergyAwareSensor instances
     */
    public List<EnergyAwareSensor> getSensors() {
        return new ArrayList<>(sensors);
    }

    /**
     * Generates a formatted energy report for all sensors.
     *
     * @return a formatted string suitable for console output
     */
    public String generateReport() {
        if (sensors.isEmpty()) {
            return "No sensors to report.\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("=== SENSOR ENERGY REPORT ===\n");
        sb.append(String.format("%-25s %12s %12s %12s %12s %12s%n",
                "Sensor", "Charge (%)", "Transmitted", "Attempts", "Success Rate", "Lifetime (s)"));
        sb.append(String.format("%-25s %12s %12s %12s %12s %12s%n",
                "-".repeat(25), "-".repeat(12), "-".repeat(12),
                "-".repeat(12), "-".repeat(12), "-".repeat(12)));

        double totalChargeFraction = 0.0;
        long totalTransmitted = 0;
        long totalAttempts = 0;
        double minLifetime = Double.MAX_VALUE;
        double maxLifetime = 0.0;
        int operationalCount = 0;

        for (EnergyAwareSensor sensor : sensors) {
            BatteryModel battery = sensor.getBattery();
            double chargeFraction = battery.getRemainingFraction() * 100.0;
            long transmitted = sensor.getTotalTuplesTransmitted();
            long attempts = sensor.getTotalTransmissionsAttempted();
            double successRate = sensor.getTransmissionSuccessRate() * 100.0;
            double lifetime = sensor.getEstimatedRemainingLifetime();

            totalChargeFraction += chargeFraction;
            totalTransmitted += transmitted;
            totalAttempts += attempts;
            minLifetime = Math.min(minLifetime, lifetime);
            maxLifetime = Math.max(maxLifetime, lifetime);

            if (sensor.isOperational()) {
                operationalCount++;
            }

            sb.append(String.format("%-25s %12.2f %12d %12d %12.2f %12.2f%n",
                    truncate(sensor.getName(), 25),
                    chargeFraction,
                    transmitted,
                    attempts,
                    successRate,
                    lifetime));
        }

        sb.append("\n");
        sb.append("=== SUMMARY ===\n");
        sb.append(String.format("Total Sensors:           %d%n", sensors.size()));
        sb.append(String.format("Operational Sensors:     %d%n", operationalCount));
        sb.append(String.format("Average Charge:          %.2f%%%n", totalChargeFraction / sensors.size()));
        sb.append(String.format("Total Tuples Transmitted: %d%n", totalTransmitted));
        sb.append(String.format("Total Transmission Attempts: %d%n", totalAttempts));
        if (totalAttempts > 0) {
            double overallSuccessRate = (double) totalTransmitted / totalAttempts * 100.0;
            sb.append(String.format("Overall Success Rate:    %.2f%%%n", overallSuccessRate));
        }
        sb.append(String.format("Min Sensor Lifetime:     %.2f seconds%n", minLifetime));
        sb.append(String.format("Max Sensor Lifetime:     %.2f seconds%n", maxLifetime));
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Generates a compact energy report for logging.
     *
     * @return a single-line formatted report
     */
    public String generateCompactReport() {
        if (sensors.isEmpty()) {
            return "No sensors to report.";
        }

        long totalTransmitted = 0;
        long totalAttempts = 0;
        int operationalCount = 0;

        for (EnergyAwareSensor sensor : sensors) {
            totalTransmitted += sensor.getTotalTuplesTransmitted();
            totalAttempts += sensor.getTotalTransmissionsAttempted();
            if (sensor.isOperational()) {
                operationalCount++;
            }
        }

        return String.format("ENERGY: %d/%d sensors operational, %d tuples transmitted, " +
                        "%.2f%% success rate",
                operationalCount, sensors.size(), totalTransmitted,
                totalAttempts > 0 ? (double) totalTransmitted / totalAttempts * 100 : 0);
    }

    /**
     * Generates a detailed per-sensor report.
     *
     * @return a multi-line formatted report
     */
    public String generateDetailedReport() {
        if (sensors.isEmpty()) {
            return "No sensors to report.\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== DETAILED SENSOR ENERGY REPORT ===\n\n");

        for (EnergyAwareSensor sensor : sensors) {
            BatteryModel battery = sensor.getBattery();
            sb.append(String.format("Sensor: %s%n", sensor.getName()));
            sb.append(String.format("  Battery Capacity:      %.4f MJ%n", battery.getCapacityMJ()));
            sb.append(String.format("  Current Charge:        %.4f MJ (%.2f%%)%n",
                    battery.getCurrentChargeMJ(), battery.getRemainingFraction() * 100));
            sb.append(String.format("  Transmit Cost:         %.6f MJ per tuple%n", battery.getTransmitCostMJPerTuple()));
            sb.append(String.format("  Idle Power:            %.2f W%n", battery.getIdlePowerW()));
            sb.append(String.format("  Tuples Transmitted:    %d%n", sensor.getTotalTuplesTransmitted()));
            sb.append(String.format("  Transmission Attempts: %d%n", sensor.getTotalTransmissionsAttempted()));
            sb.append(String.format("  Success Rate:          %.2f%%%n", sensor.getTransmissionSuccessRate() * 100));
            sb.append(String.format("  Remaining Lifetime:    %.2f seconds%n", sensor.getEstimatedRemainingLifetime()));
            sb.append(String.format("  Remaining Transmissions: %d%n", battery.getEstimatedRemainingTransmissions()));
            sb.append(String.format("  Status:                %s%n", sensor.isOperational() ? "OPERATIONAL" : "EXHAUSTED"));
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Truncates a string to a maximum length, adding ellipsis if needed.
     *
     * @param str the string to truncate
     * @param maxLength the maximum length
     * @return the truncated string
     */
    private static String truncate(String str, int maxLength) {
        if (str.length() > maxLength) {
            return str.substring(0, maxLength - 3) + "...";
        }
        return str;
    }
}
