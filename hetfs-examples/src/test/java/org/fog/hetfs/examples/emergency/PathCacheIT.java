package org.fog.hetfs.examples.emergency;

import static org.fog.hetfs.examples.support.ScenarioProcess.num;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.fog.hetfs.examples.support.ScenarioProcess;
import org.junit.Test;

/** Module D4 inside the engine: the path cache of the case study. */
public class PathCacheIT {

    private static Map<String, String> run(String zones, String cache) throws Exception {
        return ScenarioProcess.run("org.fog.hetfs.examples.emergency.EmergencyScenario",
                "--routing-kernel", "--monitor", "--exact-completion", "--min-time-between-events", "0.000001",
                "--mi-per-op", "1.0", "--zones", zones, "--cache", cache, "--checkpoint-interval", "500");
    }

    @Test
    public void onlyTheFirstIncidentOfEachZoneMisses() throws Exception {
        Map<String, String> r = run("z1,z2,z3", "8");
        assertEquals(3, (long) num(r, "cache_misses"));
        assertEquals(num(r, "router_n") - 3, num(r, "cache_hits"), 0);
        assertEquals(3, (long) num(r, "cache_entries"));
    }

    /**
     * Two zones fire at the same instants. With one slot, each completion evicts the
     * other zone's entry; since keys are inserted on completion, the zone whose result
     * was inserted last hits at the next period and the other misses: half the lookups
     * miss, against two in total with two slots.
     */
    @Test
    public void undersizedCacheThrashes() throws Exception {
        Map<String, String> one = run("z1,z2", "1");
        Map<String, String> two = run("z1,z2", "2");
        assertEquals(0.5, num(one, "cache_hit_ratio"), 0.01);
        assertEquals(2, (long) num(two, "cache_misses"));
    }

    @Test
    public void hitsShortenTheRouterSojourn() throws Exception {
        Map<String, String> cached = run("z1", "4");
        assertTrue(num(cached, "router_sojourn_ms") < 0.1 * num(cached, "router_cpu_ms"));
        assertTrue(num(cached, "checkpoints") >= 3);
    }
}
