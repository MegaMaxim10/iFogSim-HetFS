package org.fog.hetfs.events;

import java.util.*;

/**
 * Convenience builder for scheduling common topology fault injection scenarios.
 * Supports device failures, link degradations, and periodic failure-recovery cycles.
 */
public class FaultInjector {
    private List<TopologyEvent> scheduledEvents;

    /**
     * Creates a new FaultInjector.
     */
    public FaultInjector() {
        this.scheduledEvents = new ArrayList<>();
    }

    /**
     * Schedules a device failure at the specified time.
     *
     * @param deviceId the device to fail
     * @param timeMs the simulation time at which to fail the device
     * @return this FaultInjector for chaining
     */
    public FaultInjector failDeviceAt(int deviceId, double timeMs) {
        scheduledEvents.add(new TopologyEvent(TopologyEventType.DEVICE_FAIL, deviceId, timeMs));
        return this;
    }

    /**
     * Schedules a device recovery at the specified time.
     *
     * @param deviceId the device to recover
     * @param timeMs the simulation time at which to recover the device
     * @return this FaultInjector for chaining
     */
    public FaultInjector recoverDeviceAt(int deviceId, double timeMs) {
        scheduledEvents.add(new TopologyEvent(TopologyEventType.DEVICE_RECOVER, deviceId, timeMs));
        return this;
    }

    /**
     * Schedules a link latency degradation at the specified time.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @param timeMs the simulation time at which to degrade the link
     * @param newLatencyMs the new latency in milliseconds
     * @return this FaultInjector for chaining
     */
    public FaultInjector degradeLinkAt(int srcId, int dstId, double timeMs, double newLatencyMs) {
        scheduledEvents.add(new TopologyEvent(TopologyEventType.LINK_LATENCY_CHANGE, srcId, dstId, newLatencyMs, timeMs));
        return this;
    }

    /**
     * Schedules a link bandwidth reduction at the specified time.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @param timeMs the simulation time at which to reduce bandwidth
     * @param newBandwidthMbps the new bandwidth in Mbps
     * @return this FaultInjector for chaining
     */
    public FaultInjector reduceBandwidthAt(int srcId, int dstId, double timeMs, double newBandwidthMbps) {
        scheduledEvents.add(new TopologyEvent(TopologyEventType.LINK_BANDWIDTH_CHANGE, srcId, dstId, newBandwidthMbps, timeMs));
        return this;
    }

    /**
     * Schedules a link failure at the specified time.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @param timeMs the simulation time at which to fail the link
     * @return this FaultInjector for chaining
     */
    public FaultInjector failLinkAt(int srcId, int dstId, double timeMs) {
        scheduledEvents.add(new TopologyEvent(TopologyEventType.LINK_FAIL, srcId, dstId, 0.0, timeMs));
        return this;
    }

    /**
     * Schedules a link recovery at the specified time.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @param timeMs the simulation time at which to recover the link
     * @return this FaultInjector for chaining
     */
    public FaultInjector recoverLinkAt(int srcId, int dstId, double timeMs) {
        scheduledEvents.add(new TopologyEvent(TopologyEventType.LINK_RECOVER, srcId, dstId, 0.0, timeMs));
        return this;
    }

    /**
     * Schedules a periodic failure-recovery pulse (fail then recover).
     *
     * @param deviceId the device to pulse
     * @param failMs the simulation time at which to fail the device
     * @param recoverMs the simulation time at which to recover the device
     * @return this FaultInjector for chaining
     */
    public FaultInjector schedulePulse(int deviceId, double failMs, double recoverMs) {
        failDeviceAt(deviceId, failMs);
        recoverDeviceAt(deviceId, recoverMs);
        return this;
    }

    /**
     * Schedules a periodic link failure-recovery pulse.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @param failMs the simulation time at which to fail the link
     * @param recoverMs the simulation time at which to recover the link
     * @return this FaultInjector for chaining
     */
    public FaultInjector scheduleLinkPulse(int srcId, int dstId, double failMs, double recoverMs) {
        failLinkAt(srcId, dstId, failMs);
        recoverLinkAt(srcId, dstId, recoverMs);
        return this;
    }

    /**
     * Gets all scheduled events in chronological order.
     *
     * @return a list of TopologyEvent instances sorted by scheduled time
     */
    public List<TopologyEvent> getScheduledEvents() {
        List<TopologyEvent> sorted = new ArrayList<>(scheduledEvents);
        sorted.sort(Comparator.comparingDouble(TopologyEvent::getScheduledAt));
        return sorted;
    }

    /**
     * Clears all scheduled events.
     *
     * @return this FaultInjector for chaining
     */
    public FaultInjector clear() {
        scheduledEvents.clear();
        return this;
    }

    /**
     * Gets the number of scheduled events.
     *
     * @return the event count
     */
    public int getEventCount() {
        return scheduledEvents.size();
    }

    @Override
    public String toString() {
        return String.format("FaultInjector(events=%d)", scheduledEvents.size());
    }
}
