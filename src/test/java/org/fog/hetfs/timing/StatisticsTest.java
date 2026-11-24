package org.fog.hetfs.timing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StatisticsTest {

    @Test
    public void welfordMatchesTheTwoPassFormulas() {
        double[] x = {197.95, 202.03, 198.74, 202.68, 197.79};
        RunningStatistics s = new RunningStatistics();
        double sum = 0;
        for (double v : x) {
            s.add(v);
            sum += v;
        }
        double mean = sum / x.length;
        double ss = 0;
        for (double v : x) {
            ss += (v - mean) * (v - mean);
        }
        assertEquals(mean, s.getMean(), 1e-9);
        assertEquals(ss / (x.length - 1), s.getVariance(), 1e-9);
        assertEquals(197.79, s.getMin(), 0);
        assertEquals(202.68, s.getMax(), 0);
        assertEquals(2.776 * Math.sqrt(ss / 4) / Math.sqrt(5), s.getConfidenceHalfWidth95(), 1e-9);
    }

    @Test
    public void welfordIsStableForLargeOffsets() {
        RunningStatistics s = new RunningStatistics();
        for (int i = 0; i < 1000; i++) {
            s.add(1e9 + (i % 2));
        }
        assertEquals(0.25 * 1000 / 999, s.getVariance(), 1e-9);
    }

    @Test
    public void studentQuantiles() {
        assertEquals(12.706, StudentT.quantile975(1), 0);
        assertEquals(2.776, StudentT.quantile975(4), 0);
        assertEquals(2.042, StudentT.quantile975(30), 0);
        assertEquals(1.960, StudentT.quantile975(10000), 0);
        assertTrue(StudentT.quantile975(4) > 1.96 * 1.4);
    }

    @Test
    public void undefinedWithTooFewSamples() {
        RunningStatistics s = new RunningStatistics();
        assertTrue(Double.isNaN(s.getMean()));
        s.add(1);
        assertTrue(Double.isNaN(s.getVariance()));
        assertTrue(Double.isNaN(s.getConfidenceHalfWidth95()));
    }
}
