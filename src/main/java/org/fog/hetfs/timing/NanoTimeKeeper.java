package org.fog.hetfs.timing;

import org.fog.utils.TimeKeeper;

import java.util.*;

/**
 * Singleton extension of TimeKeeper that uses nanosecond-precision wall-clock timing
 * for each AppLoop. Maintains running statistics (mean, variance, min, max) using
 * Welford's online algorithm. Provides confidence interval computation at 95% level.
 */
public class NanoTimeKeeper extends TimeKeeper {
    private static NanoTimeKeeper instance;

    private Map<String, TimingStats> loopStats;

    /**
     * Gets the singleton instance of NanoTimeKeeper.
     *
     * @return the NanoTimeKeeper singleton
     */
    public static synchronized NanoTimeKeeper getInstance() {
        if (instance == null) {
            instance = new NanoTimeKeeper();
        }
        return instance;
    }

    /**
     * Private constructor for singleton pattern.
     */
    private NanoTimeKeeper() {
        super();
        this.loopStats = new HashMap<>();
    }

    /**
     * Records a single timing measurement for a loop in nanoseconds.
     *
     * @param loopId the application loop identifier
     * @param nanos the measured time in nanoseconds
     */
    public void recordNanoTime(String loopId, long nanos) {
        TimingStats stats = loopStats.computeIfAbsent(loopId, k -> new TimingStats());
        double ms = nanos / 1_000_000.0;
        stats.record(ms);
    }

    /**
     * Gets the mean latency for a loop in milliseconds.
     *
     * @param loopId the application loop identifier
     * @return the mean latency in ms, or 0 if not recorded
     */
    public double getMeanMs(String loopId) {
        TimingStats stats = loopStats.get(loopId);
        return stats != null ? stats.getMean() : 0.0;
    }

    /**
     * Gets the standard deviation for a loop in milliseconds.
     *
     * @param loopId the application loop identifier
     * @return the standard deviation in ms, or 0 if not recorded
     */
    public double getStdDevMs(String loopId) {
        TimingStats stats = loopStats.get(loopId);
        return stats != null ? stats.getStdDev() : 0.0;
    }

    /**
     * Gets the 95% confidence interval for a loop.
     *
     * @param loopId the application loop identifier
     * @return a double array [lower, upper] in ms, or [0, 0] if not recorded
     */
    public double[] getCI95(String loopId) {
        TimingStats stats = loopStats.get(loopId);
        if (stats == null) {
            return new double[]{0.0, 0.0};
        }

        double mean = stats.getMean();
        double stdDev = stats.getStdDev();
        long count = stats.getCount();

        if (count <= 1) {
            return new double[]{mean, mean};
        }

        double se = stdDev / Math.sqrt(count);
        double margin = 1.96 * se;

        return new double[]{mean - margin, mean + margin};
    }

    /**
     * Gets the minimum recorded latency for a loop.
     *
     * @param loopId the application loop identifier
     * @return the minimum latency in ms, or Double.MAX_VALUE if not recorded
     */
    public double getMinMs(String loopId) {
        TimingStats stats = loopStats.get(loopId);
        return stats != null ? stats.getMin() : Double.MAX_VALUE;
    }

    /**
     * Gets the maximum recorded latency for a loop.
     *
     * @param loopId the application loop identifier
     * @return the maximum latency in ms, or 0 if not recorded
     */
    public double getMaxMs(String loopId) {
        TimingStats stats = loopStats.get(loopId);
        return stats != null ? stats.getMax() : 0.0;
    }

    /**
     * Gets the sample count for a loop.
     *
     * @param loopId the application loop identifier
     * @return the number of measurements recorded
     */
    public long getCount(String loopId) {
        TimingStats stats = loopStats.get(loopId);
        return stats != null ? stats.getCount() : 0;
    }

    /**
     * Gets all recorded loop IDs.
     *
     * @return a set of loop identifiers
     */
    public Set<String> getLoopIds() {
        return new HashSet<>(loopStats.keySet());
    }

    /**
     * Gets the timing statistics for a specific loop.
     *
     * @param loopId the application loop identifier
     * @return the TimingStats, or null if not recorded
     */
    public TimingStats getStats(String loopId) {
        return loopStats.get(loopId);
    }

    /**
     * Clears all recorded timing data.
     */
    public void clearAll() {
        loopStats.clear();
    }

    @Override
    public String toString() {
        return String.format("NanoTimeKeeper(loops=%d)", loopStats.size());
    }

    /**
     * Inner class for computing online statistics using Welford's algorithm.
     */
    public static class TimingStats {
        private long count;
        private double mean;
        private double m2;
        private double min;
        private double max;

        /**
         * Creates a new TimingStats instance.
         */
        public TimingStats() {
            this.count = 0;
            this.mean = 0.0;
            this.m2 = 0.0;
            this.min = Double.MAX_VALUE;
            this.max = 0.0;
        }

        /**
         * Records a new measurement using Welford's online algorithm.
         *
         * @param value the measured value in milliseconds
         */
        public void record(double value) {
            count++;
            double delta = value - mean;
            mean += delta / count;
            double delta2 = value - mean;
            m2 += delta * delta2;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        /**
         * Gets the count of recorded measurements.
         *
         * @return the sample count
         */
        public long getCount() {
            return count;
        }

        /**
         * Gets the current mean value.
         *
         * @return the mean in milliseconds
         */
        public double getMean() {
            return mean;
        }

        /**
         * Gets the current variance.
         *
         * @return the variance
         */
        public double getVariance() {
            if (count < 2) {
                return 0.0;
            }
            return m2 / (count - 1);
        }

        /**
         * Gets the current standard deviation.
         *
         * @return the standard deviation in milliseconds
         */
        public double getStdDev() {
            return Math.sqrt(getVariance());
        }

        /**
         * Gets the minimum recorded value.
         *
         * @return the minimum in milliseconds
         */
        public double getMin() {
            return count > 0 ? min : Double.NaN;
        }

        /**
         * Gets the maximum recorded value.
         *
         * @return the maximum in milliseconds
         */
        public double getMax() {
            return count > 0 ? max : Double.NaN;
        }

        @Override
        public String toString() {
            return String.format("TimingStats(n=%d, mean=%.3fms, stdDev=%.3fms, min=%.3fms, max=%.3fms)",
                    count, mean, getStdDev(), min, max);
        }
    }
}
