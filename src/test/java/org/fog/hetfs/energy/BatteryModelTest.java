package org.fog.hetfs.energy;

import static org.fog.hetfs.support.ScenarioProcess.num;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.fog.hetfs.support.ScenarioProcess;
import org.junit.Test;

public class BatteryModelTest {

    @Test
    public void idleDrainExhaustsAtTheAnalyticalTime() {
        BatteryModel b = new BatteryModel(10, 0, 0, 2); // 10 J at 2 W -> 5 s
        b.advanceTo(4000);
        assertEquals(2.0, b.getRemainingJ(), 1e-12);
        b.advanceTo(9000);
        assertTrue(b.isExhausted());
        assertEquals(5000, b.getExhaustedAtMs(), 1e-9);
        assertEquals(10, b.getIdleEnergyJ(), 1e-12);
    }

    @Test
    public void transmissionsCostFixedPlusPerByte() {
        BatteryModel b = new BatteryModel(1, 0.1, 0.001, 0);
        assertTrue(b.transmit(0, 100));      // 0.2 J
        assertEquals(0.8, b.getRemainingJ(), 1e-12);
        for (int i = 0; i < 4; i++) {
            assertTrue(b.transmit(i, 100));
        }
        assertFalse(b.transmit(10, 100));
        assertEquals(5, b.getTransmissions());
        assertEquals(10, b.getExhaustedAtMs(), 0);
    }

    @Test
    public void projectedLifetimeExtrapolatesTheAverageDrain() {
        BatteryModel b = new BatteryModel(100, 0, 0, 1);
        b.advanceTo(10000); // 10 J used in 10 s
        assertEquals(100000, b.projectedLifetimeMs(10000), 1e-6);
    }

    @Test
    public void milliampHourConversion() {
        assertEquals(3.7 * 2.5 * 3600, BatteryModel.joules(2500, 3.7), 1e-9);
    }

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
