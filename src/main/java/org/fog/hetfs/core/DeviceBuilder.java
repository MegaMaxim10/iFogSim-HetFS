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
public class DeviceBuilder {

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

    public static DeviceBuilder named(String name) {
        return new DeviceBuilder(name);
    }

    public DeviceBuilder mips(long mips) {
        this.mips = mips;
        return this;
    }

    public DeviceBuilder ram(int ramMb) {
        this.ram = ramMb;
        return this;
    }

    public DeviceBuilder bandwidth(double uplink, double downlink) {
        this.uplinkBandwidth = uplink;
        this.downlinkBandwidth = downlink;
        return this;
    }

    public DeviceBuilder uplinkLatency(double ms) {
        this.uplinkLatency = ms;
        return this;
    }

    public DeviceBuilder level(int level) {
        this.level = level;
        return this;
    }

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

    public DeviceBuilder schedulingInterval(double interval) {
        this.schedulingInterval = interval;
        return this;
    }

    public HetFSFogDevice build() {
        try {
            HetFSFogDevice d = new HetFSFogDevice(name, characteristics(), allocation(), storage(),
                    schedulingInterval, uplinkBandwidth, downlinkBandwidth, uplinkLatency, ratePerMips);
            finish(d);
            return d;
        } catch (Exception e) {
            throw new IllegalStateException("cannot create device " + name, e);
        }
    }

    /** Builds a plain iFogSim {@link FogDevice} with the same configuration. */
    public FogDevice buildVanilla() {
        try {
            FogDevice d = new FogDevice(name, characteristics(), allocation(), storage(),
                    schedulingInterval, uplinkBandwidth, downlinkBandwidth, uplinkLatency, ratePerMips);
            finish(d);
            return d;
        } catch (Exception e) {
            throw new IllegalStateException("cannot create device " + name, e);
        }
    }

    private void finish(FogDevice d) {
        d.setLevel(level);
    }

    private PowerHost host;

    private FogDeviceCharacteristics characteristics() {
        List<Pe> peList = new ArrayList<Pe>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));
        host = new PowerHost(FogUtils.generateEntityId(), new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(10000), 1000000L, peList,
                new StreamOperatorScheduler(peList), new FogLinearPowerModel(busyPower, idlePower));
        return new FogDeviceCharacteristics("x86", "Linux", "Xen", host, 10.0, 3.0, 0.05, 0.001, 0.0);
    }

    private AppModuleAllocationPolicy allocation() {
        List<Host> hosts = new ArrayList<Host>();
        hosts.add(host);
        return new AppModuleAllocationPolicy(hosts);
    }

    private static List<Storage> storage() {
        return new LinkedList<Storage>();
    }
}
