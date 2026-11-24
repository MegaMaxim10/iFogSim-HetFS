package org.fog.hetfs.timing;

/**
 * Utility for running microbenchmarks and calibrating timing measurements.
 * Executes a runnable for a warmup period followed by timed repetitions,
 * collecting statistics while discarding warmup iterations.
 */
public class MicrobenchmarkRunner {

    /**
     * Timing statistics from a microbenchmark run.
     */
    public static class MicrobenchmarkStats {
        private long[] measurements;
        private long count;
        private long mean;
        private long min;
        private long max;
        private double stdDev;

        /**
         * Creates a new MicrobenchmarkStats with the given measurements.
         *
         * @param measurements the individual timing measurements in nanoseconds
         */
        public MicrobenchmarkStats(long[] measurements) {
            this.measurements = measurements;
            this.count = measurements.length;
            computeStatistics();
        }

        /**
         * Computes statistics from the measurements.
         */
        private void computeStatistics() {
            if (count == 0) {
                mean = 0;
                min = 0;
                max = 0;
                stdDev = 0.0;
                return;
            }

            long sum = 0;
            min = Long.MAX_VALUE;
            max = Long.MIN_VALUE;

            for (long m : measurements) {
                sum += m;
                min = Math.min(min, m);
                max = Math.max(max, m);
            }

            mean = sum / count;

            double varianceSum = 0.0;
            for (long m : measurements) {
                double diff = m - mean;
                varianceSum += diff * diff;
            }

            double variance = varianceSum / (count > 1 ? count - 1 : count);
            stdDev = Math.sqrt(variance);
        }

        /**
         * Gets the number of measurements.
         *
         * @return the sample count
         */
        public long getCount() {
            return count;
        }

        /**
         * Gets the mean measurement in nanoseconds.
         *
         * @return the mean in nanoseconds
         */
        public long getMeanNanos() {
            return mean;
        }

        /**
         * Gets the mean measurement in milliseconds.
         *
         * @return the mean in milliseconds
         */
        public double getMeanMs() {
            return mean / 1_000_000.0;
        }

        /**
         * Gets the minimum measurement in nanoseconds.
         *
         * @return the minimum in nanoseconds
         */
        public long getMinNanos() {
            return min;
        }

        /**
         * Gets the minimum measurement in milliseconds.
         *
         * @return the minimum in milliseconds
         */
        public double getMinMs() {
            return min / 1_000_000.0;
        }

        /**
         * Gets the maximum measurement in nanoseconds.
         *
         * @return the maximum in nanoseconds
         */
        public long getMaxNanos() {
            return max;
        }

        /**
         * Gets the maximum measurement in milliseconds.
         *
         * @return the maximum in milliseconds
         */
        public double getMaxMs() {
            return max / 1_000_000.0;
        }

        /**
         * Gets the standard deviation in nanoseconds.
         *
         * @return the standard deviation in nanoseconds
         */
        public double getStdDevNanos() {
            return stdDev;
        }

        /**
         * Gets the standard deviation in milliseconds.
         *
         * @return the standard deviation in milliseconds
         */
        public double getStdDevMs() {
            return stdDev / 1_000_000.0;
        }

        @Override
        public String toString() {
            return String.format("MicrobenchmarkStats(n=%d, mean=%.3fms, stdDev=%.3fms, " +
                            "min=%.3fms, max=%.3fms)", count, getMeanMs(), getStdDevMs(),
                    getMinMs(), getMaxMs());
        }
    }

    /**
     * Runs a microbenchmark with warmup and measurement phases.
     *
     * @param runnable the code to benchmark
     * @param warmupIterations the number of warmup iterations to discard
     * @param measuredIterations the number of iterations to measure
     * @return statistics from the measured iterations
     */
    public static MicrobenchmarkStats runMicrobenchmark(Runnable runnable,
                                                         int warmupIterations,
                                                         int measuredIterations) {
        // Warmup phase
        for (int i = 0; i < warmupIterations; i++) {
            runnable.run();
        }

        // Measurement phase
        long[] measurements = new long[measuredIterations];

        for (int i = 0; i < measuredIterations; i++) {
            long startNano = System.nanoTime();
            runnable.run();
            long endNano = System.nanoTime();
            measurements[i] = endNano - startNano;
        }

        return new MicrobenchmarkStats(measurements);
    }

    /**
     * Convenience method for running a microbenchmark with default parameters.
     * Uses 100 warmup iterations and 1000 measured iterations.
     *
     * @param runnable the code to benchmark
     * @return statistics from the measured iterations
     */
    public static MicrobenchmarkStats runMicrobenchmark(Runnable runnable) {
        return runMicrobenchmark(runnable, 100, 1000);
    }

    /**
     * Runs a microbenchmark and prints statistics to console.
     *
     * @param name the benchmark name
     * @param runnable the code to benchmark
     * @param warmupIterations the number of warmup iterations
     * @param measuredIterations the number of iterations to measure
     * @return statistics from the measured iterations
     */
    public static MicrobenchmarkStats runAndPrint(String name, Runnable runnable,
                                                   int warmupIterations,
                                                   int measuredIterations) {
        System.out.println("Running microbenchmark: " + name);
        System.out.println("  Warmup: " + warmupIterations + " iterations");
        System.out.println("  Measurement: " + measuredIterations + " iterations");

        MicrobenchmarkStats stats = runMicrobenchmark(runnable, warmupIterations, measuredIterations);

        System.out.println("Results:");
        System.out.println("  " + stats.toString());

        return stats;
    }
}
