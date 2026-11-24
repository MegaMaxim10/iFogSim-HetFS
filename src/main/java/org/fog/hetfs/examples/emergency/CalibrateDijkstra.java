package org.fog.hetfs.examples.emergency;

import java.util.Locale;

import org.fog.hetfs.hardware.ComplexityWorkload.Complexity;
import org.fog.hetfs.hardware.WorkloadCalibrator;

/**
 * Measures the road-network Dijkstra of the case study on the current host for
 * several network sizes and prints, as CSV, the median time, the operation count
 * {@code (V+E) log2 V}, the corresponding length on the 10 000-MIPS ESN and the
 * resulting MI per operation.
 *
 * <p>Usage: {@code CalibrateDijkstra [warmup] [samples] [sizes...]}
 */
public class CalibrateDijkstra {

    public static void main(String[] args) {
        int warmup = args.length > 0 ? Integer.parseInt(args[0]) : 20000;
        int samples = args.length > 1 ? Integer.parseInt(args[1]) : 2000;
        int[] sizes = {25, 50, 75, 100, 200, 400};
        if (args.length > 2) {
            sizes = new int[args.length - 2];
            for (int i = 2; i < args.length; i++) {
                sizes[i - 2] = Integer.parseInt(args[i]);
            }
        }
        System.out.println("nodes,edges,operations,median_ms,min_ms,p10_ms,p90_ms,max_ms,esn_mi,mi_per_op");
        for (int n : sizes) {
            RoadNetwork road = n == 25 ? RoadNetwork.base() : RoadNetwork.scaled(n, 7);
            double ops = Complexity.DIJKSTRA_BINARY_HEAP.operations(road.nodeCount(), road.roadCount());
            WorkloadCalibrator.Calibration c = WorkloadCalibrator.calibrate(
                    () -> road.shortestDistances("z1"), ops, EmergencyScenario.ESN_MIPS, warmup, samples);
            System.out.println(String.format(Locale.ROOT, "%d,%d,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.2f,%.6f",
                    road.nodeCount(), road.roadCount(), ops, c.medianMs, c.minMs, c.p10Ms, c.p90Ms, c.maxMs, c.lengthMi,
                    c.miPerOperation));
        }
    }
}
