package org.fog.hetfs.examples.emergency;

import static org.fog.hetfs.examples.support.ScenarioProcess.num;
import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.fog.hetfs.examples.support.ScenarioProcess;
import org.junit.Test;

/** Module D7 inside the engine: an exhausted sensor stops its application branch. */
public class BatteryIT {

    /** SAS of z1: 1 J, 10 mJ per alert, 0.5 W idle, one alert every 10 ms from t = 60 ms. */
    @Test
    public void exhaustedSensorStopsTheApplicationBranch() throws Exception {
        Map<String, String> r = ScenarioProcess.run("org.fog.hetfs.examples.emergency.EmergencyScenario",
                "--trace", "--battery", "1:0.01:0.5", "--exact-completion");
        assertEquals(700, num(r, "battery_exhausted_ms"), 1e-6);
        assertEquals(num(r, "battery_tx"), num(r, "trace_emitted"), 0);
        assertEquals(num(r, "trace_emitted"), num(r, "trace_delivered"), 0);
    }
}
