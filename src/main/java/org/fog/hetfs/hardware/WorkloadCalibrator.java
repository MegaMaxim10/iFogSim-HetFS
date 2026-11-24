package org.fog.hetfs.hardware;

import java.util.Arrays;

/**
 * Calibrates the MI-per-operation constant of a {@link ComplexityWorkload} from a
 * measured implementation of the algorithm.
 *
 * <p>The kernel is executed {@code warmup} times to let the JIT compile it, then
 * {@code samples} times with {@link System#nanoTime()}. The median duration (more
 * robust than the mean to garbage-collection and scheduling pauses) is interpreted
 * as the execution time of the kernel on a processor of {@code referenceMips}, which
 * gives its length in MI under iFogSim's convention that MI / MIPS is a time in ms.
 * That interpretation is an assumption: it equates the measuring host with the
 * reference processor, and must be stated with the results.
 */
public final class WorkloadCalibrator {

    private WorkloadCalibrator() {
    }

    /** Result of a calibration. */
    public static final class Calibration {
        public final double medianMs;
        public final double minMs;
        public final double p10Ms;
        public final double p90Ms;
        public final double maxMs;
        public final double lengthMi;
        public final double miPerOperation;

        Calibration(double medianMs, double minMs, double p10Ms, double p90Ms, double maxMs,
                    double lengthMi, double miPerOperation) {
            this.medianMs = medianMs;
            this.minMs = minMs;
            this.p10Ms = p10Ms;
            this.p90Ms = p90Ms;
            this.maxMs = maxMs;
            this.lengthMi = lengthMi;
            this.miPerOperation = miPerOperation;
        }
    }

    public static double medianMs(Runnable kernel, int warmup, int samples) {
        return measure(kernel, warmup, samples)[samples / 2];
    }

    private static double[] measure(Runnable kernel, int warmup, int samples) {
        for (int i = 0; i < warmup; i++) {
            kernel.run();
        }
        double[] t = new double[samples];
        for (int i = 0; i < samples; i++) {
            long start = System.nanoTime();
            kernel.run();
            t[i] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(t);
        return t;
    }

    public static Calibration calibrate(Runnable kernel, double operations, double referenceMips,
                                        int warmup, int samples) {
        double[] t = measure(kernel, warmup, samples);
        double median = t[samples / 2];
        double mi = median * referenceMips;
        return new Calibration(median, t[0], t[samples / 10], t[(9 * samples) / 10], t[samples - 1],
                mi, mi / operations);
    }
}
