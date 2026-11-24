package org.fog.hetfs.events;

import static org.fog.hetfs.support.ScenarioProcess.num;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.fog.hetfs.support.ScenarioProcess;
import org.junit.Test;

/** Module D5 on the case study. */
public class FaultInjectionScenarioTest {

    private static Map<String, String> run(String... args) throws Exception {
        String[] all = new String[args.length + 1];
        all[0] = "--exact-completion";
        System.arraycopy(args, 0, all, 1, args.length);
        return ScenarioProcess.run("org.fog.hetfs.examples.emergency.EmergencyScenario", all);
    }

    @Test
    public void uplinkFailureInATreeLosesEveryAlertOfTheOutage() throws Exception {
        Map<String, String> r = run("--graph", "tree", "--fail-link", "cad-z1~esn@500-1500");
        // the SAS of z1 emits every 10 ms: 100 alerts during the 1 s outage
        assertEquals(100, (long) num(r, "tuples_dropped"));
        assertEquals(2, (long) num(r, "topology_events"));
    }

    @Test
    public void lateralLinksRerouteAroundTheFailedUplink() throws Exception {
        Map<String, String> tree = run("--graph", "mesh");
        Map<String, String> mesh = run("--graph", "mesh", "--fail-link", "cad-z1~esn@500-1500");
        assertEquals(0, (long) num(mesh, "tuples_dropped"));
        double extra = num(mesh, "alert_latency_ms") - num(tree, "alert_latency_ms");
        // about half of the alerts take the 20 ms detour through cad-z2
        assertTrue(extra > 8 && extra < 12);
    }

    @Test
    public void deviceOutageDropsItsArrivals() throws Exception {
        Map<String, String> r = run("--fail-device", "esn@500-800");
        assertEquals(30, (long) num(r, "tuples_dropped"));
    }

    @Test
    public void checkpointsPreserveTheCacheAcrossAnOutage() throws Exception {
        String[] common = {"--routing-kernel", "--cache", "4", "--fail-device", "esn@500-800"};
        Map<String, String> withCkpt = run(concat(common, "--checkpoint-interval", "100"));
        Map<String, String> without = run(common);
        assertEquals(1, (long) num(withCkpt, "cache_misses"));
        assertEquals(2, (long) num(without, "cache_misses"));
    }

    private static String[] concat(String[] a, String... b) {
        String[] r = new String[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
