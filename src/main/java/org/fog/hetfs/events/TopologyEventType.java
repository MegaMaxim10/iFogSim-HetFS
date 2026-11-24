package org.fog.hetfs.events;

/**
 * Enumeration of topology event types that can occur during simulation.
 */
public enum TopologyEventType {
    /**
     * A fog device fails and becomes unavailable.
     */
    DEVICE_FAIL,

    /**
     * A previously failed fog device recovers and becomes available.
     */
    DEVICE_RECOVER,

    /**
     * The latency of a link changes dynamically.
     */
    LINK_LATENCY_CHANGE,

    /**
     * The bandwidth of a link changes dynamically.
     */
    LINK_BANDWIDTH_CHANGE,

    /**
     * A link fails and becomes unavailable.
     */
    LINK_FAIL,

    /**
     * A previously failed link recovers and becomes available.
     */
    LINK_RECOVER
}
