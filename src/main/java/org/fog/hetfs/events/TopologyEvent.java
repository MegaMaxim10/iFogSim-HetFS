package org.fog.hetfs.events;

/**
 * A topology change scheduled at a given simulation time (module D5). Device events
 * use {@code deviceId}; link events use {@code sourceId -> targetId} and, for the two
 * change events, {@code value} (latency in ms, or bandwidth).
 */
public final class TopologyEvent {

    private final TopologyEventType type;
    private final double time;
    private final int sourceId;
    private final int targetId;
    private final double value;
    private final boolean bothDirections;

    private TopologyEvent(TopologyEventType type, double time, int sourceId, int targetId,
                          double value, boolean bothDirections) {
        if (time < 0) {
            throw new IllegalArgumentException("event time must be non-negative: " + time);
        }
        this.type = type;
        this.time = time;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.value = value;
        this.bothDirections = bothDirections;
    }

    public static TopologyEvent device(TopologyEventType type, double time, int deviceId) {
        if (type != TopologyEventType.DEVICE_FAIL && type != TopologyEventType.DEVICE_RECOVER) {
            throw new IllegalArgumentException(type + " is not a device event");
        }
        return new TopologyEvent(type, time, deviceId, -1, Double.NaN, false);
    }

    public static TopologyEvent link(TopologyEventType type, double time, int src, int dst, double value,
                                     boolean bothDirections) {
        if (type == TopologyEventType.DEVICE_FAIL || type == TopologyEventType.DEVICE_RECOVER) {
            throw new IllegalArgumentException(type + " is not a link event");
        }
        return new TopologyEvent(type, time, src, dst, value, bothDirections);
    }

    public TopologyEventType getType() {
        return type;
    }

    public double getTime() {
        return time;
    }

    public int getDeviceId() {
        return sourceId;
    }

    public int getSourceId() {
        return sourceId;
    }

    public int getTargetId() {
        return targetId;
    }

    public double getValue() {
        return value;
    }

    public boolean isBothDirections() {
        return bothDirections;
    }

    @Override
    public String toString() {
        return time + " " + type + " " + sourceId + (targetId >= 0 ? "->" + targetId : "")
                + (Double.isNaN(value) ? "" : " " + value);
    }
}
