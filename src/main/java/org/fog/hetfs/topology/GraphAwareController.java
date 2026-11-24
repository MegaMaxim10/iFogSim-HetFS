package org.fog.hetfs.topology;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.fog.application.AppModule;
import org.fog.entities.FogDevice;

import java.util.*;

/**
 * Extension of iFogSim's Controller that initializes graph-based routing for
 * all GraphFogDevice instances using a PhysicalGraph. Builds routing tables
 * at simulation start to enable arbitrary topology support.
 */
public class GraphAwareController extends SimEntity {
    private PhysicalGraph physicalGraph;
    private List<GraphFogDevice> graphDevices;
    private boolean initialized = false;

    /**
     * Creates a new GraphAwareController for managing graph-based fog device topology.
     *
     * @param name the controller name
     * @param graph the PhysicalGraph representing the topology
     */
    public GraphAwareController(String name, PhysicalGraph graph) {
        super(name);
        this.physicalGraph = graph;
        this.graphDevices = new ArrayList<>();
    }

    /**
     * Registers a GraphFogDevice with this controller.
     *
     * @param device the GraphFogDevice to register
     */
    public void registerDevice(GraphFogDevice device) {
        if (!graphDevices.contains(device)) {
            graphDevices.add(device);
        }
    }

    /**
     * Registers multiple GraphFogDevice instances.
     *
     * @param devices the devices to register
     */
    public void registerDevices(List<GraphFogDevice> devices) {
        for (GraphFogDevice device : devices) {
            registerDevice(device);
        }
    }

    /**
     * Initializes all registered devices with the physical graph and builds routing tables.
     * This should be called after simulation start.
     */
    public void initializeTopology() {
        if (initialized || physicalGraph == null) {
            return;
        }

        for (GraphFogDevice device : graphDevices) {
            device.setPhysicalGraph(physicalGraph);
            device.buildRoutingTable(physicalGraph);
        }

        initialized = true;
    }

    /**
     * Rebuilds routing tables for all devices when the topology changes.
     * Call this after modifying the physical graph (e.g., after link failures).
     */
    public void rebuildAllRoutingTables() {
        for (GraphFogDevice device : graphDevices) {
            device.rebuildRoutingTable();
        }
    }

    /**
     * Gets the physical graph used by this controller.
     *
     * @return the PhysicalGraph
     */
    public PhysicalGraph getPhysicalGraph() {
        return physicalGraph;
    }

    /**
     * Sets a new physical graph. Call rebuildAllRoutingTables() afterward.
     *
     * @param graph the new PhysicalGraph
     */
    public void setPhysicalGraph(PhysicalGraph graph) {
        this.physicalGraph = graph;
    }

    /**
     * Gets all registered graph devices.
     *
     * @return a list of registered GraphFogDevice instances
     */
    public List<GraphFogDevice> getGraphDevices() {
        return new ArrayList<>(graphDevices);
    }

    @Override
    public void startEntity() {
        initializeTopology();
    }

    @Override
    public void shutdownEntity() {
        graphDevices.clear();
    }

    @Override
    public void processEvent(org.cloudbus.cloudsim.core.SimEvent ev) {
    }

    @Override
    public String toString() {
        return String.format("GraphAwareController(devices=%d, initialized=%s)",
                graphDevices.size(), initialized);
    }
}
