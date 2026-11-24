package org.fog.hetfs.hardware;

import java.util.function.IntSupplier;

import org.fog.entities.Tuple;

/**
 * Workload that follows the operation count of an algorithm on a graph of
 * {@code V} vertices and {@code E} edges: {@code L = a + c * ops(V, E)} MI, where the
 * fixed part {@code a} and the cost {@code c} of an elementary operation are
 * calibrated once, for instance by regressing {@link WorkloadCalibrator} measurements
 * taken at several sizes. {@code V} and {@code E} are read at every tuple, so the cost
 * follows the problem size if it changes during the simulation.
 */
public final class ComplexityWorkload implements WorkloadModel {

    /** Operation-count models. */
    public enum Complexity {
        /** {@code 1} */
        CONSTANT,
        /** {@code V + E}: breadth-first search, one relaxation pass. */
        LINEAR,
        /** {@code (V + E) log2 V}: Dijkstra with a binary heap. */
        DIJKSTRA_BINARY_HEAP,
        /** {@code V^2}: Dijkstra with an array. */
        QUADRATIC,
        /** {@code V^3}: Floyd-Warshall. */
        CUBIC;

        public double operations(int v, int e) {
            switch (this) {
                case CONSTANT:
                    return 1;
                case LINEAR:
                    return v + e;
                case DIJKSTRA_BINARY_HEAP:
                    return (v + e) * (Math.log(Math.max(v, 2)) / Math.log(2));
                case QUADRATIC:
                    return (double) v * v;
                case CUBIC:
                    return (double) v * v * v;
                default:
                    throw new AssertionError(this);
            }
        }
    }

    private final Complexity complexity;
    private final double fixedMi;
    private final double miPerOperation;
    private final IntSupplier vertices;
    private final IntSupplier edges;

    public ComplexityWorkload(Complexity complexity, double fixedMi, double miPerOperation,
                              IntSupplier vertices, IntSupplier edges) {
        if (miPerOperation <= 0) {
            throw new IllegalArgumentException("MI per operation must be positive: " + miPerOperation);
        }
        if (fixedMi < 0) {
            throw new IllegalArgumentException("fixed MI must be non-negative: " + fixedMi);
        }
        this.complexity = complexity;
        this.fixedMi = fixedMi;
        this.miPerOperation = miPerOperation;
        this.vertices = vertices;
        this.edges = edges;
    }

    /** Fixed-size problem. */
    public ComplexityWorkload(Complexity complexity, double fixedMi, double miPerOperation, int vertices, int edges) {
        this(complexity, fixedMi, miPerOperation, () -> vertices, () -> edges);
    }

    public double lengthFor(int v, int e) {
        return fixedMi + miPerOperation * complexity.operations(v, e);
    }

    @Override
    public long cpuLength(Tuple tuple, long edgeLength) {
        return Math.max(1L, Math.round(lengthFor(vertices.getAsInt(), edges.getAsInt())));
    }

    public Complexity getComplexity() {
        return complexity;
    }

    public double getFixedMi() {
        return fixedMi;
    }

    public double getMiPerOperation() {
        return miPerOperation;
    }
}
