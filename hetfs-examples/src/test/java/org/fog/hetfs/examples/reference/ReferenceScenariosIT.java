package org.fog.hetfs.examples.reference;

import static org.fog.hetfs.examples.support.ScenarioProcess.num;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.fog.hetfs.examples.support.ScenarioProcess;
import org.junit.Test;

/**
 * The case studies of the original iFogSim paper. Reference values were produced by
 * running iFogSim's own {@code VRGameFog} (2 departments of 5 smartphones, edge-ward)
 * and {@code DCNSFog} (1 area of 4 cameras, edge-ward) unmodified, with the generator
 * behind {@code Math.random()} seeded with 1.
 */
public class ReferenceScenariosIT {

    private static final String MAIN = "org.fog.hetfs.examples.reference.ReferenceScenarios";

    @Test
    public void portReproducesTheOriginalVrGame() throws Exception {
        Map<String, String> r = ScenarioProcess.run(MAIN, "--app", "vrgame", "--groups", "2", "--per-group", "5",
                "--seed", "1");
        assertEquals(229.40943027211446, num(r, "loop_eeg_display_ms"), 1e-6);
        assertEquals(3247030.382857115, num(r, "energy_cloud_j"), 1e-3);
        assertEquals(826574.7200000233, num(r, "cloud_cost"), 1e-3);
        assertEquals(196907.5, num(r, "network_usage"), 1e-6);
    }

    @Test
    public void portReproducesTheOriginalDcns() throws Exception {
        Map<String, String> r = ScenarioProcess.run(MAIN, "--app", "dcns", "--groups", "1", "--per-group", "4",
                "--seed", "1");
        assertEquals(120.49721760797391, num(r, "loop_motion_tracker_ms"), 1e-6);
        assertEquals(3.019999999999983, num(r, "loop_tracker_ptz_ms"), 1e-6);
        assertEquals(2666758.2688775556, num(r, "energy_cloud_j"), 1e-3);
        assertEquals(3910.4571428581366, num(r, "cloud_cost"), 1e-3);
        assertEquals(10259.6, num(r, "network_usage"), 1e-6);
    }

    @Test
    public void hetfsDevicesGiveIdenticalResults() throws Exception {
        String[] cols = {
            "loop_eeg_display_ms", "energy_cloud_j", "energy_edge_j", "energy_end_j", "network_usage", "cloud_cost"};
        for (String placement : new String[] {"edgeward", "cloud"}) {
            Map<String, String> v = ScenarioProcess.run(MAIN, "--app", "vrgame", "--groups", "4",
                    "--placement", placement, "--seed", "7");
            Map<String, String> h = ScenarioProcess.run(MAIN, "--app", "vrgame", "--groups", "4",
                    "--placement", placement, "--seed", "7", "--devices", "hetfs");
            for (String c : cols) {
                assertEquals(c, v.get(c), h.get(c));
            }
        }
    }

    @Test
    public void completionArtefactInflatesTheReferenceLoop() throws Exception {
        Map<String, String> plain = ScenarioProcess.run(MAIN, "--app", "vrgame", "--groups", "4", "--seed", "1",
                "--devices", "hetfs");
        Map<String, String> exact = ScenarioProcess.run(MAIN, "--app", "vrgame", "--groups", "4", "--seed", "1",
                "--devices", "hetfs", "--exact-completion");
        assertTrue(num(exact, "loop_eeg_display_ms") < 0.7 * num(plain, "loop_eeg_display_ms"));
    }
}
