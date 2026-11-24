package org.fog.hetfs.stateful;

import java.util.List;

import org.fog.entities.Tuple;

/**
 * Lifecycle of a stateful module instance (module D4).
 *
 * <p>A behaviour is attached to a module on a given device with
 * {@code HetFSFogDevice#setBehaviour}. The device calls it at the following points:
 * <ul>
 *   <li>{@link #onInit} when the module instance is launched on the device;</li>
 *   <li>{@link #onTupleArrival} when a tuple is about to be executed, to decide how
 *       much work (MI) it costs given the current state;</li>
 *   <li>{@link #onTupleCompleted} when its execution is complete, with the tuples
 *       produced, to update the state;</li>
 *   <li>{@link #onRestore} when the device recovers from a failure (module D5): the
 *       state is then the last checkpoint, or empty if none was taken.</li>
 * </ul>
 * The state is the {@link ModuleState} of the context, which the device checkpoints
 * periodically when a checkpoint interval is set.
 */
public interface ModuleBehaviour {

    default void onInit(ModuleContext ctx) {
    }

    /**
     * @param proposedLength length (MI) given by the edge or by the module's workload model
     * @return length (MI) to execute for this tuple
     */
    default long onTupleArrival(Tuple tuple, long proposedLength, ModuleContext ctx) {
        return proposedLength;
    }

    default void onTupleCompleted(Tuple tuple, List<Tuple> outputs, ModuleContext ctx) {
    }

    /** @param fromCheckpoint {@code false} when no checkpoint existed and the state is empty */
    default void onRestore(ModuleContext ctx, boolean fromCheckpoint) {
    }
}
