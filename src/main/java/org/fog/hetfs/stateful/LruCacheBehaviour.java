package org.fog.hetfs.stateful;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.fog.entities.Tuple;

/**
 * Reference stateful behaviour: a least-recently-used result cache (module D4).
 *
 * <p>Each tuple is mapped to a key (for a routing service, the incident zone). If the
 * key is cached and the entry is younger than the time-to-live, the tuple costs only
 * {@code hitLengthMi}, executed on the device's processor; otherwise it costs the
 * proposed length (edge or workload model, possibly accelerated), and its key is
 * inserted when its execution completes, evicting the least recently used entry when
 * the capacity is reached. Tuples arriving while the first computation for a key is
 * still running are therefore misses too, as in a real cache.
 *
 * <p>The cache and its counters live in the {@link ModuleState}, so they are lost on a
 * device failure and come back from the last checkpoint on recovery.
 */
public class LruCacheBehaviour implements ModuleBehaviour {

    static final String ENTRIES = "lru.entries";
    public static final String HITS = "lru.hits";
    public static final String MISSES = "lru.misses";
    public static final String EVICTIONS = "lru.evictions";

    private final Function<Tuple, String> keyOf;
    private final int capacity;
    private final long hitLengthMi;
    private final double ttlMs;

    /**
     * @param keyOf       key of a tuple; {@code null} keys are never cached
     * @param capacity    maximum number of entries
     * @param hitLengthMi cost of a hit, in MI
     * @param ttlMs       time to live of an entry in ms ({@code Double.POSITIVE_INFINITY} for none)
     */
    public LruCacheBehaviour(Function<Tuple, String> keyOf, int capacity, long hitLengthMi, double ttlMs) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.keyOf = keyOf;
        this.capacity = capacity;
        this.hitLengthMi = Math.max(1, hitLengthMi);
        this.ttlMs = ttlMs;
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap<String, Double> entries(ModuleState s) {
        LinkedHashMap<String, Double> m = s.get(ENTRIES);
        if (m == null) {
            m = new LinkedHashMap<>(16, 0.75f, true);
            s.put(ENTRIES, m);
        }
        return m;
    }

    @Override
    public long onTupleArrival(Tuple tuple, long proposedLength, ModuleContext ctx) {
        String key = keyOf.apply(tuple);
        LinkedHashMap<String, Double> m = entries(ctx.getState());
        Double insertedAt = key == null ? null : m.get(key);
        if (insertedAt != null && ctx.clock() - insertedAt <= ttlMs) {
            ctx.getState().increment(HITS);
            ctx.bypassAcceleration();
            return hitLengthMi;
        }
        if (insertedAt != null) {
            m.remove(key);
        }
        ctx.getState().increment(MISSES);
        return proposedLength;
    }

    @Override
    public void onTupleCompleted(Tuple tuple, List<Tuple> outputs, ModuleContext ctx) {
        String key = keyOf.apply(tuple);
        if (key == null) {
            return;
        }
        LinkedHashMap<String, Double> m = entries(ctx.getState());
        if (!m.containsKey(key)) {
            if (m.size() >= capacity) {
                String eldest = m.keySet().iterator().next();
                m.remove(eldest);
                ctx.getState().increment(EVICTIONS);
            }
            m.put(key, ctx.clock());
        } else {
            m.get(key); // touch: refresh recency, keep insertion time
        }
    }

    public static long hits(ModuleState s) {
        return s.getOrDefault(HITS, 0L);
    }

    public static long misses(ModuleState s) {
        return s.getOrDefault(MISSES, 0L);
    }

    public static int size(ModuleState s) {
        Map<?, ?> m = s.get(ENTRIES);
        return m == null ? 0 : m.size();
    }
}
