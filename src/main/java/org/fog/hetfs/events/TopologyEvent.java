package org.fog.hetfs.events;

/**
 * Represents a runtime topology event (device failure, link change, etc.).
 * Contains the event type, affected device(s)/link(s), new values if applicable,
 * and the simulation time at which the event occurs.
 */
public class TopologyEvent {
    private TopologyEventType type;
    private int targetDeviceId;
    private int srcId;
    private int dstId;
    private double newValue;
    private double scheduledAt;

    /**
     * Creates a device-level topology event.
     *
     * @param type the event type
     * @param targetDeviceId the affected device ID
     * @param scheduledAt the simulation time at which the event occurs
     */
    public TopologyEvent(TopologyEventType type, int targetDeviceId, double scheduledAt) {
        this.type = type;
        this.targetDeviceId = targetDeviceId;
        this.srcId = -1;
        this.dstId = -1;
        this.newValue = 0.0;
        this.scheduledAt = scheduledAt;
    }

    /**
     * Creates a link-level topology event.
     *
     * @param type the event type
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @param newValue the new latency (ms) or bandwidth (Mbps) for CHANGE events
     * @param scheduledAt the simulation time at which the event occurs
     */
    public TopologyEvent(TopologyEventType type, int srcId, int dstId, double newValue, double scheduledAt) {
        this.type = type;
        this.targetDeviceId = -1;
        this.srcId = srcId;
        this.dstId = dstId;
        this.newValue = newValue;
        this.scheduledAt = scheduledAt;
    }

    /**
     * Gets the event type.
     *
     * @return the TopologyEventType
     */
    public TopologyEventType getType() {
        return type;
    }

    /**
     * Gets the target device ID for device-level events.
     *
     * @return the device ID, or -1 for link-level events
     */
    public int getTargetDeviceId() {
        return targetDeviceId;
    }

    /**
     * Gets the source device ID for link-level events.
     *
     * @return the source device ID, or -1 for device-level events
     */
    public int getSrcId() {
        return srcId;
    }

    /**
     * Gets the destination device ID for link-level events.
     *
     * @return the destination device ID, or -1 for device-level events
     */
    public int getDstId() {
        return dstId;
    }

    /**
     * Gets the new value for CHANGE events (latency or bandwidth).
     *
     * @return the new value, or 0 for other event types
     */
    public double getNewValue() {
        return newValue;
    }

    /**
     * Gets the simulation time at which this event is scheduled.
     *
     * @return the scheduled simulation time
     */
    public double getScheduledAt() {
        return scheduledAt;
    }

    /**
     * Checks if this is a device-level event.
     *
     * @return true if the event affects a device
     */
    public boolean isDeviceEvent() {
        return targetDeviceId >= 0;
    }

    /**
     * Checks if this is a link-level event.
     *
     * @return true if the event affects a link
     */
    public boolean isLinkEvent() {
        return srcId >= 0 && dstId >= 0;
    }

    @Override
    public String toString() {
        if (isDeviceEvent()) {
            return String.format("TopologyEvent(%s, device=%d, at=%.2fms)", type, targetDeviceId, scheduledAt);
        } else {
            return String.format("TopologyEvent(%s, link=%d->%d, value=%.2f, at=%.2fms)",
                    type, srcId, dstId, newValue, scheduledAt);
        }
    }
}
