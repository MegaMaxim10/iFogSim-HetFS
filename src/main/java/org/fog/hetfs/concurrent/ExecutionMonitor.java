package org.fog.hetfs.concurrent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fog.entities.Tuple;

/**
 * Per-module execution monitor (module D3).
 *
 * <p>iFogSim reports latency only between the end points of an {@code AppLoop}; the
 * queueing and processor sharing that happen inside a module are visible only
 * indirectly, and not at all when the loop ends at the contended module (challenge C9
 * of the companion paper). A monitor attached to the devices records, for every
 * module instance, how long each tuple stays on it compared with its nominal service
 * time. One monitor is normally shared by all the devices of a simulation.
 */
public class ExecutionMonitor {

    private final Map<String, ModuleExecutionStats> stats = new LinkedHashMap<>();
    private final Map<Integer, double[]> pending = new HashMap<>();
    private final Map<Integer, String> pendingKey = new HashMap<>();

    private static String key(String device, String module) {
        return device + "/" + module;
    }

    /**
     * Records that {@code tuple} is handed to {@code module} on {@code device} at
     * {@code clock}, with a nominal service time of {@code nominalMs}.
     */
    public void onArrival(String device, String module, Tuple tuple, double clock, double nominalMs) {
        String k = key(device, module);
        stats.computeIfAbsent(k, x -> new ModuleExecutionStats(device, module)).arrival();
        pending.put(tuple.getCloudletId(), new double[] {clock, nominalMs});
        pendingKey.put(tuple.getCloudletId(), k);
    }

    /** Records the detected completion of {@code tuple} at {@code clock}. */
    public void onCompletion(Tuple tuple, double clock) {
        double[] p = pending.remove(tuple.getCloudletId());
        String k = pendingKey.remove(tuple.getCloudletId());
        if (p != null) {
            stats.get(k).completion(clock - p[0], p[1]);
        }
    }

    public ModuleExecutionStats get(String device, String module) {
        return stats.get(key(device, module));
    }

    public List<ModuleExecutionStats> getAll() {
        return new ArrayList<>(stats.values());
    }

    /** Number of tuples handed to a module whose completion has not been detected. */
    public int getPendingCount() {
        return pending.size();
    }
}
