package org.fog.hetfs.core;

import static org.fog.hetfs.support.ScenarioProcess.num;
import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.fog.hetfs.support.ScenarioProcess;
import org.junit.Test;

/**
 * Behavioural compatibility with iFogSim on the companion case study.
 *
 * <p>The reference values are those of {@code exp1_alert_dispatch.csv} and
 * {@code exp3_alert_dispatch.csv} in the reproducibility package of the companion
 * paper (Zenodo, doi:10.5281/zenodo.22234733), produced with plain iFogSim devices.
 */
public class BackwardCompatibilityTest {

    private static final String SCENARIO = "org.fog.hetfs.examples.emergency.EmergencyScenario";

    private static Map<String, String> run(String... args) throws Exception {
        return ScenarioProcess.run(SCENARIO, args);
    }

    @Test
    public void vanillaScenarioReproducesTheCompanionPackage() throws Exception {
        double[][] exp1 = {
            {104.199818, 93.753494}, {106.245549, 95.784802}, {104.709773, 94.025969},
            {106.023390, 96.656996}, {104.443817, 93.348403}};
        for (int seed = 1; seed <= 5; seed++) {
            Map<String, String> r = run("--devices", "vanilla", "--seed", "" + seed);
            assertEquals(exp1[seed - 1][0], num(r, "alert_latency_ms"), 1e-6);
            assertEquals(exp1[seed - 1][1], num(r, "dispatch_latency_ms"), 1e-6);
        }
        Map<String, String> r = run("--devices", "vanilla", "--zones", "z1,z2", "--seed", "1");
        assertEquals(104.199818, num(r, "alert_latency_ms"), 1e-6);
        assertEquals(103.086827, num(r, "dispatch_latency_ms"), 1e-6);
    }

    @Test
    public void hetfsDeviceWithoutComponentsIsIdenticalToFogDevice() throws Exception {
        for (String zones : new String[] {"z1", "z1,z2,z3"}) {
            Map<String, String> v = run("--devices", "vanilla", "--zones", zones, "--seed", "3");
            Map<String, String> h = run("--devices", "hetfs", "--zones", zones, "--seed", "3");
            assertEquals(v.get("alert_latency_ms"), h.get("alert_latency_ms"));
            assertEquals(v.get("dispatch_latency_ms"), h.get("dispatch_latency_ms"));
        }
    }

    /**
     * Graph routing over the tree itself gives the same latencies as tree routing,
     * up to the per-link (instead of per-interface) serialisation of D1. The
     * comparison uses a fine scheduling interval, because with the default 10 ms the
     * completion-detection artefact handled by D3 depends on the exact interleaving
     * of events and amplifies sub-microsecond differences into whole intervals.
     */
    @Test
    public void routingOverTheTreeGraphMatchesTreeRouting() throws Exception {
        for (String zones : new String[] {"z1", "z1,z2,z3,z4"}) {
            Map<String, String> v = run("--devices", "vanilla", "--zones", zones, "--seed", "2",
                    "--scheduling-interval", "0.01");
            Map<String, String> g = run("--graph", "tree", "--zones", zones, "--seed", "2",
                    "--scheduling-interval", "0.01");
            assertEquals(num(v, "alert_latency_ms"), num(g, "alert_latency_ms"), 0.01);
            assertEquals(num(v, "dispatch_latency_ms"), num(g, "dispatch_latency_ms"), 0.01);
        }
    }

    @Test
    public void lateralLinksAreUnusedWhenTheyAreNotShorter() throws Exception {
        Map<String, String> t = run("--graph", "tree", "--zones", "z1,z2", "--seed", "4");
        Map<String, String> m = run("--graph", "mesh", "--zones", "z1,z2", "--seed", "4");
        assertEquals(t.get("alert_latency_ms"), m.get("alert_latency_ms"));
        assertEquals(t.get("dispatch_latency_ms"), m.get("dispatch_latency_ms"));
    }
}
