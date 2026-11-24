package org.fog.hetfs.concurrent;

/**
 * Statistics bean that aggregates concurrency metrics across all streams,
 * including queue depths, service times, and contention ratios.
 */
public class ConcurrencyStats {
    private double avgQueueDepth;
    private double maxQueueDepth;
    private double contentionRatio;
    private double throughputTuplesSec;
    private long totalTuplesProcessed;
    private double simulationDurationMs;

    /**
     * Creates a new ConcurrencyStats instance with default values.
     */
    public ConcurrencyStats() {
        this.avgQueueDepth = 0.0;
        this.maxQueueDepth = 0.0;
        this.contentionRatio = 0.0;
        this.throughputTuplesSec = 0.0;
        this.totalTuplesProcessed = 0;
        this.simulationDurationMs = 0.0;
    }

    /**
     * Gets the average queue depth across all streams.
     *
     * @return the average queue depth
     */
    public double getAvgQueueDepth() {
        return avgQueueDepth;
    }

    /**
     * Sets the average queue depth.
     *
     * @param avgQueueDepth the average queue depth
     */
    public void setAvgQueueDepth(double avgQueueDepth) {
        this.avgQueueDepth = avgQueueDepth;
    }

    /**
     * Gets the maximum queue depth observed.
     *
     * @return the maximum queue depth
     */
    public double getMaxQueueDepth() {
        return maxQueueDepth;
    }

    /**
     * Sets the maximum queue depth.
     *
     * @param maxQueueDepth the maximum queue depth
     */
    public void setMaxQueueDepth(double maxQueueDepth) {
        this.maxQueueDepth = maxQueueDepth;
    }

    /**
     * Gets the contention ratio (fraction of time the network link was busy).
     *
     * @return the contention ratio between 0 and 1
     */
    public double getContentionRatio() {
        return contentionRatio;
    }

    /**
     * Sets the contention ratio.
     *
     * @param contentionRatio the contention ratio between 0 and 1
     */
    public void setContentionRatio(double contentionRatio) {
        this.contentionRatio = Math.max(0.0, Math.min(1.0, contentionRatio));
    }

    /**
     * Gets the throughput in tuples per second.
     *
     * @return the throughput in tuples/sec
     */
    public double getThroughputTuplesSec() {
        return throughputTuplesSec;
    }

    /**
     * Sets the throughput in tuples per second.
     *
     * @param throughputTuplesSec the throughput in tuples/sec
     */
    public void setThroughputTuplesSec(double throughputTuplesSec) {
        this.throughputTuplesSec = throughputTuplesSec;
    }

    /**
     * Gets the total number of tuples processed.
     *
     * @return the total tuple count
     */
    public long getTotalTuplesProcessed() {
        return totalTuplesProcessed;
    }

    /**
     * Sets the total number of tuples processed.
     *
     * @param totalTuplesProcessed the total tuple count
     */
    public void setTotalTuplesProcessed(long totalTuplesProcessed) {
        this.totalTuplesProcessed = totalTuplesProcessed;
    }

    /**
     * Gets the simulation duration in milliseconds.
     *
     * @return the duration in milliseconds
     */
    public double getSimulationDurationMs() {
        return simulationDurationMs;
    }

    /**
     * Sets the simulation duration in milliseconds.
     *
     * @param simulationDurationMs the duration in milliseconds
     */
    public void setSimulationDurationMs(double simulationDurationMs) {
        this.simulationDurationMs = simulationDurationMs;
    }

    @Override
    public String toString() {
        return String.format("ConcurrencyStats(avgQueue=%.2f, maxQueue=%.2f, contention=%.4f, " +
                        "throughput=%.2f tuples/sec, total=%d, duration=%.2fms)",
                avgQueueDepth, maxQueueDepth, contentionRatio, throughputTuplesSec,
                totalTuplesProcessed, simulationDurationMs);
    }
}
