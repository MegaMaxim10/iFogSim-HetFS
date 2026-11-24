package org.fog.hetfs.hardware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.fog.hetfs.hardware.ComplexityWorkload.Complexity;
import org.junit.Test;

public class AccelerationAndWorkloadTest {

    @Test
    public void fullyParallelKernelIsAPlainDivisor() {
        AccelerationProfile p = AccelerationProfile.divisor(ComputeArchitecture.FPGA, 10);
        assertEquals(100, p.effectiveLength(1000, 10000));
        assertEquals(10.0, p.amdahlSpeedup(), 1e-12);
    }

    @Test
    public void amdahlLimitsTheSpeedup() {
        AccelerationProfile p = new AccelerationProfile(ComputeArchitecture.GPU, 100, 0.9, 0);
        // 1000 * (0.1 + 0.9/100) = 109
        assertEquals(109, p.effectiveLength(1000, 10000));
        assertTrue(p.amdahlSpeedup() < 10);
    }

    @Test
    public void overheadIsPaidAtEveryInvocation() {
        AccelerationProfile p = new AccelerationProfile(ComputeArchitecture.FPGA, 10, 1.0, 0.05);
        // 1000/10 + 0.05 ms * 10000 MIPS = 100 + 500
        assertEquals(600, p.effectiveLength(1000, 10000));
    }

    @Test
    public void lengthNeverDropsToZero() {
        assertEquals(1, AccelerationProfile.divisor(ComputeArchitecture.FPGA, 50).effectiveLength(10, 1000));
    }

    @Test(expected = IllegalArgumentException.class)
    public void slowdownIsRejected() {
        new AccelerationProfile(ComputeArchitecture.FPGA, 0.5, 1, 0);
    }

    @Test
    public void onlyDeclaredModulesAreAccelerated() {
        HardwareProfile h = HardwareProfile.cpu().accelerate("k", AccelerationProfile.divisor(ComputeArchitecture.GPU, 4));
        assertEquals(250, h.effectiveLength("k", 1000, 1000));
        assertEquals(1000, h.effectiveLength("other", 1000, 1000));
        assertNull(h.accelerationOf("other"));
    }

    @Test
    public void operationCounts() {
        assertEquals(1.0, Complexity.CONSTANT.operations(10, 20), 0);
        assertEquals(30.0, Complexity.LINEAR.operations(10, 20), 0);
        assertEquals(24 * 3.0, Complexity.DIJKSTRA_BINARY_HEAP.operations(8, 16), 1e-9);
        assertEquals(100.0, Complexity.QUADRATIC.operations(10, 0), 0);
        assertEquals(1000.0, Complexity.CUBIC.operations(10, 0), 0);
    }

    @Test
    public void workloadFollowsTheProblemSize() {
        AtomicInteger v = new AtomicInteger(8);
        ComplexityWorkload w = new ComplexityWorkload(Complexity.DIJKSTRA_BINARY_HEAP, 5, 2.0, v::get, () -> 16);
        assertEquals(5 + 2 * 72, w.cpuLength(null, 1));
        v.set(16);
        assertEquals(5 + 2 * (32 * 4), w.cpuLength(null, 1));
    }

    @Test
    public void calibratorReturnsConsistentLengths() {
        WorkloadCalibrator.Calibration c = WorkloadCalibrator.calibrate(() -> Math.sqrt(2), 10, 1000, 10, 101);
        assertTrue(c.minMs <= c.medianMs && c.medianMs <= c.maxMs);
        assertEquals(c.medianMs * 1000, c.lengthMi, 1e-9);
        assertEquals(c.lengthMi / 10, c.miPerOperation, 1e-12);
    }
}
