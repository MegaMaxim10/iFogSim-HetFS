package org.fog.hetfs.topology;

import java.util.*;

/**
 * Represents the physical topology of fog devices as a directed graph with
 * configurable links. Supports adjacency-list representation, shortest path
 * computation via Dijkstra's algorithm, and dynamic link management.
 */
public class PhysicalGraph {
    private Map<Integer, FogDeviceNode> nodes;
    private Map<String, PhysicalLink> links;

    /**
     * Inner class to represent a fog device node in the graph.
     */
    private static class FogDeviceNode {
        int deviceId;
        List<Integer> outgoingNeighbors;
        List<Integer> incomingNeighbors;

        FogDeviceNode(int deviceId) {
            this.deviceId = deviceId;
            this.outgoingNeighbors = new ArrayList<>();
            this.incomingNeighbors = new ArrayList<>();
        }
    }

    /**
     * Creates an empty physical graph.
     */
    public PhysicalGraph() {
        this.nodes = new HashMap<>();
        this.links = new HashMap<>();
    }

    /**
     * Adds a fog device node to the graph.
     *
     * @param deviceId the unique identifier for the device
     */
    public void addDevice(int deviceId) {
        if (!nodes.containsKey(deviceId)) {
            nodes.put(deviceId, new FogDeviceNode(deviceId));
        }
    }

    /**
     * Adds a directed link from source to destination device.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @param latencyMs the link latency in milliseconds
     * @param bandwidthMbps the link bandwidth in Mbps
     * @return the created PhysicalLink
     */
    public PhysicalLink addLink(int srcId, int dstId, double latencyMs, double bandwidthMbps) {
        addDevice(srcId);
        addDevice(dstId);

        String key = linkKey(srcId, dstId);
        PhysicalLink link = new PhysicalLink(srcId, dstId, latencyMs, bandwidthMbps);
        links.put(key, link);

        FogDeviceNode srcNode = nodes.get(srcId);
        FogDeviceNode dstNode = nodes.get(dstId);

        if (!srcNode.outgoingNeighbors.contains(dstId)) {
            srcNode.outgoingNeighbors.add(dstId);
        }
        if (!dstNode.incomingNeighbors.contains(srcId)) {
            dstNode.incomingNeighbors.add(srcId);
        }

        return link;
    }

    /**
     * Removes a directed link from source to destination.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     */
    public void removeLink(int srcId, int dstId) {
        String key = linkKey(srcId, dstId);
        links.remove(key);

        FogDeviceNode srcNode = nodes.get(srcId);
        FogDeviceNode dstNode = nodes.get(dstId);

        if (srcNode != null) {
            srcNode.outgoingNeighbors.remove(Integer.valueOf(dstId));
        }
        if (dstNode != null) {
            dstNode.incomingNeighbors.remove(Integer.valueOf(srcId));
        }
    }

    /**
     * Sets the latency of an existing link.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @param latencyMs the new latency in milliseconds
     * @return true if the link was updated, false if it doesn't exist
     */
    public boolean setLinkLatency(int srcId, int dstId, double latencyMs) {
        String key = linkKey(srcId, dstId);
        PhysicalLink link = links.get(key);
        if (link != null) {
            link.setLatencyMs(latencyMs);
            return true;
        }
        return false;
    }

    /**
     * Sets the bandwidth of an existing link.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @param bandwidthMbps the new bandwidth in Mbps
     * @return true if the link was updated, false if it doesn't exist
     */
    public boolean setLinkBandwidth(int srcId, int dstId, double bandwidthMbps) {
        String key = linkKey(srcId, dstId);
        PhysicalLink link = links.get(key);
        if (link != null) {
            link.setBandwidthMbps(bandwidthMbps);
            return true;
        }
        return false;
    }

    /**
     * Retrieves a link from source to destination.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @return the PhysicalLink if it exists, null otherwise
     */
    public PhysicalLink getLink(int srcId, int dstId) {
        return links.get(linkKey(srcId, dstId));
    }

    /**
     * Gets the outgoing neighbors of a device (devices it can reach directly).
     *
     * @param nodeId the device ID
     * @return a list of neighbor device IDs, empty if device doesn't exist
     */
    public List<Integer> getNeighbours(int nodeId) {
        FogDeviceNode node = nodes.get(nodeId);
        return node != null ? new ArrayList<>(node.outgoingNeighbors) : new ArrayList<>();
    }

    /**
     * Gets the incoming neighbors of a device (devices that can reach it directly).
     *
     * @param nodeId the device ID
     * @return a list of incoming neighbor device IDs
     */
    public List<Integer> getIncomingNeighbours(int nodeId) {
        FogDeviceNode node = nodes.get(nodeId);
        return node != null ? new ArrayList<>(node.incomingNeighbors) : new ArrayList<>();
    }

    /**
     * Finds the shortest path between two devices using Dijkstra's algorithm,
     * with path cost based on latency.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @return a list of device IDs representing the path, empty if no path exists
     */
    public List<Integer> getShortestPath(int srcId, int dstId) {
        if (!nodes.containsKey(srcId) || !nodes.containsKey(dstId)) {
            return new ArrayList<>();
        }

        Map<Integer, Double> distances = new HashMap<>();
        Map<Integer, Integer> previous = new HashMap<>();
        Set<Integer> visited = new HashSet<>();
        PriorityQueue<Integer> queue = new PriorityQueue<>(
                Comparator.comparingDouble(distances::get));

        for (Integer nodeId : nodes.keySet()) {
            distances.put(nodeId, Double.MAX_VALUE);
        }
        distances.put(srcId, 0.0);
        queue.offer(srcId);

        while (!queue.isEmpty()) {
            Integer current = queue.poll();

            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);

            if (current.equals(dstId)) {
                break;
            }

            List<Integer> neighbors = getNeighbours(current);
            for (Integer neighbor : neighbors) {
                if (visited.contains(neighbor)) {
                    continue;
                }

                PhysicalLink link = getLink(current, neighbor);
                if (link == null || !link.isUp()) {
                    continue;
                }

                double newDist = distances.get(current) + link.getLatencyMs();
                if (newDist < distances.get(neighbor)) {
                    distances.put(neighbor, newDist);
                    previous.put(neighbor, current);
                    queue.remove(neighbor);
                    queue.offer(neighbor);
                }
            }
        }

        if (!visited.contains(dstId)) {
            return new ArrayList<>();
        }

        List<Integer> path = new ArrayList<>();
        Integer current = dstId;
        while (current != null) {
            path.add(0, current);
            current = previous.get(current);
        }
        return path;
    }

    /**
     * Gets all device IDs in the graph.
     *
     * @return a set of all device IDs
     */
    public Set<Integer> getAllDeviceIds() {
        return new HashSet<>(nodes.keySet());
    }

    /**
     * Gets all links in the graph.
     *
     * @return a collection of all physical links
     */
    public Collection<PhysicalLink> getAllLinks() {
        return new ArrayList<>(links.values());
    }

    /**
     * Checks if a device exists in the graph.
     *
     * @param deviceId the device ID to check
     * @return true if the device exists, false otherwise
     */
    public boolean hasDevice(int deviceId) {
        return nodes.containsKey(deviceId);
    }

    /**
     * Checks if a link exists between two devices.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @return true if the link exists, false otherwise
     */
    public boolean hasLink(int srcId, int dstId) {
        return links.containsKey(linkKey(srcId, dstId));
    }

    /**
     * Gets the number of devices in the graph.
     *
     * @return the number of devices
     */
    public int getDeviceCount() {
        return nodes.size();
    }

    /**
     * Gets the number of links in the graph.
     *
     * @return the number of links
     */
    public int getLinkCount() {
        return links.size();
    }

    /**
     * Generates a unique key for a link based on source and destination IDs.
     *
     * @param srcId the source device ID
     * @param dstId the destination device ID
     * @return a unique link key
     */
    private String linkKey(int srcId, int dstId) {
        return srcId + "->" + dstId;
    }

    @Override
    public String toString() {
        return String.format("PhysicalGraph(devices=%d, links=%d)", nodes.size(), links.size());
    }
}
