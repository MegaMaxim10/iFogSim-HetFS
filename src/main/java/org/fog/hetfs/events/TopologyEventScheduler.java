package org.fog.hetfs.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.hetfs.core.HetFSEvents;
import org.fog.hetfs.core.HetFSFogDevice;
import org.fog.hetfs.topology.PhysicalGraph;

/**
 * CloudSim entity that applies scheduled topology changes (module D5).
 *
 * <p>In iFogSim the topology is fixed once {@code Controller.connectWithLatencies()}
 * has run, and neither {@code FogDevice} nor {@code FogEvents} offers a failure or
 * recovery mechanism (challenge C7). The scheduler posts every {@link TopologyEvent}
 * to itself as a CloudSim event, so changes happen at their exact simulation time and
 * interleave correctly with tuple events. A device failure is applied both to the
 * device ({@link HetFSFogDevice#fail()}: arriving tuples are dropped, the tuples it
 * was executing produce nothing, its module states are lost) and to the physical
 * graph (routes avoid it). Link events require a {@link PhysicalGraph}.
 */
public class TopologyEventScheduler extends SimEntity {

    private final PhysicalGraph graph;
    private final Map<Integer, HetFSFogDevice> devices = new HashMap<>();
    private final List<TopologyEvent> pending = new ArrayList<>();
    private final List<String> log = new ArrayList<>();

    /** @param graph the physical graph, or {@code null} if only device events are used */
    public TopologyEventScheduler(String name, PhysicalGraph graph) {
        super(name);
        this.graph = graph;
    }

    public void registerDevice(HetFSFogDevice device) {
        devices.put(device.getId(), device);
    }

    public void schedule(TopologyEvent event) {
        boolean linkEvent = event.getType() != TopologyEventType.DEVICE_FAIL
                && event.getType() != TopologyEventType.DEVICE_RECOVER;
        if (linkEvent && graph == null) {
            throw new IllegalStateException("link events require a physical graph");
        }
        if (!linkEvent && !devices.containsKey(event.getDeviceId())) {
            throw new IllegalArgumentException("device " + event.getDeviceId() + " is not registered");
        }
        pending.add(event);
    }

    public List<TopologyEvent> getScheduledEvents() {
        return Collections.unmodifiableList(pending);
    }

    /** One line per applied event: time, event, description. */
    public List<String> getLog() {
        return Collections.unmodifiableList(log);
    }

    @Override
    public void startEntity() {
        for (TopologyEvent e : pending) {
            schedule(getId(), e.getTime(), HetFSEvents.TOPOLOGY_EVENT, e);
        }
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() != HetFSEvents.TOPOLOGY_EVENT) {
            return;
        }
        apply((TopologyEvent) ev.getData());
    }

    void apply(TopologyEvent e) {
        switch (e.getType()) {
            case DEVICE_FAIL:
                devices.get(e.getDeviceId()).fail();
                break;
            case DEVICE_RECOVER:
                devices.get(e.getDeviceId()).recover();
                break;
            case LINK_FAIL:
            case LINK_RECOVER:
                boolean up = e.getType() == TopologyEventType.LINK_RECOVER;
                graph.setLinkUp(e.getSourceId(), e.getTargetId(), up);
                if (e.isBothDirections()) {
                    graph.setLinkUp(e.getTargetId(), e.getSourceId(), up);
                }
                break;
            case LINK_LATENCY_CHANGE:
                graph.setLinkLatency(e.getSourceId(), e.getTargetId(), e.getValue());
                if (e.isBothDirections()) {
                    graph.setLinkLatency(e.getTargetId(), e.getSourceId(), e.getValue());
                }
                break;
            case LINK_BANDWIDTH_CHANGE:
                graph.setLinkBandwidth(e.getSourceId(), e.getTargetId(), e.getValue());
                if (e.isBothDirections()) {
                    graph.setLinkBandwidth(e.getTargetId(), e.getSourceId(), e.getValue());
                }
                break;
            default:
                throw new AssertionError(e.getType());
        }
        log.add(String.format(java.util.Locale.ROOT, "%.3f %s", CloudSim.clock(), e));
    }

    @Override
    public void shutdownEntity() {
    }
}
