package org.fog.hetfs.topology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.fog.entities.FogDevice;

/**
 * Physical device topology as a directed graph (module D1).
 *
 * <p>iFogSim encodes connectivity in the single integer {@code FogDevice.parentId},
 * which restricts every topology to a tree. A {@code PhysicalGraph} stores the
 * communication network as an adjacency list of {@link PhysicalLink}s instead, so
 * that meshes, lateral links between siblings and multi-homed devices can be
 * expressed. Devices are identified by their CloudSim entity identifier.
 *
 * <p>The graph also carries the availability of devices and links, which module D5
 * changes at run time. Every structural or availability change increments
 * {@link #getVersion()}, which {@link GraphRouter} uses to invalidate cached routes.
 */
public class PhysicalGraph {

    private final Map<Integer, Map<Integer, PhysicalLink>> adjacency = new LinkedHashMap<>();
    private final Set<Integer> downDevices = new HashSet<>();
    private long version;

    /** Adds a device (idempotent). */
    public void addDevice(int deviceId) {
        if (!adjacency.containsKey(deviceId)) {
            adjacency.put(deviceId, new LinkedHashMap<Integer, PhysicalLink>());
            version++;
        }
    }

    /** Adds (or replaces) the directed link {@code src -> dst}. */
    public PhysicalLink addLink(int src, int dst, double latencyMs, double bandwidth) {
        if (src == dst) {
            throw new IllegalArgumentException("self-loops are not allowed: " + src);
        }
        addDevice(src);
        addDevice(dst);
        PhysicalLink link = new PhysicalLink(src, dst, latencyMs, bandwidth);
        adjacency.get(src).put(dst, link);
        version++;
        return link;
    }

    /** Adds two directed links with identical characteristics. */
    public void addBidirectionalLink(int a, int b, double latencyMs, double bandwidth) {
        addLink(a, b, latencyMs, bandwidth);
        addLink(b, a, latencyMs, bandwidth);
    }

    public void removeLink(int src, int dst) {
        Map<Integer, PhysicalLink> out = adjacency.get(src);
        if (out != null && out.remove(dst) != null) {
            version++;
        }
    }

    public PhysicalLink getLink(int src, int dst) {
        Map<Integer, PhysicalLink> out = adjacency.get(src);
        return out == null ? null : out.get(dst);
    }

    public boolean containsDevice(int deviceId) {
        return adjacency.containsKey(deviceId);
    }

    public Set<Integer> getDeviceIds() {
        return Collections.unmodifiableSet(adjacency.keySet());
    }

    public Collection<PhysicalLink> getOutgoingLinks(int deviceId) {
        Map<Integer, PhysicalLink> out = adjacency.get(deviceId);
        return out == null ? Collections.<PhysicalLink>emptyList()
                : Collections.unmodifiableCollection(out.values());
    }

    public List<PhysicalLink> getLinks() {
        List<PhysicalLink> all = new ArrayList<>();
        for (Map<Integer, PhysicalLink> out : adjacency.values()) {
            all.addAll(out.values());
        }
        return all;
    }

    public int getDeviceCount() {
        return adjacency.size();
    }

    public int getLinkCount() {
        int n = 0;
        for (Map<Integer, PhysicalLink> out : adjacency.values()) {
            n += out.size();
        }
        return n;
    }

    // ------------------------------------------------------------------
    // Availability (driven by module D5)
    // ------------------------------------------------------------------

    public boolean isDeviceUp(int deviceId) {
        return !downDevices.contains(deviceId);
    }

    public void setDeviceUp(int deviceId, boolean up) {
        boolean changed = up ? downDevices.remove(deviceId) : downDevices.add(deviceId);
        if (changed) {
            version++;
        }
    }

    public void setLinkUp(int src, int dst, boolean up) {
        PhysicalLink l = requireLink(src, dst);
        if (l.isUp() != up) {
            l.setUp(up);
            version++;
        }
    }

    public void setLinkLatency(int src, int dst, double latencyMs) {
        requireLink(src, dst).setLatencyMs(latencyMs);
        version++;
    }

    public void setLinkBandwidth(int src, int dst, double bandwidth) {
        requireLink(src, dst).setBandwidth(bandwidth);
        version++;
    }

    /** True when a link exists in that direction, is up, and both ends are up. */
    public boolean isUsable(PhysicalLink l) {
        return l.isUp() && isDeviceUp(l.getSourceId()) && isDeviceUp(l.getDestinationId());
    }

    /** Monotonic counter incremented by every change that can affect routing. */
    public long getVersion() {
        return version;
    }

    private PhysicalLink requireLink(int src, int dst) {
        PhysicalLink l = getLink(src, dst);
        if (l == null) {
            throw new IllegalArgumentException("no link " + src + " -> " + dst);
        }
        return l;
    }

    // ------------------------------------------------------------------
    // Shortest paths
    // ------------------------------------------------------------------

    /**
     * Dijkstra's algorithm (binary heap) from {@code source}, weighted by link latency,
     * over usable links only. Ties are broken towards the smaller device identifier so
     * that routes are deterministic.
     */
    public ShortestPathTree shortestPathTree(int source) {
        Map<Integer, Double> dist = new HashMap<>();
        Map<Integer, Integer> pred = new HashMap<>();
        if (!containsDevice(source) || !isDeviceUp(source)) {
            return new ShortestPathTree(source, dist, pred);
        }
        PriorityQueue<double[]> heap = new PriorityQueue<double[]>((x, y) -> {
            int c = Double.compare(x[0], y[0]);
            return c != 0 ? c : Double.compare(x[1], y[1]);
        });
        dist.put(source, 0.0);
        heap.add(new double[] {0.0, source});
        while (!heap.isEmpty()) {
            double[] top = heap.poll();
            int u = (int) top[1];
            if (top[0] > dist.get(u)) {
                continue;
            }
            for (PhysicalLink l : getOutgoingLinks(u)) {
                if (!isUsable(l)) {
                    continue;
                }
                int v = l.getDestinationId();
                if (v == source) {
                    continue;
                }
                double nd = top[0] + l.getLatencyMs();
                Double old = dist.get(v);
                if (old == null || nd < old || (nd == old && u < pred.get(v))) {
                    dist.put(v, nd);
                    pred.put(v, u);
                    heap.add(new double[] {nd, v});
                }
            }
        }
        return new ShortestPathTree(source, dist, pred);
    }

    // ------------------------------------------------------------------
    // Conversion from an iFogSim tree
    // ------------------------------------------------------------------

    /**
     * Builds the graph equivalent to an iFogSim device tree: one link per
     * parent-child pair in each direction, with the child's uplink latency, the
     * child's uplink bandwidth upwards and the parent's downlink bandwidth downwards.
     * Routing over this graph reproduces iFogSim's own tree routing.
     */
    public static PhysicalGraph fromTree(List<? extends FogDevice> devices) {
        PhysicalGraph g = new PhysicalGraph();
        Map<Integer, FogDevice> byId = new HashMap<>();
        for (FogDevice d : devices) {
            byId.put(d.getId(), d);
            g.addDevice(d.getId());
        }
        for (FogDevice d : devices) {
            FogDevice parent = byId.get(d.getParentId());
            if (parent != null) {
                g.addLink(d.getId(), parent.getId(), d.getUplinkLatency(), d.getUplinkBandwidth());
                g.addLink(parent.getId(), d.getId(), d.getUplinkLatency(), parent.getDownlinkBandwidth());
            }
        }
        return g;
    }

    /** Result of a single-source shortest-path computation. */
    public static final class ShortestPathTree {
        private final int source;
        private final Map<Integer, Double> distance;
        private final Map<Integer, Integer> predecessor;

        ShortestPathTree(int source, Map<Integer, Double> distance, Map<Integer, Integer> predecessor) {
            this.source = source;
            this.distance = distance;
            this.predecessor = predecessor;
        }

        public int getSource() {
            return source;
        }

        public boolean reaches(int target) {
            return distance.containsKey(target);
        }

        /** Propagation latency of the shortest path, or {@code +inf} if unreachable. */
        public double distanceTo(int target) {
            Double d = distance.get(target);
            return d == null ? Double.POSITIVE_INFINITY : d;
        }

        /** Device sequence from the source to {@code target}, empty if unreachable. */
        public List<Integer> pathTo(int target) {
            if (!reaches(target)) {
                return Collections.emptyList();
            }
            List<Integer> path = new ArrayList<>();
            for (Integer v = target; v != null; v = predecessor.get(v)) {
                path.add(v);
                if (v == source) {
                    break;
                }
            }
            Collections.reverse(path);
            return path;
        }

        /** First hop towards {@code target}; the source itself if it is the target; -1 if unreachable. */
        public int nextHop(int target) {
            if (target == source) {
                return source;
            }
            if (!reaches(target)) {
                return -1;
            }
            int v = target;
            Integer p = predecessor.get(v);
            while (p != null && p != source) {
                v = p;
                p = predecessor.get(v);
            }
            return v;
        }
    }
}
