package org.fog.hetfs.examples.emergency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * The synthetic urban road network of the smart emergency response case study:
 * 25 nodes (five incident zones z1-z5, eight response-unit stations, ten
 * intersections a1-a10 and two bridges b1-b2) and 41 undirected roads, with
 * distances in kilometres. The node and edge lists are those of the reproducibility
 * package of the companion paper (Zenodo, doi:10.5281/zenodo.22234733).
 *
 * <p>{@link #scaled(int, long)} derives larger networks of the same city by inserting
 * relay junctions in the middle of randomly chosen roads, which keeps every
 * zone-to-unit road distance unchanged; this is the construction used for the
 * 25/50/75/100-node sweep of the case study.
 */
public final class RoadNetwork {

    public static final String[] ZONES = {"z1", "z2", "z3", "z4", "z5"};
    public static final String[] UNITS = {"c1", "c2", "c3", "u1", "u2", "u3", "p1", "p2"};

    private static final Object[][] ROADS = {
        {"a1", "b2", 2.0}, {"a1", "z1", 1.5},
        {"a2", "a5", 2.5}, {"a2", "u3", 2.0}, {"a2", "z1", 4.0},
        {"a3", "a9", 2.0}, {"a3", "b2", 1.5}, {"a3", "p1", 1.0},
        {"a4", "a9", 2.0}, {"a4", "c3", 2.0}, {"a4", "u1", 1.0}, {"a4", "z3", 1.0}, {"a4", "z4", 2.3},
        {"a5", "b1", 2.0}, {"a5", "z2", 2.0},
        {"a6", "a7", 3.0}, {"a6", "a10", 1.2}, {"a6", "u2", 1.7}, {"a6", "z5", 1.4},
        {"a7", "c3", 1.2}, {"a7", "p2", 3.0}, {"a7", "z3", 2.5},
        {"a8", "a10", 2.0}, {"a8", "b1", 3.0}, {"a8", "p2", 3.0}, {"a8", "z5", 1.5},
        {"a9", "c1", 1.0}, {"a9", "u1", 1.0},
        {"a10", "c2", 1.0},
        {"b1", "c2", 2.3},
        {"b2", "c1", 2.0}, {"b2", "z1", 4.0},
        {"c2", "u2", 0.5}, {"c2", "z2", 2.5}, {"c2", "z4", 1.0},
        {"c3", "u2", 2.5},
        {"p1", "z2", 4.0}, {"p1", "z4", 1.0},
        {"p2", "z5", 2.0},
        {"u3", "z1", 1.0}, {"u3", "z2", 2.0},
        {"u2", "z4", 1.0},
    };

    private final Map<String, Map<String, Double>> adjacency = new LinkedHashMap<>();

    private RoadNetwork() {
    }

    /** The 25-node, 41-road network of the case study. */
    public static RoadNetwork base() {
        RoadNetwork n = new RoadNetwork();
        for (Object[] r : ROADS) {
            n.addRoad((String) r[0], (String) r[1], (Double) r[2]);
        }
        return n;
    }

    /**
     * The base network grown to {@code nodes} nodes by repeatedly splitting a random
     * road {@code (u,v,d)} into {@code (u,j,d/2)} and {@code (j,v,d/2)} with a new relay
     * junction {@code j}. Shortest road distances are preserved.
     */
    public static RoadNetwork scaled(int nodes, long seed) {
        RoadNetwork n = base();
        Random rnd = new Random(seed);
        int k = 0;
        while (n.nodeCount() < nodes) {
            List<String[]> roads = n.roads();
            String[] r = roads.get(rnd.nextInt(roads.size()));
            double d = n.adjacency.get(r[0]).get(r[1]);
            String j = "j" + (++k);
            n.adjacency.get(r[0]).remove(r[1]);
            n.adjacency.get(r[1]).remove(r[0]);
            n.addRoad(r[0], j, d / 2);
            n.addRoad(j, r[1], d / 2);
        }
        return n;
    }

    private void addRoad(String u, String v, double km) {
        adjacency.computeIfAbsent(u, x -> new LinkedHashMap<>()).put(v, km);
        adjacency.computeIfAbsent(v, x -> new LinkedHashMap<>()).put(u, km);
    }

    /** Undirected roads, each listed once. */
    public List<String[]> roads() {
        List<String[]> list = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> e : adjacency.entrySet()) {
            for (String v : e.getValue().keySet()) {
                if (e.getKey().compareTo(v) < 0) {
                    list.add(new String[] {e.getKey(), v});
                }
            }
        }
        Collections.sort(list, (a, b) -> (a[0] + "|" + a[1]).compareTo(b[0] + "|" + b[1]));
        return list;
    }

    public int nodeCount() {
        return adjacency.size();
    }

    public int roadCount() {
        return roads().size();
    }

    /** Dijkstra (binary heap) from {@code source}; road distances in km. */
    public Map<String, Double> shortestDistances(String source) {
        Map<String, Double> dist = new HashMap<>();
        dist.put(source, 0.0);
        PriorityQueue<Object[]> heap = new PriorityQueue<>((a, b) -> Double.compare((Double) a[0], (Double) b[0]));
        heap.add(new Object[] {0.0, source});
        while (!heap.isEmpty()) {
            Object[] top = heap.poll();
            double d = (Double) top[0];
            String u = (String) top[1];
            if (d > dist.get(u)) {
                continue;
            }
            for (Map.Entry<String, Double> nb : adjacency.get(u).entrySet()) {
                double nd = d + nb.getValue();
                Double old = dist.get(nb.getKey());
                if (old == null || nd < old) {
                    dist.put(nb.getKey(), nd);
                    heap.add(new Object[] {nd, nb.getKey()});
                }
            }
        }
        return dist;
    }
}
