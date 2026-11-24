package org.fog.hetfs.examples;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.core.FogComputingConstants;
import org.fog.core.FogDevice;
import org.fog.core.NetworkUsageMonitor;
import org.fog.entities.Actuator;
import org.fog.entities.Sensor;
import org.fog.hetfs.energy.BatteryModel;
import org.fog.hetfs.energy.EnergyAwareSensor;
import org.fog.hetfs.energy.SensorEnergyReport;
import org.fog.hetfs.events.FaultInjector;
import org.fog.hetfs.events.TopologyEventScheduler;
import org.fog.hetfs.hardware.ComputeArchitecture;
import org.fog.hetfs.hardware.HardwareProfile;
import org.fog.hetfs.hardware.HeterogeneousFogDevice;
import org.fog.hetfs.stateful.CacheModule;
import org.fog.hetfs.stateful.ModuleState;
import org.fog.hetfs.stateful.StatefulController;
import org.fog.hetfs.timing.NanoTimeKeeper;
import org.fog.hetfs.timing.TimingReport;
import org.fog.hetfs.topology.GraphAwareController;
import org.fog.hetfs.topology.GraphFogDevice;
import org.fog.hetfs.topology.PhysicalGraph;
import org.fog.hetfs.topology.PhysicalLink;
import org.fog.policies.AppModuleAllocationPolicy;
import org.fog.provisioner.RamProvisioner;
import org.fog.provisioner.StorageProvisioner;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogUtils;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;

import java.util.*;

/**
 * Smart Emergency Response System example demonstrating iFogSim-HetFS capabilities.
 * Implements a 25-node road network topology with heterogeneous hardware (FPGA-based ESN),
 * stateful caching modules, fault injection, and comprehensive timing/energy reporting.
 */
public class SmartEmergencyHetFS {
    private static final int SIMULATION_LENGTH = 120_000; // 2 minutes in milliseconds

    public static void main(String[] args) {
        try {
            // Initialize CloudSim
            CloudSim.init(1, Calendar.getInstance(), false);

            // Create physical topology graph for 25-node road network
            PhysicalGraph graph = buildRoadNetworkTopology();

            // Create fog devices with heterogeneous hardware
            List<GraphFogDevice> graphDevices = buildFogDevices(graph);

            // Setup stateful controller with topology and modules
            StatefulController statefulController = new StatefulController("StatefulController", graph);

            // Register graph devices with controller
            for (GraphFogDevice device : graphDevices) {
                statefulController.registerDevice(device);
            }

            // Create and register stateful cache modules
            List<CacheModule> cacheModules = buildCacheModules();
            statefulController.registerStatefulModules(cacheModules);

            // Setup fault injection (fail one device mid-simulation)
            TopologyEventScheduler eventScheduler = new TopologyEventScheduler(
                    "TopologyEventScheduler", graph, graphDevices);

            FaultInjector faultInjector = new FaultInjector()
                    .failDeviceAt(10, SIMULATION_LENGTH / 2)  // Fail device 10 at halfway point
                    .recoverDeviceAt(10, SIMULATION_LENGTH * 3 / 4); // Recover at 75% mark

            for (org.fog.hetfs.events.TopologyEvent event : faultInjector.getScheduledEvents()) {
                eventScheduler.scheduleEvent(event);
            }

            CloudSim.addEntity(eventScheduler);

            // Create sensors with energy models
            List<EnergyAwareSensor> energySensors = buildEnergySensors();

            // Initialize timing keeper for performance tracking
            NanoTimeKeeper timeKeeper = NanoTimeKeeper.getInstance();

            // Start simulation
            System.out.println("Starting Smart Emergency Extended simulation...");
            System.out.println("Topology: " + graph.toString());
            System.out.println("Devices: " + graphDevices.size());
            System.out.println("Cache Modules: " + cacheModules.size());
            System.out.println("Energy Sensors: " + energySensors.size());
            System.out.println();

            statefulController.startEntity();
            CloudSim.startSimulation();

            // Print timing report
            System.out.println(TimingReport.generateReport(timeKeeper));

            // Print energy report
            SensorEnergyReport energyReport = new SensorEnergyReport();
            for (EnergyAwareSensor sensor : energySensors) {
                energyReport.addSensor(sensor);
            }
            System.out.println(energyReport.generateReport());

            // Print topology event log
            System.out.println("=== TOPOLOGY EVENTS LOG ===");
            for (String logEntry : eventScheduler.getEventLog()) {
                System.out.println(logEntry);
            }
            System.out.println();

            // Print cache module statistics
            System.out.println("=== CACHE MODULE STATISTICS ===");
            for (CacheModule module : cacheModules) {
                System.out.println(module.toString());
            }
            System.out.println();

            CloudSim.stopSimulation();
            System.out.println("Simulation completed successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Builds a 25-node road network topology using a graph structure.
     *
     * @return the PhysicalGraph representing the road network
     */
    private static PhysicalGraph buildRoadNetworkTopology() {
        PhysicalGraph graph = new PhysicalGraph();

        // Create 25 devices (nodes 0-24) in a grid-like arrangement
        for (int i = 0; i < 25; i++) {
            graph.addDevice(i);
        }

        // Add links representing road network connectivity
        // This creates a 5x5 grid with horizontal and vertical connections
        int[] neighbors = new int[25];
        for (int i = 0; i < 25; i++) {
            neighbors[i] = 0;
        }

        // Horizontal links
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 4; col++) {
                int srcId = row * 5 + col;
                int dstId = row * 5 + col + 1;
                graph.addLink(srcId, dstId, 10.0, 1000.0); // 10ms latency, 1Gbps bandwidth
                graph.addLink(dstId, srcId, 10.0, 1000.0); // Bidirectional
            }
        }

        // Vertical links
        for (int col = 0; col < 5; col++) {
            for (int row = 0; row < 4; row++) {
                int srcId = row * 5 + col;
                int dstId = (row + 1) * 5 + col;
                graph.addLink(srcId, dstId, 15.0, 800.0); // 15ms latency, 800Mbps bandwidth
                graph.addLink(dstId, srcId, 15.0, 800.0); // Bidirectional
            }
        }

        System.out.println("Created road network topology: " + graph.getDeviceCount() +
                " devices, " + graph.getLinkCount() + " links");

        return graph;
    }

    /**
     * Builds fog devices with heterogeneous hardware profiles.
     *
     * @param graph the physical graph to use for routing
     * @return a list of configured GraphFogDevice instances
     */
    private static List<GraphFogDevice> buildFogDevices(PhysicalGraph graph) {
        List<GraphFogDevice> devices = new ArrayList<>();

        for (int i = 0; i < 25; i++) {
            String deviceName = "Road_Segment_" + i;

            // Most devices are CPU-based, but node 10 (ESN) is FPGA-based
            if (i == 10) {
                HardwareProfile fpgaProfile = new HardwareProfile(
                        ComputeArchitecture.FPGA,
                        8,      // 8-way parallelism
                        2.5,    // 2.5 GHz
                        15.0,   // 15W idle
                        45.0    // 45W peak
                );

                HeterogeneousFogDevice device = new HeterogeneousFogDevice(
                        deviceName, 1000, 2048, 10000, 0.0, fpgaProfile);

                device.setLatency(1.0);
                device.setPhysicalGraph(graph);
                device.buildRoutingTable(graph);
                devices.add((GraphFogDevice) device);

                System.out.println("Created FPGA-accelerated ESN: " + deviceName);
            } else {
                GraphFogDevice device = new GraphFogDevice(
                        deviceName, 500, 1024, 5000, 0.0);

                device.setLatency(1.0);
                device.setPhysicalGraph(graph);
                device.buildRoutingTable(graph);
                devices.add(device);

                if (i % 5 == 0) {
                    System.out.println("Created CPU device: " + deviceName);
                }
            }
        }

        return devices;
    }

    /**
     * Builds stateful cache modules for the emergency system.
     *
     * @return a list of configured CacheModule instances
     */
    private static List<CacheModule> buildCacheModules() {
        List<CacheModule> modules = new ArrayList<>();

        // Create cache modules for different zones
        for (int i = 0; i < 5; i++) {
            CacheModule cache = new CacheModule(
                    "CacheModule_Zone_" + i,
                    10,        // Cache 10 zones
                    100,       // 100 MI for cache hit
                    500        // 500 MI for cache miss
            );

            modules.add(cache);

            if (i == 0) {
                System.out.println("Created " + modules.size() + " cache modules");
            }
        }

        return modules;
    }

    /**
     * Builds energy-aware sensors for the road network.
     *
     * @return a list of configured EnergyAwareSensor instances
     */
    private static List<EnergyAwareSensor> buildEnergySensors() {
        List<EnergyAwareSensor> sensors = new ArrayList<>();

        for (int i = 0; i < 25; i++) {
            BatteryModel battery = new BatteryModel(
                    100.0,      // 100 MJ capacity
                    0.5,        // 0.5 MJ per transmission
                    2.0         // 2W idle power
            );

            EnergyAwareSensor sensor = new EnergyAwareSensor(
                    "Sensor_" + i,
                    1000.0,     // Transmit every 1000ms
                    1,          // App ID
                    1,          // User ID
                    i,          // Gateway is the local device
                    battery
            );

            sensors.add(sensor);
        }

        System.out.println("Created " + sensors.size() + " energy-aware sensors");

        return sensors;
    }
}
