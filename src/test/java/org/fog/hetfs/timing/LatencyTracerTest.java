package org.fog.hetfs.timing;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.fog.entities.Tuple;
import org.fog.hetfs.timing.LatencyTracer.Point;
import org.junit.Test;

public class LatencyTracerTest {

    private static Tuple t(int id) {
        return new Tuple("app", id, Tuple.UP, 1, 1, 1, 1, new UtilizationModelFull(),
                new UtilizationModelFull(), new UtilizationModelFull());
    }

    /** sensor -> A (fan-out to B1, B2) -> actuators X (via B1) and Y (via B2). */
    @Test
    public void segmentsFollowTheLineageOfEachDelivery() {
        LatencyTracer tr = new LatencyTracer();
        Tuple s = t(1), a1 = t(2), a2 = t(3), out1 = t(4), out2 = t(5);
        tr.onEmit(s, "S", 0);
        tr.onModuleArrival(s, "A", 5);
        tr.onModuleCompletion(s, Arrays.asList(a1, a2), 7);
        tr.onModuleArrival(a1, "B", 20);
        tr.onModuleArrival(a2, "B", 30);
        tr.onModuleCompletion(a1, Collections.singletonList(out1), 21);
        tr.onModuleCompletion(a2, Collections.singletonList(out2), 34);
        tr.onActuatorArrival(out1, "X", 25);
        tr.onActuatorArrival(out2, "Y", 40);

        assertEquals(25, tr.endToEnd("S", "X").getMean(), 0);
        assertEquals(40, tr.endToEnd("S", "Y").getMean(), 0);
        assertEquals(2, tr.segment("S", "X", Point.arrival("A"), Point.completion("A"), 0).getMean(), 0);
        assertEquals(4, tr.segment("S", "Y", Point.arrival("B"), Point.completion("B"), 0).getMean(), 0);
        assertEquals(1, tr.deliveredCount("S", "X"));
        assertEquals(1, tr.emittedCount("S"));
        // a point missing from the lineage yields no sample
        assertEquals(0, tr.segment("S", "X", Point.arrival("C"), Point.actuator(), 0).getCount());
    }

    @Test
    public void untracedTuplesAreIgnoredAndDropsCounted() {
        LatencyTracer tr = new LatencyTracer();
        tr.onActuatorArrival(t(9), "X", 1);
        assertEquals(0, tr.deliveredCount(null, "X"));
        tr.onEmit(t(1), "S", 0);
        tr.onDrop(t(1), "link down");
        assertEquals(Long.valueOf(1), tr.getDrops().get("link down"));
    }
}
