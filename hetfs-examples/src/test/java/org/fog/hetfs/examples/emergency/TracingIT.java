package org.fog.hetfs.examples.emergency;

import static org.fog.hetfs.examples.support.ScenarioProcess.num;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.fog.hetfs.examples.support.ScenarioProcess;
import org.junit.Test;

/** Module D6 against TimeKeeper on the case study. */
public class TracingIT {

    private static Map<String, String> run(String... args) throws Exception {
        String[] all = new String[args.length + 1];
        all[0] = "--trace";
        System.arraycopy(args, 0, all, 1, args.length);
        return ScenarioProcess.run("org.fog.hetfs.examples.emergency.EmergencyScenario", all);
    }

    @Test
    public void tracerReproducesTimeKeeperWithTheSameBoundaries() throws Exception {
        Map<String, String> r = run("--zones", "z1", "--seed", "2");
        assertEquals(num(r, "alert_latency_ms"), num(r, "trace_alert_tk_ms"), 1e-6);
        assertEquals(num(r, "dispatch_latency_ms"), num(r, "trace_dispatch_tk_ms"), 1e-6);
    }

    @Test
    public void tracingChangesNoTimeKeeperResult() throws Exception {
        Map<String, String> plain = ScenarioProcess.run("org.fog.hetfs.examples.emergency.EmergencyScenario",
                "--zones", "z1,z2", "--seed", "3");
        Map<String, String> traced = run("--zones", "z1,z2", "--seed", "3");
        assertEquals(plain.get("alert_latency_ms"), traced.get("alert_latency_ms"));
        assertEquals(plain.get("dispatch_latency_ms"), traced.get("dispatch_latency_ms"));
    }

    /** Under concurrent load the two-loop sum misses the contention at the ESN boundary. */
    @Test
    public void endToEndExceedsTheSumOfTheLoopsUnderLoad() throws Exception {
        Map<String, String> r = run("--zones", "z1,z2", "--seed", "1");
        assertTrue(num(r, "e2e_mean_ms") > num(r, "coordination_latency_ms") + 5);
        Map<String, String> exact = run("--zones", "z1,z2", "--seed", "1", "--exact-completion");
        assertTrue(num(exact, "e2e_mean_ms") - num(exact, "coordination_latency_ms") < 1.5);
    }
}
