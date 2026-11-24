package org.fog.hetfs.topology;

/**
 * Represents a directed link between two fog devices with configurable latency,
 * bandwidth, loss rate, and up/down status. Models a physical network connection
 * in the fog computing topology.
 */
public class PhysicalLink {
    private final int srcId;
    private final int dstId;
    private double latencyMs;
    private double bandwidthMbps;
    private boolean isUp;
    private double lossRate;

    /**
     * Creates a new physical link between source and destination fog devices.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @param latencyMs the link latency in milliseconds
     * @param bandwidthMbps the link bandwidth in Mbps
     */
    public PhysicalLink(int srcId, int dstId, double latencyMs, double bandwidthMbps) {
        this.srcId = srcId;
        this.dstId = dstId;
        this.latencyMs = latencyMs;
        this.bandwidthMbps = bandwidthMbps;
        this.isUp = true;
        this.lossRate = 0.0;
    }

    /**
     * Gets the source device ID of this link.
     *
     * @return the source device ID
     */
    public int getSrcId() {
        return srcId;
    }

    /**
     * Gets the destination device ID of this link.
     *
     * @return the destination device ID
     */
    public int getDstId() {
        return dstId;
    }

    /**
     * Gets the latency of this link in milliseconds.
     *
     * @return the latency in milliseconds
     */
    public double getLatencyMs() {
        return latencyMs;
    }

    /**
     * Sets the latency of this link in milliseconds.
     *
     * @param latencyMs the new latency in milliseconds
     */
    public void setLatencyMs(double latencyMs) {
        this.latencyMs = latencyMs;
    }

    /**
     * Gets the bandwidth of this link in Mbps.
     *
     * @return the bandwidth in Mbps
     */
    public double getBandwidthMbps() {
        return bandwidthMbps;
    }

    /**
     * Sets the bandwidth of this link in Mbps.
     *
     * @param bandwidthMbps the new bandwidth in Mbps
     */
    public void setBandwidthMbps(double bandwidthMbps) {
        this.bandwidthMbps = bandwidthMbps;
    }

    /**
     * Checks if this link is currently operational (up).
     *
     * @return true if the link is up, false otherwise
     */
    public boolean isUp() {
        return isUp;
    }

    /**
     * Sets the operational status of this link.
     *
     * @param up true to bring the link up, false to bring it down
     */
    public void setUp(boolean up) {
        this.isUp = up;
    }

    /**
     * Gets the packet loss rate of this link (0.0 to 1.0).
     *
     * @return the loss rate between 0 and 1
     */
    public double getLossRate() {
        return lossRate;
    }

    /**
     * Sets the packet loss rate of this link.
     *
     * @param lossRate the new loss rate between 0 and 1
     */
    public void setLossRate(double lossRate) {
        this.lossRate = Math.max(0.0, Math.min(1.0, lossRate));
    }

    @Override
    public String toString() {
        return String.format("PhysicalLink(%d -> %d, latency=%.2fms, bw=%.2fMbps, up=%s, loss=%.4f)",
                srcId, dstId, latencyMs, bandwidthMbps, isUp, lossRate);
    }
}
