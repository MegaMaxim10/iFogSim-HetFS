package org.fog.hetfs.timing;

import java.util.*;

/**
 * Generates a pretty-printed timing report table for all recorded application loops.
 * Displays mean, standard deviation, confidence intervals, min, and max latencies.
 */
public class TimingReport {

    /**
     * Generates a formatted timing report for all recorded loops.
     *
     * @param timeKeeper the NanoTimeKeeper instance containing timing data
     * @return a formatted string suitable for console output
     */
    public static String generateReport(NanoTimeKeeper timeKeeper) {
        Set<String> loopIds = timeKeeper.getLoopIds();

        if (loopIds.isEmpty()) {
            return "No timing data recorded.\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("=== TIMING REPORT ===\n");
        sb.append(String.format("%-30s %8s %12s %12s %12s %12s %12s%n",
                "Loop", "N", "Mean (ms)", "StdDev", "95% CI Lower", "95% CI Upper", "Min (ms)"));
        sb.append(String.format("%-30s %8s %12s %12s %12s %12s %12s%n",
                "-".repeat(30), "-".repeat(8), "-".repeat(12), "-".repeat(12),
                "-".repeat(12), "-".repeat(12), "-".repeat(12)));

        for (String loopId : new TreeSet<>(loopIds)) {
            NanoTimeKeeper.TimingStats stats = timeKeeper.getStats(loopId);
            if (stats == null) {
                continue;
            }

            double mean = stats.getMean();
            double stdDev = stats.getStdDev();
            double[] ci = timeKeeper.getCI95(loopId);
            double min = stats.getMin();
            double max = stats.getMax();
            long count = stats.getCount();

            sb.append(String.format("%-30s %8d %12.3f %12.3f %12.3f %12.3f %12.3f%n",
                    truncate(loopId, 30), count, mean, stdDev, ci[0], ci[1], min));
        }

        sb.append("\n");

        return sb.toString();
    }

    /**
     * Generates a compact timing report suitable for logging.
     *
     * @param timeKeeper the NanoTimeKeeper instance containing timing data
     * @return a formatted string with one line per loop
     */
    public static String generateCompactReport(NanoTimeKeeper timeKeeper) {
        Set<String> loopIds = timeKeeper.getLoopIds();

        if (loopIds.isEmpty()) {
            return "No timing data recorded.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("TIMING SUMMARY: ");

        for (String loopId : new TreeSet<>(loopIds)) {
            double mean = timeKeeper.getMeanMs(loopId);
            double stdDev = timeKeeper.getStdDevMs(loopId);
            long count = timeKeeper.getCount(loopId);

            sb.append(String.format("%s: %.3f±%.3f ms (n=%d) | ",
                    loopId, mean, stdDev, count));
        }

        return sb.toString();
    }

    /**
     * Generates a detailed report including all statistics.
     *
     * @param timeKeeper the NanoTimeKeeper instance containing timing data
     * @return a multi-line formatted report
     */
    public static String generateDetailedReport(NanoTimeKeeper timeKeeper) {
        Set<String> loopIds = timeKeeper.getLoopIds();

        if (loopIds.isEmpty()) {
            return "No timing data recorded.\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== DETAILED TIMING REPORT ===\n\n");

        for (String loopId : new TreeSet<>(loopIds)) {
            NanoTimeKeeper.TimingStats stats = timeKeeper.getStats(loopId);
            if (stats == null) {
                continue;
            }

            double mean = stats.getMean();
            double stdDev = stats.getStdDev();
            double[] ci = timeKeeper.getCI95(loopId);
            double min = stats.getMin();
            double max = stats.getMax();
            long count = stats.getCount();

            sb.append(String.format("Loop: %s%n", loopId));
            sb.append(String.format("  Sample Count:        %d%n", count));
            sb.append(String.format("  Mean:                %.4f ms%n", mean));
            sb.append(String.format("  Standard Deviation:  %.4f ms%n", stdDev));
            sb.append(String.format("  95%% CI:              [%.4f, %.4f] ms%n", ci[0], ci[1]));
            sb.append(String.format("  Min:                 %.4f ms%n", min));
            sb.append(String.format("  Max:                 %.4f ms%n", max));
            sb.append(String.format("  Range:               %.4f ms%n", max - min));
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Truncates a string to a maximum length, adding ellipsis if needed.
     *
     * @param str the string to truncate
     * @param maxLength the maximum length
     * @return the truncated string
     */
    private static String truncate(String str, int maxLength) {
        if (str.length() > maxLength) {
            return str.substring(0, maxLength - 3) + "...";
        }
        return str;
    }
}
