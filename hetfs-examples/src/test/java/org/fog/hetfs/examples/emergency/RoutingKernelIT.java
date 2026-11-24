package org.fog.hetfs.examples.emergency;

import static org.fog.hetfs.examples.support.ScenarioProcess.num;
import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.fog.hetfs.examples.support.ScenarioProcess;
import org.fog.hetfs.hardware.ComplexityWorkload;
import org.junit.Test;

/** Module D2 inside the engine, on the case study's routing kernel. */
public class RoutingKernelIT {

    private static Map<String, String> run(String... extra) throws Exception {
        String[] base = {"--routing-kernel", "--monitor", "--exact-completion", "--mi-fixed", "100",
            "--mi-per-op", "1.0", "--min-time-between-events", "0.000001"};
        String[] args = new String[base.length + extra.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(extra, 0, args, base.length, extra.length);
        return ScenarioProcess.run("org.fog.hetfs.examples.emergency.EmergencyScenario", args);
    }

    @Test
    public void routerCostFollowsTheWorkloadModel() throws Exception {
        Map<String, String> r = run();
        double ops = ComplexityWorkload.Complexity.DIJKSTRA_BINARY_HEAP.operations(25, 42);
        assertEquals(Math.round(100 + ops), (long) num(r, "router_cpu_mi"));
        assertEquals(num(r, "router_cpu_mi") / 10000.0, num(r, "router_nominal_ms"), 1e-6);
        assertEquals(0.0, num(r, "router_excess_ms"), 1e-6);
    }

    @Test
    public void acceleratorDividesTheExecutedLength() throws Exception {
        Map<String, String> r = run("--accel", "fpga:10");
        assertEquals(Math.round(num(r, "router_cpu_mi") / 10.0) / 10000.0, num(r, "router_nominal_ms"), 1e-6);
    }

    @Test
    public void largerRoadNetworksCostMore() throws Exception {
        double small = num(run("--road-nodes", "25"), "router_cpu_mi");
        double large = num(run("--road-nodes", "100"), "router_cpu_mi");
        assertEquals(100 + ComplexityWorkload.Complexity.DIJKSTRA_BINARY_HEAP.operations(100, 117), large, 1);
        assertEquals(true, large > 3 * small);
    }
}
