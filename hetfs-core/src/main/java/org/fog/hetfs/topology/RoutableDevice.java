package org.fog.hetfs.topology;

/**
 * What {@link GraphRouter} needs to know about a device in order to decide where a
 * tuple must go. Implemented by {@code org.fog.hetfs.core.HetFSFogDevice}.
 */
public interface RoutableDevice {

    /** CloudSim entity identifier, which is also the device's node in the {@link PhysicalGraph}. */
    int getId();

    String getName();

    /** True when an instance of {@code moduleName} of application {@code appId} is placed here. */
    boolean hostsModule(String appId, String moduleName);

    /** True when the module instance (CloudSim VM) {@code vmId} runs on this device. */
    boolean hostsModuleInstance(int vmId);

    /** True when an actuator of type {@code actuatorType} is attached to this device. */
    boolean hasActuatorOfType(String actuatorType);
}
