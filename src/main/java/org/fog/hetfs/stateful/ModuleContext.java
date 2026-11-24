package org.fog.hetfs.stateful;

import java.util.function.DoubleSupplier;

import org.cloudbus.cloudsim.core.CloudSim;

/**
 * What a {@link ModuleBehaviour} sees of its execution environment (module D4).
 */
public final class ModuleContext {

    private final String deviceName;
    private final int deviceId;
    private final String moduleName;
    private final ModuleState state;
    private final DoubleSupplier clock;
    private boolean bypassAcceleration;

    public ModuleContext(String deviceName, int deviceId, String moduleName, ModuleState state) {
        this(deviceName, deviceId, moduleName, state, CloudSim::clock);
    }

    /** Context with an explicit time source (unit tests, offline replay). */
    public ModuleContext(String deviceName, int deviceId, String moduleName, ModuleState state, DoubleSupplier clock) {
        this.deviceName = deviceName;
        this.deviceId = deviceId;
        this.moduleName = moduleName;
        this.state = state;
        this.clock = clock;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public String getModuleName() {
        return moduleName;
    }

    public ModuleState getState() {
        return state;
    }

    /** Current simulation time (ms). */
    public double clock() {
        return clock.getAsDouble();
    }

    /**
     * Requests that the length returned by the current {@code onTupleArrival} call be
     * executed on the device's processor rather than on the module's accelerator (for
     * instance a cache lookup that does not run the accelerated kernel).
     */
    public void bypassAcceleration() {
        this.bypassAcceleration = true;
    }

    /** Reads and clears the request made by {@link #bypassAcceleration()}. */
    public boolean consumeBypassAcceleration() {
        boolean b = bypassAcceleration;
        bypassAcceleration = false;
        return b;
    }
}
