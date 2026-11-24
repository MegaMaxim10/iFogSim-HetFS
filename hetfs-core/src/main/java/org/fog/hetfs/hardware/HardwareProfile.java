package org.fog.hetfs.hardware;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hardware description of a device (module D2): the architecture of its main
 * processor and the accelerators available to specific modules.
 */
public final class HardwareProfile {

    private final ComputeArchitecture hostArchitecture;
    private final Map<String, AccelerationProfile> accelerated = new LinkedHashMap<>();

    public HardwareProfile(ComputeArchitecture hostArchitecture) {
        this.hostArchitecture = hostArchitecture;
    }

    public static HardwareProfile cpu() {
        return new HardwareProfile(ComputeArchitecture.CPU);
    }

    /** Declares that {@code moduleName} runs on an accelerator described by {@code profile}. */
    public HardwareProfile accelerate(String moduleName, AccelerationProfile profile) {
        accelerated.put(moduleName, profile);
        return this;
    }

    public AccelerationProfile accelerationOf(String moduleName) {
        return accelerated.get(moduleName);
    }

    public Map<String, AccelerationProfile> getAccelerations() {
        return Collections.unmodifiableMap(accelerated);
    }

    public ComputeArchitecture getHostArchitecture() {
        return hostArchitecture;
    }

    /** Length to execute for {@code moduleName}; unchanged if the module is not accelerated. */
    public long effectiveLength(String moduleName, long mi, double deviceMips) {
        AccelerationProfile p = accelerated.get(moduleName);
        return p == null ? mi : p.effectiveLength(mi, deviceMips);
    }
}
