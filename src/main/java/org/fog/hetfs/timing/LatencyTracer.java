package org.fog.hetfs.timing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fog.entities.Tuple;

/**
 * Tuple-lineage latency tracer (module D6, challenge C9 of the companion paper).
 *
 * <p>{@code TimeKeeper} measures a latency between the two ends of an
 * {@code AppLoop}, with two structural limits: a loop is credited when its tuple
 * <em>arrives</em> at the end module, before that module's queueing and execution,
 * and a tuple id is overwritten whenever it crosses the start of another loop, so
 * that a loop overlapping another one at an interior module cannot be tracked. The
 * tracer instead records the whole lineage of every tuple emitted by a traced sensor:
 * each derived tuple remembers its parent, and for every module it visits the tracer
 * stores its arrival and completion times. Any segment between two
 * {@link Point}s of the lineage of a tuple delivered to an actuator can then be
 * measured afterwards, without declaring loops in advance.
 *
 * <p>The tracer is fed by {@code HetFSSensor}, {@code HetFSActuator} and
 * {@code HetFSFogDevice}. One tracer is shared by all the entities of a simulation.
 */
public class LatencyTracer {

    /** A point on the path of a tuple. */
    public static final class Point {
        enum Kind { EMIT, ARRIVAL, COMPLETION, ACTUATOR }

        final Kind kind;
        final String module;

        private Point(Kind kind, String module) {
            this.kind = kind;
            this.module = module;
        }

        /** Emission by the sensor at the root of the lineage. */
        public static Point emit() {
            return new Point(Kind.EMIT, null);
        }

        /** Hand-over of the tuple to {@code module} (where TimeKeeper ends a loop). */
        public static Point arrival(String module) {
            return new Point(Kind.ARRIVAL, module);
        }

        /** Detected completion of the tuple on {@code module}. */
        public static Point completion(String module) {
            return new Point(Kind.COMPLETION, module);
        }

        /** Delivery to the actuator. */
        public static Point actuator() {
            return new Point(Kind.ACTUATOR, null);
        }

        @Override
        public String toString() {
            return kind + (module == null ? "" : "(" + module + ")");
        }
    }

    private static final class Record {
        final int parent;
        final int root;
        String module;
        double arrival = Double.NaN;
        double completion = Double.NaN;

        Record(int parent, int root) {
            this.parent = parent;
            this.root = root;
        }
    }

    private static final class Root {
        final String sensor;
        final double emitTime;

        Root(String sensor, double emitTime) {
            this.sensor = sensor;
            this.emitTime = emitTime;
        }
    }

    private static final class Delivery {
        final int tuple;
        final double time;

        Delivery(int tuple, double time) {
            this.tuple = tuple;
            this.time = time;
        }
    }

    private final Map<Integer, Record> records = new HashMap<>();
    private final Map<Integer, Root> roots = new HashMap<>();
    private final Map<String, List<Delivery>> deliveries = new LinkedHashMap<>();
    private final Map<String, Long> emitted = new LinkedHashMap<>();
    private final Map<String, Long> drops = new LinkedHashMap<>();

    // ------------------------------------------------------------------
    // Recording
    // ------------------------------------------------------------------

    public void onEmit(Tuple tuple, String sensorName, double clock) {
        int id = tuple.getCloudletId();
        roots.put(id, new Root(sensorName, clock));
        records.put(id, new Record(-1, id));
        emitted.merge(sensorName, 1L, Long::sum);
    }

    public void onModuleArrival(Tuple tuple, String module, double clock) {
        Record r = records.get(tuple.getCloudletId());
        if (r != null) {
            r.module = module;
            r.arrival = clock;
        }
    }

    public void onModuleCompletion(Tuple tuple, List<Tuple> results, double clock) {
        Record r = records.get(tuple.getCloudletId());
        if (r == null) {
            return;
        }
        r.completion = clock;
        for (Tuple res : results) {
            records.put(res.getCloudletId(), new Record(tuple.getCloudletId(), r.root));
        }
    }

    public void onActuatorArrival(Tuple tuple, String actuatorName, double clock) {
        if (records.containsKey(tuple.getCloudletId())) {
            deliveries.computeIfAbsent(actuatorName, k -> new ArrayList<>())
                    .add(new Delivery(tuple.getCloudletId(), clock));
        }
    }

    public void onDrop(Tuple tuple, String reason) {
        if (records.containsKey(tuple.getCloudletId())) {
            drops.merge(reason, 1L, Long::sum);
        }
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /**
     * Statistics of the latency between {@code from} and {@code to} over the tuples
     * delivered to {@code actuatorName} whose lineage starts at {@code sensorName}
     * ({@code null} for any sensor) and was emitted at or after {@code sinceMs}.
     * Deliveries whose lineage does not contain both points are ignored.
     */
    public RunningStatistics segment(String sensorName, String actuatorName, Point from, Point to, double sinceMs) {
        RunningStatistics st = new RunningStatistics();
        for (Delivery d : deliveries.getOrDefault(actuatorName, new ArrayList<Delivery>())) {
            Root root = roots.get(records.get(d.tuple).root);
            if ((sensorName != null && !sensorName.equals(root.sensor)) || root.emitTime < sinceMs) {
                continue;
            }
            double a = timeOf(d, root, from);
            double b = timeOf(d, root, to);
            if (!Double.isNaN(a) && !Double.isNaN(b)) {
                st.add(b - a);
            }
        }
        return st;
    }

    /** Sensor emission to actuator delivery. */
    public RunningStatistics endToEnd(String sensorName, String actuatorName) {
        return segment(sensorName, actuatorName, Point.emit(), Point.actuator(), 0);
    }

    private double timeOf(Delivery d, Root root, Point p) {
        switch (p.kind) {
            case EMIT:
                return root.emitTime;
            case ACTUATOR:
                return d.time;
            default:
                for (Integer id = d.tuple; id != null && id >= 0; ) {
                    Record r = records.get(id);
                    if (r == null) {
                        break;
                    }
                    if (p.module.equals(r.module)) {
                        return p.kind == Point.Kind.ARRIVAL ? r.arrival : r.completion;
                    }
                    id = r.parent;
                }
                return Double.NaN;
        }
    }

    /** Number of tuples delivered to {@code actuatorName} that descend from {@code sensorName}. */
    public long deliveredCount(String sensorName, String actuatorName) {
        long n = 0;
        for (Delivery d : deliveries.getOrDefault(actuatorName, new ArrayList<Delivery>())) {
            if (sensorName == null || sensorName.equals(roots.get(records.get(d.tuple).root).sensor)) {
                n++;
            }
        }
        return n;
    }

    public long emittedCount(String sensorName) {
        return emitted.getOrDefault(sensorName, 0L);
    }

    /** Traced tuples dropped, by reason. */
    public Map<String, Long> getDrops() {
        return new LinkedHashMap<>(drops);
    }

    public int getRecordCount() {
        return records.size();
    }
}
