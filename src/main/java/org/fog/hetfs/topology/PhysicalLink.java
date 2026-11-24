package org.fog.hetfs.topology;

import java.util.ArrayDeque;
import java.util.Deque;

import org.fog.entities.Tuple;

/**
 * A directed communication link between two devices of a {@link PhysicalGraph}.
 *
 * <p>The link reproduces, at the granularity of a single link, the transmission model
 * that iFogSim applies to the north and south interfaces of a {@code FogDevice}: a
 * tuple of {@code s} bytes occupies the link for {@code s / bandwidth} time units
 * (serialisation), then reaches the far end after a further {@code latency}
 * (propagation). Tuples submitted while the link is busy wait in a FIFO queue. The
 * bandwidth is therefore expressed in the unit iFogSim uses for
 * {@code FogDevice#getUplinkBandwidth()}, and the latency in milliseconds.
 *
 * <p>Unlike iFogSim, which has one north and one south queue per device, every link
 * has its own queue, so that two tuples leaving a device on two different links do
 * not delay each other.
 */
public class PhysicalLink {

    private final int sourceId;
    private final int destinationId;
    private double latencyMs;
    private double bandwidth;
    private boolean up = true;

    private boolean busy;
    private final Deque<Tuple> queue = new ArrayDeque<Tuple>();
    private long transmittedTuples;
    private long droppedTuples;

    public PhysicalLink(int sourceId, int destinationId, double latencyMs, double bandwidth) {
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latency must be non-negative: " + latencyMs);
        }
        if (bandwidth <= 0) {
            throw new IllegalArgumentException("bandwidth must be positive: " + bandwidth);
        }
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.latencyMs = latencyMs;
        this.bandwidth = bandwidth;
    }

    public int getSourceId() {
        return sourceId;
    }

    public int getDestinationId() {
        return destinationId;
    }

    public double getLatencyMs() {
        return latencyMs;
    }

    void setLatencyMs(double latencyMs) {
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latency must be non-negative: " + latencyMs);
        }
        this.latencyMs = latencyMs;
    }

    public double getBandwidth() {
        return bandwidth;
    }

    void setBandwidth(double bandwidth) {
        if (bandwidth <= 0) {
            throw new IllegalArgumentException("bandwidth must be positive: " + bandwidth);
        }
        this.bandwidth = bandwidth;
    }

    public boolean isUp() {
        return up;
    }

    /** Sets the administrative state of the link; taking it down discards its queue. */
    void setUp(boolean up) {
        this.up = up;
        if (!up) {
            droppedTuples += queue.size();
            queue.clear();
            busy = false;
        }
    }

    /** Serialisation time of a tuple of {@code bytes} bytes on this link. */
    public double serialisationDelay(double bytes) {
        return bytes / bandwidth;
    }

    public boolean isBusy() {
        return busy;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public Deque<Tuple> getQueue() {
        return queue;
    }

    public int getQueueLength() {
        return queue.size();
    }

    public long getTransmittedTuples() {
        return transmittedTuples;
    }

    public void countTransmission() {
        transmittedTuples++;
    }

    public long getDroppedTuples() {
        return droppedTuples;
    }

    void countDrop() {
        droppedTuples++;
    }

    @Override
    public String toString() {
        return "PhysicalLink[" + sourceId + "->" + destinationId + ", " + latencyMs + " ms, bw="
                + bandwidth + (up ? "" : ", DOWN") + "]";
    }
}
