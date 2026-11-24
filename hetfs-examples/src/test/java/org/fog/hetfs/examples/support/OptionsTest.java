package org.fog.hetfs.examples.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OptionsTest {

    @Test
    public void parsesValuesAndFlags() {
        Options o = Options.parse(new String[] {"--zones", "z1,z2", "--trace", "--seed", "3"},
                "zones", "trace", "seed", "out");
        assertEquals("z1,z2", o.get("zones", null));
        assertTrue(o.has("trace"));
        assertEquals("true", o.get("trace", null));
        assertEquals(3L, o.getLong("seed", 1));
        assertFalse(o.has("out"));
        assertEquals("default", o.get("out", "default"));
        assertEquals(2.5, o.getDouble("out", 2.5), 0);
    }

    @Test
    public void negativeNumbersAreNotValues() {
        Options o = Options.parse(new String[] {"--shift", "-5"}, "shift");
        assertEquals(-5, o.getInt("shift", 0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownOptionIsRejected() {
        Options.parse(new String[] {"--zone", "z1"}, "zones");
    }

    @Test(expected = IllegalArgumentException.class)
    public void positionalArgumentIsRejected() {
        Options.parse(new String[] {"z1"}, "zones");
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingMandatoryOptionIsReported() {
        Options.parse(new String[0], "battery").require("battery");
    }
}
