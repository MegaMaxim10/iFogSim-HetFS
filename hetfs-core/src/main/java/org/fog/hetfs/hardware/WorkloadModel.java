package org.fog.hetfs.hardware;

import org.fog.entities.Tuple;

/**
 * Input-dependent cost of a module (module D2, challenge C3 of the companion paper).
 *
 * <p>In iFogSim the cost of processing a tuple is the fixed {@code tupleCpuLength} of
 * the edge that produced it, whatever the size of the input. A {@code WorkloadModel}
 * computes the length to execute from the tuple and from the current problem size.
 */
public interface WorkloadModel {

    /**
     * @param tuple      the tuple about to be executed
     * @param edgeLength the length (MI) declared on the application edge
     * @return the length (MI) to execute, at least 1
     */
    long cpuLength(Tuple tuple, long edgeLength);
}
