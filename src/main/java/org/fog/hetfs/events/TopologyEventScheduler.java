package org.fog.hetfs.events;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.hetfs.topology.GraphFogDevice;
import org.fog.hetfs.topology.PhysicalGraph;
import org.fog.hetfs.topology.PhysicalLink;

import java.util.*;

/**
 * A CloudSim SimEntity that processes runtime topology events at their scheduled times.
 * Mutates the PhysicalGraph and notifies affected GraphFogDevice instances to rebuild
 * routing tables. Logs all events with before/after values.
 */
public class TopologyEventScheduler extends SimEntity {
    private PhysicalGraph physicalGraph;
    private List<GraphFogDevice> graphDevices;
    private Queue<TopologyEvent> eventQueue;
    private List<String> eventLog;
    private Set<Integer> failedDevices;

    private static final int TOPOLOGY_EVENT_TAG = 2001;

    /**
     * Creates a new topology event scheduler.
     *
     * @param name the scheduler entity name
     * @param graph the PhysicalGraph to mutate
     * @param devices the list of GraphFogDevice instances that may be affected
     */
    public TopologyEventScheduler(String name, PhysicalGraph graph, List<GraphFogDevice> devices) {
        super(name);
        this.physicalGraph = graph;
        this.graphDevices = new ArrayList<>(devices);
        this.eventQueue = new PriorityQueue<>(
                Comparator.comparingDouble(TopologyEvent::getScheduledAt));
        this.eventLog = new ArrayList<>();
        this.failedDevices = new HashSet<>();
    }

    /**
     * Schedules a topology event for processing.
     *
     * @param event the TopologyEvent to schedule
     */
    public void scheduleEvent(TopologyEvent event) {
        eventQueue.offer(event);
        schedule(getId(), event.getScheduledAt(), TOPOLOGY_EVENT_TAG, event);
    }

    /**
     * Schedules multiple topology events.
     *
     * @param events the collection of TopologyEvent instances to schedule
     */
    public void scheduleEvents(List<TopologyEvent> events) {
        for (TopologyEvent event : events) {
            scheduleEvent(event);
        }
    }

    /**
     * Gets the event log containing descriptions of processed events.
     *
     * @return a list of event log strings
     */
    public List<String> getEventLog() {
        return new ArrayList<>(eventLog);
    }

    /**
     * Gets the set of device IDs that are currently in a failed state.
     *
     * @return a set of failed device IDs
     */
    public Set<Integer> getFailedDevices() {
        return new HashSet<>(failedDevices);
    }

    /**
     * Checks if a device is currently failed.
     *
     * @param deviceId the device ID to check
     * @return true if the device is failed
     */
    public boolean isDeviceFailed(int deviceId) {
        return failedDevices.contains(deviceId);
    }

    @Override
    public void startEntity() {
    }

    @Override
    public void shutdownEntity() {
        eventQueue.clear();
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() == TOPOLOGY_EVENT_TAG && ev.getData() instanceof TopologyEvent) {
            TopologyEvent event = (TopologyEvent) ev.getData();
            processTopologyEvent(event);
        }
    }

    /**
     * Processes a single topology event, mutating the graph and rebuilding routing tables.
     *
     * @param event the TopologyEvent to process
     */
    private void processTopologyEvent(TopologyEvent event) {
        String logEntry = null;

        switch (event.getType()) {
            case DEVICE_FAIL:
                logEntry = processDeviceFail(event);
                break;
            case DEVICE_RECOVER:
                logEntry = processDeviceRecover(event);
                break;
            case LINK_LATENCY_CHANGE:
                logEntry = processLinkLatencyChange(event);
                break;
            case LINK_BANDWIDTH_CHANGE:
                logEntry = processLinkBandwidthChange(event);
                break;
            case LINK_FAIL:
                logEntry = processLinkFail(event);
                break;
            case LINK_RECOVER:
                logEntry = processLinkRecover(event);
                break;
        }

        if (logEntry != null) {
            eventLog.add(logEntry);
            System.out.println("[" + CloudSim.clock() + "ms] " + logEntry);
        }
    }

    /**
     * Processes a DEVICE_FAIL event.
     *
     * @param event the event to process
     * @return a log message describing the action taken
     */
    private String processDeviceFail(TopologyEvent event) {
        int deviceId = event.getTargetDeviceId();
        failedDevices.add(deviceId);

        List<Integer> affectedDevices = new ArrayList<>();
        for (GraphFogDevice device : graphDevices) {
            if (device.getId() == deviceId) {
                device.setPhysicalGraph(null);
            }
            affectedDevices.add((int) device.getId());
        }

        rebuildAffectedRoutingTables(affectedDevices);

        return String.format("DEVICE_FAIL: Device %d failed at %.2fms", deviceId, CloudSim.clock());
    }

    /**
     * Processes a DEVICE_RECOVER event.
     *
     * @param event the event to process
     * @return a log message describing the action taken
     */
    private String processDeviceRecover(TopologyEvent event) {
        int deviceId = event.getTargetDeviceId();
        failedDevices.remove(deviceId);

        List<Integer> affectedDevices = new ArrayList<>();
        for (GraphFogDevice device : graphDevices) {
            affectedDevices.add((int) device.getId());
        }

        rebuildAffectedRoutingTables(affectedDevices);

        return String.format("DEVICE_RECOVER: Device %d recovered at %.2fms", deviceId, CloudSim.clock());
    }

    /**
     * Processes a LINK_LATENCY_CHANGE event.
     *
     * @param event the event to process
     * @return a log message describing the action taken
     */
    private String processLinkLatencyChange(TopologyEvent event) {
        int srcId = event.getSrcId();
        int dstId = event.getDstId();
        double newLatency = event.getNewValue();

        PhysicalLink link = physicalGraph.getLink(srcId, dstId);
        double oldLatency = link != null ? link.getLatencyMs() : 0.0;

        boolean updated = physicalGraph.setLinkLatency(srcId, dstId, newLatency);

        if (updated) {
            List<Integer> affectedDevices = Arrays.asList(srcId, dstId);
            rebuildAffectedRoutingTables(affectedDevices);
            return String.format("LINK_LATENCY_CHANGE: %d->%d: %.2f -> %.2fms", srcId, dstId, oldLatency, newLatency);
        }

        return null;
    }

    /**
     * Processes a LINK_BANDWIDTH_CHANGE event.
     *
     * @param event the event to process
     * @return a log message describing the action taken
     */
    private String processLinkBandwidthChange(TopologyEvent event) {
        int srcId = event.getSrcId();
        int dstId = event.getDstId();
        double newBandwidth = event.getNewValue();

        PhysicalLink link = physicalGraph.getLink(srcId, dstId);
        double oldBandwidth = link != null ? link.getBandwidthMbps() : 0.0;

        boolean updated = physicalGraph.setLinkBandwidth(srcId, dstId, newBandwidth);

        if (updated) {
            return String.format("LINK_BANDWIDTH_CHANGE: %d->%d: %.2f -> %.2fMbps", srcId, dstId, oldBandwidth, newBandwidth);
        }

        return null;
    }

    /**
     * Processes a LINK_FAIL event.
     *
     * @param event the event to process
     * @return a log message describing the action taken
     */
    private String processLinkFail(TopologyEvent event) {
        int srcId = event.getSrcId();
        int dstId = event.getDstId();

        PhysicalLink link = physicalGraph.getLink(srcId, dstId);
        if (link != null) {
            link.setUp(false);
            List<Integer> affectedDevices = Arrays.asList(srcId, dstId);
            rebuildAffectedRoutingTables(affectedDevices);
            return String.format("LINK_FAIL: %d->%d link down", srcId, dstId);
        }

        return null;
    }

    /**
     * Processes a LINK_RECOVER event.
     *
     * @param event the event to process
     * @return a log message describing the action taken
     */
    private String processLinkRecover(TopologyEvent event) {
        int srcId = event.getSrcId();
        int dstId = event.getDstId();

        PhysicalLink link = physicalGraph.getLink(srcId, dstId);
        if (link != null) {
            link.setUp(true);
            List<Integer> affectedDevices = Arrays.asList(srcId, dstId);
            rebuildAffectedRoutingTables(affectedDevices);
            return String.format("LINK_RECOVER: %d->%d link up", srcId, dstId);
        }

        return null;
    }

    /**
     * Rebuilds routing tables for affected devices.
     *
     * @param affectedDevices the list of affected device IDs
     */
    private void rebuildAffectedRoutingTables(List<Integer> affectedDevices) {
        for (GraphFogDevice device : graphDevices) {
            if (affectedDevices.contains((int) device.getId())) {
                device.rebuildRoutingTable();
            }
        }
    }

    @Override
    public String toString() {
        return String.format("TopologyEventScheduler(events=%d, failed=%d, logged=%d)",
                eventQueue.size(), failedDevices.size(), eventLog.size());
    }
}
