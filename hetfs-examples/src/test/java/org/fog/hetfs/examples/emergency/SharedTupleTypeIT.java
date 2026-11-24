package org.fog.hetfs.examples.emergency;

import static org.fog.hetfs.examples.support.ScenarioProcess.num;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.fog.hetfs.examples.support.ScenarioProcess;
import org.junit.Test;

/** Challenge C8 on the case study: two zones whose sensors share one tuple type. */
public class SharedTupleTypeIT {

    private static Map<String, String> run(String... extra) throws Exception {
        String[] base = {"--shared-incident-type", "--zones", "z1,z2", "--focal-zone", "z1", "--validate", "--monitor"};
        String[] a = new String[base.length + extra.length];
        System.arraycopy(base, 0, a, 0, base.length);
        System.arraycopy(extra, 0, a, base.length, extra.length);
        return ScenarioProcess.run("org.fog.hetfs.examples.emergency.EmergencyScenario", a);
    }

    @Test
    public void plainSensorsSilentlyBypassTheFocalZone() throws Exception {
        Map<String, String> r = run();
        assertEquals(2, (long) num(r, "validation_errors"));
        assertEquals(0, (long) num(r, "focal_cad_executed"));
        assertTrue(r.get("alert_latency_ms").isEmpty());
    }

    @Test
    public void hetfsSensorsReachTheirOwnZone() throws Exception {
        Map<String, String> r = run("--hetfs-sensors");
        assertEquals(0, (long) num(r, "validation_errors"));
        assertTrue(num(r, "focal_cad_executed") > 190);
        assertEquals(104.199818, num(r, "alert_latency_ms"), 1e-6);
    }
}
