package org.fog.hetfs.stateful;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.fog.entities.Tuple;
import org.junit.Test;

public class StatefulModulesTest {

    private double now;

    private ModuleContext ctx(ModuleState s) {
        return new ModuleContext("d", 1, "m", s, () -> now);
    }

    private static Tuple tuple(String key) {
        Tuple t = new Tuple("app", 1, Tuple.UP, 1000, 1, 1, 1, new UtilizationModelFull(),
                new UtilizationModelFull(), new UtilizationModelFull());
        t.setModuleCopyMap(new HashMap<String, Integer>());
        t.setTupleType(key);
        return t;
    }

    @Test
    public void snapshotIsADeepCopy() {
        ModuleState s = new ModuleState();
        ArrayList<Integer> list = new ArrayList<>(Collections.singletonList(1));
        s.put("list", list);
        s.increment("n");
        byte[] snap = s.snapshot();
        list.add(2);
        s.increment("n");
        s.restore(snap);
        ArrayList<Integer> restored = s.get("list");
        assertEquals(1, restored.size());
        assertEquals(1L, (long) s.getOrDefault("n", 0L));
    }

    @Test
    public void lruHitsMissesAndEvictions() {
        LruCacheBehaviour c = new LruCacheBehaviour(Tuple::getTupleType, 2, 5, Double.POSITIVE_INFINITY);
        ModuleState s = new ModuleState();
        ModuleContext x = ctx(s);
        assertEquals(1000, c.onTupleArrival(tuple("a"), 1000, x));   // miss
        assertFalse(x.consumeBypassAcceleration());
        c.onTupleCompleted(tuple("a"), null, x);
        assertEquals(5, c.onTupleArrival(tuple("a"), 1000, x));      // hit
        assertTrue(x.consumeBypassAcceleration());
        c.onTupleCompleted(tuple("b"), null, x);                     // insert b
        c.onTupleArrival(tuple("a"), 1000, x);                       // touch a
        c.onTupleCompleted(tuple("c"), null, x);                     // evicts b (LRU)
        assertEquals(1000, c.onTupleArrival(tuple("b"), 1000, x));
        assertEquals(5, c.onTupleArrival(tuple("a"), 1000, x));
        assertEquals(3, LruCacheBehaviour.hits(s));
        assertEquals(2, LruCacheBehaviour.misses(s));
        assertEquals(1L, (long) s.getOrDefault(LruCacheBehaviour.EVICTIONS, 0L));
        assertEquals(2, LruCacheBehaviour.size(s));
    }

    @Test
    public void expiredEntriesAreMisses() {
        LruCacheBehaviour c = new LruCacheBehaviour(Tuple::getTupleType, 4, 5, 100);
        ModuleState s = new ModuleState();
        now = 0;
        c.onTupleCompleted(tuple("a"), null, ctx(s));
        now = 100;
        assertEquals(5, c.onTupleArrival(tuple("a"), 1000, ctx(s)));
        now = 100.5;
        assertEquals(1000, c.onTupleArrival(tuple("a"), 1000, ctx(s)));
        assertEquals(0, LruCacheBehaviour.size(s));
    }

    @Test
    public void cacheSurvivesACheckpointRestoreCycle() {
        LruCacheBehaviour c = new LruCacheBehaviour(Tuple::getTupleType, 4, 5, Double.POSITIVE_INFINITY);
        ModuleState s = new ModuleState();
        c.onTupleCompleted(tuple("a"), null, ctx(s));
        byte[] snap = s.snapshot();
        s.clear();
        assertEquals(1000, c.onTupleArrival(tuple("a"), 1000, ctx(s)));
        s.restore(snap);
        assertEquals(5, c.onTupleArrival(tuple("a"), 1000, ctx(s)));
    }
}
