package org.fog.hetfs.hardware;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.hetfs.topology.GraphFogDevice;

import java.util.List;

/**
 * A GraphFogDevice that supports heterogeneous hardware acceleration through a
 * HardwareProfile. Divides effective execution time by the parallelism degree
 * for FPGA, GPU, and TPU architectures, enabling acceleration modeling.
 */
public class HeterogeneousFogDevice extends GraphFogDevice {
    private HardwareProfile hardwareProfile;
    private ParallelCloudletScheduler parallelScheduler;

    /**
     * Creates a new HeterogeneousFogDevice with specified parameters.
     *
     * @param name the device name
     * @param mips the MIPS capacity
     * @param ram the RAM in MB
     * @param bandwidth the bandwidth in Mbps
     * @param ratePerMips the power rate per MIPS
     * @param hardwareProfile the hardware configuration
     */
    public HeterogeneousFogDevice(String name, long mips, int ram, long bandwidth,
                                  double ratePerMips, HardwareProfile hardwareProfile) {
        super(name, mips, ram, bandwidth, ratePerMips);
        this.hardwareProfile = hardwareProfile;
        this.parallelScheduler = new ParallelCloudletScheduler(hardwareProfile.getParallelismDegree());
    }

    /**
     * Creates a new HeterogeneousFogDevice with hosts and storage.
     *
     * @param name the device name
     * @param hostList list of hosts
     * @param storageList list of storage
     * @param mips the MIPS capacity
     * @param ram the RAM in MB
     * @param bandwidth the bandwidth in Mbps
     * @param ratePerMips the power rate per MIPS
     * @param hardwareProfile the hardware configuration
     */
    public HeterogeneousFogDevice(String name, List<? extends Host> hostList,
                                  List<Storage> storageList, long mips, int ram,
                                  long bandwidth, double ratePerMips,
                                  HardwareProfile hardwareProfile) {
        super(name, hostList, storageList, mips, ram, bandwidth, ratePerMips);
        this.hardwareProfile = hardwareProfile;
        this.parallelScheduler = new ParallelCloudletScheduler(hardwareProfile.getParallelismDegree());
    }

    /**
     * Gets the hardware profile of this device.
     *
     * @return the HardwareProfile
     */
    public HardwareProfile getHardwareProfile() {
        return hardwareProfile;
    }

    /**
     * Sets a new hardware profile for this device.
     *
     * @param hardwareProfile the new HardwareProfile
     */
    public void setHardwareProfile(HardwareProfile hardwareProfile) {
        this.hardwareProfile = hardwareProfile;
        this.parallelScheduler = new ParallelCloudletScheduler(hardwareProfile.getParallelismDegree());
    }

    /**
     * Gets the effective MIPS considering parallelism acceleration.
     * FPGA, GPU, and TPU architectures multiply MIPS by parallelism degree.
     * CPU architecture uses baseline MIPS.
     *
     * @return the effective MIPS capacity
     */
    public long getEffectiveMips() {
        ComputeArchitecture arch = hardwareProfile.getArchitecture();
        long baseMips = getHost().getTotalMips();

        switch (arch) {
            case FPGA:
            case GPU:
            case TPU:
                return baseMips * hardwareProfile.getParallelismDegree();
            case CPU:
            default:
                return baseMips;
        }
    }

    /**
     * Gets the effective execution time considering parallelism.
     * For accelerated architectures, execution time is divided by parallelism degree.
     *
     * @param baseExecutionTime the execution time without acceleration
     * @return the effective execution time after acceleration
     */
    public double getEffectiveExecutionTime(double baseExecutionTime) {
        ComputeArchitecture arch = hardwareProfile.getArchitecture();

        switch (arch) {
            case FPGA:
            case GPU:
            case TPU:
                return baseExecutionTime / hardwareProfile.getParallelismDegree();
            case CPU:
            default:
                return baseExecutionTime;
        }
    }

    /**
     * Gets the parallel cloudlet scheduler for this device.
     *
     * @return the ParallelCloudletScheduler
     */
    public ParallelCloudletScheduler getParallelScheduler() {
        return parallelScheduler;
    }

    /**
     * Calculates power consumption based on current utilization and hardware profile.
     * Linear interpolation between idle and peak power based on load.
     *
     * @param utilization the CPU utilization ratio (0.0 to 1.0)
     * @return the power consumption in Watts
     */
    public double estimatePowerConsumption(double utilization) {
        double idle = hardwareProfile.getPowerIdleWatts();
        double peak = hardwareProfile.getPowerPeakWatts();
        double load = Math.max(0.0, Math.min(1.0, utilization));
        return idle + (peak - idle) * load;
    }

    @Override
    public String toString() {
        return String.format("HeterogeneousFogDevice(id=%d, name=%s, arch=%s, " +
                        "parallelism=%d, effectiveMips=%d)",
                getId(), getName(), hardwareProfile.getArchitecture(),
                hardwareProfile.getParallelismDegree(), getEffectiveMips());
    }
}
