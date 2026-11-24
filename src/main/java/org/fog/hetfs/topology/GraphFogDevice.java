package org.fog.hetfs.topology;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;

import java.util.*;

/**
 * Extends FogDevice to support arbitrary graph-based topology using a PhysicalGraph.
 * Replaces the simple parent-child tree structure with a flexible routing table that
 * can model complex network topologies. Provides automatic routing table construction
 * via Dijkstra's algorithm.
 */
public class GraphFogDevice extends FogDevice {
    private PhysicalGraph physicalGraph;
    private Map<Integer, Integer> routingTable;

    /**
     * Creates a new GraphFogDevice with the specified parameters.
     *
     * @param name the device name
     * @param mips the MIPS capacity
     * @param ram the RAM in MB
     * @param bandwidth the bandwidth in Mbps
     * @param ratePerMips the power rate per MIPS
     */
    public GraphFogDevice(String name, long mips, int ram, long bandwidth, double ratePerMips) {
        super(name, mips, ram, bandwidth, ratePerMips);
        this.physicalGraph = null;
        this.routingTable = new HashMap<>();
    }

    /**
     * Creates a new GraphFogDevice with hosts and storage.
     *
     * @param name the device name
     * @param hostList list of hosts
     * @param storageList list of storage
     * @param mips the MIPS capacity
     * @param ram the RAM in MB
     * @param bandwidth the bandwidth in Mbps
     * @param ratePerMips the power rate per MIPS
     */
    public GraphFogDevice(String name, List<? extends Host> hostList, List<Storage> storageList,
                          long mips, int ram, long bandwidth, double ratePerMips) {
        super(name, hostList, storageList, mips, ram, bandwidth, ratePerMips);
        this.physicalGraph = null;
        this.routingTable = new HashMap<>();
    }

    /**
     * Sets the physical graph reference for this device.
     *
     * @param graph the PhysicalGraph representing the topology
     */
    public void setPhysicalGraph(PhysicalGraph graph) {
        this.physicalGraph = graph;
    }

    /**
     * Gets the physical graph reference.
     *
     * @return the PhysicalGraph, or null if not set
     */
    public PhysicalGraph getPhysicalGraph() {
        return physicalGraph;
    }

    /**
     * Builds the routing table for this device using Dijkstra's shortest path algorithm.
     * The routing table maps destination device IDs to the next-hop device ID.
     *
     * @param graph the PhysicalGraph to route over
     */
    public void buildRoutingTable(PhysicalGraph graph) {
        this.physicalGraph = graph;
        this.routingTable.clear();

        if (graph == null) {
            return;
        }

        Set<Integer> destinations = graph.getAllDeviceIds();

        for (Integer dst : destinations) {
            if (dst.equals(getId())) {
                continue;
            }

            List<Integer> path = graph.getShortestPath((int) getId(), dst);
            if (path.size() >= 2) {
                int nextHop = path.get(1);
                routingTable.put(dst, nextHop);
            }
        }
    }

    /**
     * Gets the routing table for this device.
     *
     * @return a map of destination device ID to next-hop device ID
     */
    public Map<Integer, Integer> getRoutingTable() {
        return new HashMap<>(routingTable);
    }

    /**
     * Determines the next hop for a given destination using the routing table.
     *
     * @param destinationId the destination device ID
     * @return the next-hop device ID, or -1 if no route exists
     */
    public int getNextHop(int destinationId) {
        return routingTable.getOrDefault(destinationId, -1);
    }

    /**
     * Rebuilds the routing table when the topology changes (e.g., after a link failure).
     * This should be called after modifying the physical graph.
     */
    public void rebuildRoutingTable() {
        if (physicalGraph != null) {
            buildRoutingTable(physicalGraph);
        }
    }

    /**
     * Sends a tuple up through the network using graph-based routing.
     * Overrides the parent method to use the routing table instead of parentId.
     *
     * @param receiverId the receiver device ID (can be a path destination)
     * @param moduleName the module name receiving the tuple
     */
    @Override
    public void sendUpFreeLink(int receiverId, String moduleName) {
        if (physicalGraph == null) {
            super.sendUpFreeLink(receiverId, moduleName);
            return;
        }

        int nextHop = getNextHop(receiverId);
        if (nextHop == -1) {
            return;
        }

        PhysicalLink link = physicalGraph.getLink((int) getId(), nextHop);
        if (link != null && link.isUp()) {
            super.sendUpFreeLink(nextHop, moduleName);
        }
    }

    /**
     * Sends a tuple down through the network using graph-based routing.
     * Overrides the parent method to use the routing table instead of childrenIds.
     *
     * @param receiverId the receiver device ID
     * @param moduleName the module name receiving the tuple
     */
    @Override
    public void sendDownFreeLink(int receiverId, String moduleName) {
        if (physicalGraph == null) {
            super.sendDownFreeLink(receiverId, moduleName);
            return;
        }

        int nextHop = getNextHop(receiverId);
        if (nextHop == -1) {
            return;
        }

        PhysicalLink link = physicalGraph.getLink((int) getId(), nextHop);
        if (link != null && link.isUp()) {
            super.sendDownFreeLink(nextHop, moduleName);
        }
    }

    @Override
    public String toString() {
        return String.format("GraphFogDevice(id=%d, name=%s, routingTableSize=%d)",
                getId(), getName(), routingTable.size());
    }
}
