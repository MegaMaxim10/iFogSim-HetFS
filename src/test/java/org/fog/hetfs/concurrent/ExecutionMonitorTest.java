package org.fog.hetfs.concurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.fog.entities.Tuple;
import org.junit.Test;

public class ExecutionMonitorTest {

    private static Tuple tuple(int id) {
        return new Tuple("app", id, Tuple.UP, 100, 1, 10, 10, new UtilizationModelFull(),
                new UtilizationModelFull(), new UtilizationModelFull());
    }

    @Test
    public void excessIsSojournBeyondNominal() {
        ExecutionMonitor m = new ExecutionMonitor();
        m.onArrival("d", "m", tuple(1), 10.0, 0.5);
        m.onArrival("d", "m", tuple(2), 10.0, 0.5);
        m.onCompletion(tuple(1), 11.0);
        m.onCompletion(tuple(2), 11.0);
        ModuleExecutionStats s = m.get("d", "m");
        assertEquals(2, s.getCompleted());
        assertEquals(1.0, s.getMeanSojournMs(), 1e-12);
        assertEquals(0.5, s.getMeanExcessMs(), 1e-12);
        assertEquals(2, s.getMaxInProgress());
        assertEquals(1, s.getArrivalsWhileBusy());
        assertEquals(0, m.getPendingCount());
    }

    @Test
    public void unknownCompletionsAreIgnored() {
        ExecutionMonitor m = new ExecutionMonitor();
        m.onCompletion(tuple(7), 3.0);
        assertNull(m.get("d", "m"));
    }
}
