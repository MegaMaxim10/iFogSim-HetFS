package org.fog.hetfs.stateful;

import org.fog.application.AppModule;
import org.fog.application.AppTuple;
import org.cloudbus.cloudsim.core.CloudSim;

/**
 * An application module that maintains state across tuple processing invocations.
 * Provides lifecycle hooks for initialization, tuple arrival, checkpointing, and recovery.
 * Extends AppModule with stateful execution semantics.
 */
public abstract class StatefulAppModule extends AppModule {
    protected ModuleState state;

    /**
     * Creates a new stateful application module.
     *
     * @param name the module name
     */
    public StatefulAppModule(String name) {
        super(name);
        this.state = new ModuleState();
    }

    /**
     * Gets the module state.
     *
     * @return the ModuleState
     */
    public ModuleState getState() {
        return state;
    }

    /**
     * Lifecycle hook called when the module is initialized.
     * Subclasses should override to set up initial state.
     *
     * @param initialState the initial module state
     */
    public void onInit(ModuleState initialState) {
    }

    /**
     * Lifecycle hook called when a tuple arrives at this module.
     * Subclasses should override to process the tuple and update state.
     *
     * @param tuple the incoming tuple
     * @param currentState the current module state
     */
    public void onTupleArrival(AppTuple tuple, ModuleState currentState) {
    }

    /**
     * Lifecycle hook called when the module is checkpointing (creating a snapshot).
     * Subclasses should override to prepare state for serialization.
     *
     * @return the ModuleState snapshot, or null to use current state
     */
    public ModuleState onCheckpoint() {
        return state.copy();
    }

    /**
     * Lifecycle hook called when the module is recovering from a checkpoint.
     * Subclasses should override to validate or post-process recovered state.
     *
     * @param recoveredState the state restored from checkpoint
     */
    public void onRestore(ModuleState recoveredState) {
        this.state = recoveredState;
    }

    /**
     * Initializes this module with the given state.
     *
     * @param initialState the initial module state
     */
    public void initialize(ModuleState initialState) {
        this.state = initialState != null ? initialState : new ModuleState();
        onInit(this.state);
    }

    /**
     * Processes a tuple arrival and calls the onTupleArrival hook.
     *
     * @param tuple the incoming tuple
     */
    public void processTupleArrival(AppTuple tuple) {
        onTupleArrival(tuple, state);
    }

    /**
     * Creates a state checkpoint and calls the onCheckpoint hook.
     *
     * @return a byte array containing the serialized checkpoint
     */
    public byte[] createCheckpoint() {
        ModuleState snapshotState = onCheckpoint();
        if (snapshotState == null) {
            snapshotState = state;
        }
        try {
            return snapshotState.checkpoint();
        } catch (java.io.IOException e) {
            System.err.println("Failed to create checkpoint: " + e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Restores state from a checkpoint and calls the onRestore hook.
     *
     * @param checkpoint the byte array from createCheckpoint()
     */
    public void restoreFromCheckpoint(byte[] checkpoint) {
        try {
            ModuleState recoveredState = new ModuleState();
            recoveredState.restore(checkpoint);
            onRestore(recoveredState);
        } catch (java.io.IOException | ClassNotFoundException e) {
            System.err.println("Failed to restore from checkpoint: " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return String.format("StatefulAppModule(name=%s, stateSize=%d)", getName(), state.size());
    }
}
