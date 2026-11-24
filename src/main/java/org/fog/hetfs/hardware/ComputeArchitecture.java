package org.fog.hetfs.hardware;

/**
 * Enumeration of compute architectures supported by the heterogeneous fog device model.
 * Each architecture has different parallelism and efficiency characteristics.
 */
public enum ComputeArchitecture {
    /**
     * CPU - traditional sequential processor.
     */
    CPU,

    /**
     * FPGA - field-programmable gate array with pipeline parallelism.
     */
    FPGA,

    /**
     * GPU - graphics processing unit with massive data parallelism.
     */
    GPU,

    /**
     * TPU - tensor processing unit optimized for machine learning workloads.
     */
    TPU
}
