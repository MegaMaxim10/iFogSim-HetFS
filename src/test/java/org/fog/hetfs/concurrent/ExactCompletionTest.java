package org.fog.hetfs.concurrent;

import static org.fog.hetfs.support.ScenarioProcess.num;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.fog.hetfs.support.ScenarioProcess;
import org.junit.Test;

/**
 * Module D3 on the companion case study: completion-detection artefact of iFogSim
 * and its correction.
 */
public class ExactCompletionTest {

    private static Map<String, String> run(String zones, boolean exact) throws Exception {
        return exact
                ? ScenarioProcess.run("org.fog.hetfs.examples.emergency.EmergencyScenario",
                        "--zones", zones, "--seed", "1", "--monitor", "--exact-completion")
                : ScenarioProcess.run("org.fog.hetfs.examples.emergency.EmergencyScenario",
                        "--zones", zones, "--seed", "1", "--monitor");
    }

    @Test
    public void withoutOverlapExactEventsChangeNothing() throws Exception {
        Map<String, String> a = run("z1", false);
        Map<String, String> b = run("z1", true);
        assertEquals(a.get("alert_latency_ms"), b.get("alert_latency_ms"));
        assertEquals(a.get("dispatch_latency_ms"), b.get("dispatch_latency_ms"));
        assertEquals(0.0, num(b, "classifier_excess_ms"), 1e-9);
        assertEquals(0.0, num(b, "dispatcher_excess_ms"), 1e-9);
        assertEquals(0.0, num(b, "iops_excess_ms"), 1e-9);
    }

    @Test
    public void defaultEngineDetectsOverlappingCompletionsLate() throws Exception {
        Map<String, String> r = run("z1,z2", false);
        // the late detection is bounded by, and close to, the 10 ms scheduling interval
        assertTrue(num(r, "classifier_max_excess_ms") > 9.0);
        assertTrue(num(r, "classifier_max_excess_ms") <= 10.0);
    }

    /**
     * N tuples submitted together to a time-shared module all finish after N nominal
     * service times: the excess is (N-1) nominal service times.
     */
    @Test
    public void exactEventsLeaveOnlyProcessorSharing() throws Exception {
        String[] zones = {"z1,z2", "z1,z2,z3", "z1,z2,z3,z4"};
        for (int i = 0; i < zones.length; i++) {
            int n = i + 2;
            Map<String, String> r = run(zones[i], true);
            assertEquals((n - 1) * 0.2, num(r, "classifier_max_excess_ms"), 1e-3);
            assertEquals((n - 1) * 0.3, num(r, "dispatcher_max_excess_ms"), 1e-3);
            assertEquals(n, (int) num(r, "classifier_max_in_progress"));
        }
    }
}
