# iFogSim-HetFS: Advanced Extensions for Fog Computing Simulation

iFogSim-HetFS is a comprehensive Java library that extends iFogSim with advanced capabilities for simulating complex fog computing systems. It provides seven major modules for topology modeling, hardware heterogeneity, concurrent processing, stateful computing, runtime events, high-precision timing, and sensor energy management.

## Features

### D1: Arbitrary Graph Topology (`org.fog.hetfs.topology`)
- **PhysicalLink**: Models directed network links with configurable latency, bandwidth, and loss rates
- **PhysicalGraph**: Adjacency-list graph supporting Dijkstra shortest-path routing
- **GraphFogDevice**: Extends FogDevice with graph-based routing and dynamic routing tables
- **GraphAwareController**: Initializes and manages graph-based topologies at simulation start

### D2: Heterogeneous Hardware (`org.fog.hetfs.hardware`)
- **ComputeArchitecture**: Enum supporting CPU, FPGA, GPU, and TPU architectures
- **HardwareProfile**: Defines parallelism, clock speed, and power characteristics
- **HeterogeneousFogDevice**: Applies architecture-specific execution acceleration
- **ParallelCloudletScheduler**: Processes multiple cloudlets concurrently up to parallelism degree

### D3: Concurrent Stream Processing (`org.fog.hetfs.concurrent`)
- **TupleStream**: Represents independent tuple streams with per-stream statistics
- **StreamDispatcher**: Manages multiple concurrent streams in CloudSim event queue
- **ConcurrencyStats**: Aggregates queue depth, throughput, and contention metrics

### D4: Stateful Modules (`org.fog.hetfs.stateful`)
- **ModuleState**: Serializable key-value store with checkpoint/restore semantics
- **StatefulAppModule**: Base class for stateful modules with lifecycle hooks
- **CacheModule**: LRU cache implementation with hit/miss tracking and cost modeling
- **StatefulController**: Manages module initialization, checkpointing, and recovery

### D5: Runtime Topology Events (`org.fog.hetfs.events`)
- **TopologyEventType**: Enum for device failures, recoveries, and link changes
- **TopologyEvent**: Represents individual topology events with scheduling
- **TopologyEventScheduler**: CloudSim entity that processes events and rebuilds routing
- **FaultInjector**: Convenient builder for scheduling common failure patterns

### D6: High-Resolution Timing (`org.fog.hetfs.timing`)
- **NanoTimeKeeper**: Singleton extending TimeKeeper with nanosecond precision
- **TimingStats**: Welford online algorithm for mean, variance, min, max tracking
- **TimingReport**: Pretty-printed tables for loop timing statistics and confidence intervals
- **MicrobenchmarkRunner**: Utility for running calibrated microbenchmarks with warmup

### D7: Sensor Energy Modeling (`org.fog.hetfs.energy`)
- **BatteryModel**: Models sensor battery with transmission and idle drain costs
- **EnergyAwareSensor**: Extends Sensor with battery tracking and exhaustion
- **SensorEnergyReport**: Collects and reports energy statistics across all sensors

## Quick Start

### Building the Library

```bash
mvn clean package
```

This produces:
- `target/ifogsim-hetfs-1.0.0.jar` — Library JAR
- `target/ifogsim-hetfs-1.0.0-jar-with-dependencies.jar` — Fat JAR with dependencies

### Basic Example: Arbitrary Topology

```java
// Create a graph-based topology
PhysicalGraph graph = new PhysicalGraph();
graph.addDevice(0);
graph.addDevice(1);
graph.addLink(0, 1, 10.0, 1000.0);  // 10ms latency, 1Gbps bandwidth

// Create devices with routing
GraphFogDevice device0 = new GraphFogDevice("Node0", 1000, 2048, 10000, 0.0);
device0.setPhysicalGraph(graph);
device0.buildRoutingTable(graph);

// Initialize topology via controller
GraphAwareController controller = new GraphAwareController("Controller", graph);
controller.registerDevice(device0);
controller.initializeTopology();
```

### Example: FPGA Acceleration

```java
// Define FPGA hardware profile
HardwareProfile fpgaProfile = new HardwareProfile(
    ComputeArchitecture.FPGA,
    8,      // 8-way parallelism
    2.5,    // 2.5 GHz
    15.0,   // 15W idle
    45.0    // 45W peak
);

// Create FPGA-accelerated device
HeterogeneousFogDevice fpgaDevice = new HeterogeneousFogDevice(
    "FPGA_ESN", 1000, 2048, 5000, 0.0, fpgaProfile);

// Execution time is divided by parallelism degree
double effectiveTime = fpgaDevice.getEffectiveExecutionTime(10.0);  // Accelerated
```

### Example: Fault Injection

```java
FaultInjector injector = new FaultInjector()
    .failDeviceAt(5, 5000.0)          // Fail device at 5 seconds
    .recoverDeviceAt(5, 10000.0)      // Recover at 10 seconds
    .degradeLinkAt(0, 1, 3000.0, 50.0); // Degrade link latency to 50ms

TopologyEventScheduler scheduler = new TopologyEventScheduler(
    "Scheduler", graph, devices);

for (TopologyEvent event : injector.getScheduledEvents()) {
    scheduler.scheduleEvent(event);
}
```

### Example: Energy-Aware Sensors

```java
BatteryModel battery = new BatteryModel(
    100.0,      // 100 MJ capacity
    0.5,        // 0.5 MJ per transmission
    2.0         // 2W idle power
);

EnergyAwareSensor sensor = new EnergyAwareSensor(
    "Sensor0", 1000.0, 1, 1, 0, battery);

// Sensor stops transmitting when battery exhausted
if (sensor.transmit()) {
    System.out.println("Transmission successful");
} else {
    System.out.println("Battery exhausted");
}
```

### Example: Timing Measurements

```java
NanoTimeKeeper timeKeeper = NanoTimeKeeper.getInstance();

// Record timing for an application loop
long startNano = System.nanoTime();
// ... application code ...
long endNano = System.nanoTime();
timeKeeper.recordNanoTime("myLoop", endNano - startNano);

// Print report with confidence intervals
System.out.println(TimingReport.generateReport(timeKeeper));
```

## Running Examples

### Smart Emergency Response System
```bash
mvn exec:java -Dexec.mainClass="org.fog.hetfs.examples.SmartEmergencyHetFS"
```

### Arbitrary Topology
```bash
mvn exec:java -Dexec.mainClass="org.fog.hetfs.examples.ArbitraryTopologyExample"
```

### FPGA Acceleration
```bash
mvn exec:java -Dexec.mainClass="org.fog.hetfs.examples.FPGAAccelerationExample"
```

### Fault Injection
```bash
mvn exec:java -Dexec.mainClass="org.fog.hetfs.examples.FaultInjectionExample"
```

## Dependencies

- **iFogSim 2.0.0** — CloudSim-based fog computing simulator (provided)
- **CloudSim 3.0.3** — Cloud computing simulator (provided via iFogSim)
- **Java 8+** — Required runtime

## Project Structure

```
sources/
├── pom.xml
├── README.md
├── LICENSE
├── CONTRIBUTING.md
├── CHANGELOG.md
├── src/
│   ├── main/java/org/fog/hetfs/
│   │   ├── topology/
│   │   ├── hardware/
│   │   ├── concurrent/
│   │   ├── stateful/
│   │   ├── events/
│   │   ├── timing/
│   │   ├── energy/
│   │   └── examples/
│   └── test/java/org/fog/hetfs/
└── examples/
```

## Unit Tests

Run tests with:
```bash
mvn test
```

## API Documentation

Generate Javadoc with:
```bash
mvn javadoc:javadoc
```

Documentation will be available in `target/site/apidocs/`.

## Key Design Decisions

1. **Graph-Based Routing**: PhysicalGraph replaces simple parent-child hierarchies, enabling arbitrary network topologies.

2. **Online Statistics**: TimingStats and StreamDispatcher use Welford's online algorithm for memory-efficient statistics.

3. **Checkpoint/Restore**: ModuleState supports serialization for fault tolerance and recovery patterns.

4. **Hardware Abstraction**: HeterogeneousFogDevice models acceleration through parallelism multipliers rather than requiring detailed micro-architecture.

5. **Energy Modeling**: BatteryModel provides simple linear drain model suitable for high-level simulation.

## Contributing

See CONTRIBUTING.md for guidelines on submitting pull requests and reporting issues.

## License

This project is licensed under the Apache License 2.0. See LICENSE for details.

## Citation

If you use iFogSim-HetFS in your research, please cite:

```
@software{ifogsim_hetfs,
  title={iFogSim-HetFS: Advanced Fog Computing Extensions},
  author={Your Name},
  url={https://github.com/nafasi-digital/ifogsim-hetfs},
  year={2026}
}
```

## Support

For questions, issues, or feedback:
- Open an issue on GitHub
- Contact: support@nafasi-digital.com
- Documentation: See inline Javadoc comments in source code

## Roadmap

Future enhancements:
- ML-based workload prediction
- Byzantine fault tolerance
- GPU memory model refinement
- Distributed state management
- Extended energy models (photovoltaic, hybrid)
