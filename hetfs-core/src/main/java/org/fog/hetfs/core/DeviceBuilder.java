package org.fog.hetfs.core;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;

/**
 * Fluent factory for fog devices.
 *
 * <p>The host configuration (one processing element, {@code StreamOperatorScheduler},
 * {@code FogLinearPowerModel}, {@code AppModuleAllocationPolicy}) is the one used by
 * the {@code createFogDevice} helpers of the examples distributed with iFogSim, so
 * that {@link #buildVanilla()} produces exactly the device those examples produce and
 * {@link #build()} produces its {@link HetFSFogDevice} counterpart.
 */
public final class DeviceBuilder {

    // Host and characteristics of the createFogDevice helpers of the iFogSim examples.
    private static final int HOST_BANDWIDTH = 10000;
    private static final long HOST_STORAGE = 1000000L;
    private static final String ARCH = "x86";
    private static final String OS = "Linux";
    private static final String VMM = "Xen";
    private static final double TIME_ZONE = 10.0;
    private static final double COST = 3.0;
    private static final double COST_PER_MEMORY = 0.05;
    private static final double COST_PER_STORAGE = 0.001;
    private static final double COST_PER_BANDWIDTH = 0.0;

    private final String name;
    private long mips = 1000;
    private int ram = 1024;
    private double uplinkBandwidth = 10000;
    private double downlinkBandwidth = 10000;
    private double uplinkLatency;
    private int level;
    private double ratePerMips;
    private double busyPower = 107.339;
    private double idlePower = 83.4333;
    private double schedulingInterval = 10;

    private DeviceBuilder(String name) {
        this.name = name;
    }

    /** Starts the description of a device; the defaults are those of a 1 000-MIPS edge node. */
    public static DeviceBuilder named(String name) {
        return new DeviceBuilder(name);
    }

    /** Processing capacity of the device, in MIPS. */
    public DeviceBuilder mips(long mips) {
        this.mips = mips;
        return this;
    }

    /** Memory of the device, in MB. */
    public DeviceBuilder ram(int ramMb) {
        this.ram = ramMb;
        return this;
    }

    /** Uplink and downlink bandwidths, in the unit iFogSim uses for tuple sizes per time unit. */
    public DeviceBuilder bandwidth(double uplink, double downlink) {
        this.uplinkBandwidth = uplink;
        this.downlinkBandwidth = downlink;
        return this;
    }

    /** Latency of the link to the parent device, in ms. */
    public DeviceBuilder uplinkLatency(double ms) {
        this.uplinkLatency = ms;
        return this;
    }

    /** Level of the device in the hierarchy (0 for the cloud), used by iFogSim's placement policies. */
    public DeviceBuilder level(int level) {
        this.level = level;
        return this;
    }

    /** Cost per MIPS used, accumulated in {@code FogDevice#getTotalCost()}. */
    public DeviceBuilder ratePerMips(double rate) {
        this.ratePerMips = rate;
        return this;
    }

    /** Busy and idle power (W) of the linear power model. */
    public DeviceBuilder power(double busyWatts, double idleWatts) {
        this.busyPower = busyWatts;
        this.idlePower = idleWatts;
        return this;
    }

    /** Scheduling interval of CloudSim's {@code PowerDatacenter} (10 in the iFogSim examples). */
    public DeviceBuilder schedulingInterval(double interval) {
        this.schedulingInterval = interval;
        return this;
    }

    /** Builds a {@link HetFSFogDevice} without any component. */
    public HetFSFogDevice build() {
        PowerHost host = createHost();
        try {
            HetFSFogDevice device = new HetFSFogDevice(name, characteristics(host), allocationPolicy(host),
                    new LinkedList<Storage>(), schedulingInterval, uplinkBandwidth, downlinkBandwidth,
                    uplinkLatency, ratePerMips);
            device.setLevel(level);
            return device;
        } catch (Exception e) {
            throw new IllegalStateException("cannot create device " + name, e);
        }
    }

    /** Builds a plain iFogSim {@link FogDevice} with the same configuration (for baselines). */
    public FogDevice buildVanilla() {
        PowerHost host = createHost();
        try {
            FogDevice device = new FogDevice(name, characteristics(host), allocationPolicy(host),
                    new LinkedList<Storage>(), schedulingInterval, uplinkBandwidth, downlinkBandwidth,
                    uplinkLatency, ratePerMips);
            device.setLevel(level);
            return device;
        } catch (Exception e) {
            throw new IllegalStateException("cannot create device " + name, e);
        }
    }

    private PowerHost createHost() {
        List<Pe> peList = new ArrayList<>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));
        return new PowerHost(FogUtils.generateEntityId(), new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(HOST_BANDWIDTH), HOST_STORAGE, peList,
                new StreamOperatorScheduler(peList), new FogLinearPowerModel(busyPower, idlePower));
    }

    private static FogDeviceCharacteristics characteristics(PowerHost host) {
        return new FogDeviceCharacteristics(ARCH, OS, VMM, host, TIME_ZONE, COST, COST_PER_MEMORY,
                COST_PER_STORAGE, COST_PER_BANDWIDTH);
    }

    private static AppModuleAllocationPolicy allocationPolicy(PowerHost host) {
        List<Host> hosts = new ArrayList<>();
        hosts.add(host);
        return new AppModuleAllocationPolicy(hosts);
    }
}
