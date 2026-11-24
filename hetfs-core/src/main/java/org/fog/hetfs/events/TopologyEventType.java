package org.fog.hetfs.events;

/** Kinds of run-time topology change (module D5). */
public enum TopologyEventType {
    DEVICE_FAIL,
    DEVICE_RECOVER,
    LINK_FAIL,
    LINK_RECOVER,
    LINK_LATENCY_CHANGE,
    LINK_BANDWIDTH_CHANGE
}
