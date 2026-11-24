package org.fog.hetfs.topology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fog.entities.Tuple;

/**
 * Routes tuples over a {@link PhysicalGraph} (module D1).
 *
 * <p>In iFogSim a tuple travels "up" to the parent or is broadcast "down" to every
 * child until it meets a device hosting its destination module. These directions are
 * meaningless in a general graph. The router instead resolves, for every tuple, the
 * device that must receive it, then forwards it hop by hop along the minimum-latency
 * path:
 * <ul>
 *   <li>a tuple addressed to an actuator type goes to the nearest device that has an
 *       actuator of that type attached;</li>
 *   <li>a tuple whose module-copy map pins its destination module to a given
 *       instance goes to the device running that instance;</li>
 *   <li>otherwise the tuple goes to the nearest reachable device hosting an instance
 *       of its destination module.</li>
 * </ul>
 * A single destination instance is therefore selected for every tuple. iFogSim's
 * downward broadcast, where several instances of the same module in the subtree all
 * receive the tuple, has no counterpart; distinct tuple types per target obtain the
 * same effect explicitly.
 *
 * <p>Shortest-path trees are cached per source device and discarded as soon as the
 * graph version changes (link or device failure, latency change, new link), so the
 * Dijkstra cost is paid once per source between two topology changes.
 */
public class GraphRouter {

    /** Returned by {@link #resolveTarget} when no device hosts the destination at all. */
    public static final int UNPLACED = -2;
    /** Returned by {@link #resolveTarget} when hosts exist but none is reachable. */
    public static final int UNREACHABLE = -1;

    private final PhysicalGraph graph;
    private final Map<Integer, RoutableDevice> devices = new LinkedHashMap<>();
    private final Map<Integer, PhysicalGraph.ShortestPathTree> cache = new HashMap<>();
    private long cachedVersion = -1;
    private long treeComputations;
    private long cacheHits;

    public GraphRouter(PhysicalGraph graph) {
        this.graph = graph;
    }

    public PhysicalGraph getGraph() {
        return graph;
    }

    /** Registers a device; it must already be a node of the graph. */
    public void register(RoutableDevice device) {
        if (!graph.containsDevice(device.getId())) {
            throw new IllegalArgumentException(device.getName() + " (id " + device.getId()
                    + ") is not a node of the physical graph");
        }
        devices.put(device.getId(), device);
    }

    public RoutableDevice getDevice(int id) {
        return devices.get(id);
    }

    public List<RoutableDevice> getDevices() {
        return Collections.unmodifiableList(new ArrayList<>(devices.values()));
    }

    // ------------------------------------------------------------------

    public PhysicalGraph.ShortestPathTree treeFrom(int source) {
        if (cachedVersion != graph.getVersion()) {
            cache.clear();
            cachedVersion = graph.getVersion();
        }
        PhysicalGraph.ShortestPathTree t = cache.get(source);
        if (t == null) {
            t = graph.shortestPathTree(source);
            cache.put(source, t);
            treeComputations++;
        } else {
            cacheHits++;
        }
        return t;
    }

    public int nextHop(int source, int target) {
        return treeFrom(source).nextHop(target);
    }

    public List<Integer> path(int source, int target) {
        return treeFrom(source).pathTo(target);
    }

    public double latency(int source, int target) {
        return treeFrom(source).distanceTo(target);
    }

    /**
     * Device that must receive {@code tuple} next as its final destination, seen from
     * device {@code here}: {@code here} itself, another device id, {@link #UNREACHABLE}
     * or {@link #UNPLACED}.
     */
    public int resolveTarget(RoutableDevice here, Tuple tuple) {
        if (tuple.getDirection() == Tuple.ACTUATOR) {
            if (here.hasActuatorOfType(tuple.getDestModuleName())) {
                return here.getId();
            }
            List<Integer> gateways = new ArrayList<>();
            for (RoutableDevice d : devices.values()) {
                if (d.hasActuatorOfType(tuple.getDestModuleName())) {
                    gateways.add(d.getId());
                }
            }
            return nearest(here.getId(), gateways);
        }
        String module = tuple.getDestModuleName();
        if (module == null) {
            return here.getId();
        }
        Integer pinned = tuple.getModuleCopyMap() == null ? null : tuple.getModuleCopyMap().get(module);
        if (pinned != null) {
            for (RoutableDevice d : devices.values()) {
                if (d.hostsModuleInstance(pinned)) {
                    return d.getId() == here.getId() || treeFrom(here.getId()).reaches(d.getId())
                            ? d.getId() : UNREACHABLE;
                }
            }
        }
        if (here.hostsModule(tuple.getAppId(), module)) {
            return here.getId();
        }
        List<Integer> hosts = new ArrayList<>();
        for (RoutableDevice d : devices.values()) {
            if (d.hostsModule(tuple.getAppId(), module)) {
                hosts.add(d.getId());
            }
        }
        return nearest(here.getId(), hosts);
    }

    private int nearest(int source, List<Integer> candidates) {
        if (candidates.isEmpty()) {
            return UNPLACED;
        }
        PhysicalGraph.ShortestPathTree t = treeFrom(source);
        int best = UNREACHABLE;
        double bestDist = Double.POSITIVE_INFINITY;
        for (int c : candidates) {
            double d = t.distanceTo(c);
            if (d < bestDist || (d == bestDist && best >= 0 && c < best)) {
                best = c;
                bestDist = d;
            }
        }
        return best;
    }

    /** Number of shortest-path trees computed so far (cache misses). */
    public long getTreeComputations() {
        return treeComputations;
    }

    public long getCacheHits() {
        return cacheHits;
    }
}
