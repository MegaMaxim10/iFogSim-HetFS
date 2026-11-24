# Changelog

All notable changes to iFogSim-HetFS will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-04-19

### Added

#### Module D1: Arbitrary Graph Topology
- **PhysicalLink**: Directed network links with configurable latency, bandwidth, and packet loss
- **PhysicalGraph**: Adjacency-list graph representation with Dijkstra shortest-path routing
- **GraphFogDevice**: FogDevice extension with graph-based routing tables and dynamic path computation
- **GraphAwareController**: SimEntity that initializes routing tables for all graph devices at simulation start

#### Module D2: Heterogeneous Hardware
- **ComputeArchitecture**: Enum for CPU, FPGA, GPU, and TPU architectures
- **HardwareProfile**: Hardware specification including parallelism, clock speed, and power model
- **HeterogeneousFogDevice**: GraphFogDevice with architecture-specific execution acceleration
- **ParallelCloudletScheduler**: CloudletScheduler supporting concurrent execution up to parallelism degree

#### Module D3: Concurrent Stream Processing
- **TupleStream**: Independent stream abstraction with per-stream queue and statistics
- **StreamDispatcher**: SimEntity managing multiple concurrent streams and CloudSim event interleaving
- **ConcurrencyStats**: Statistics bean aggregating queue depth, throughput, and contention metrics

#### Module D4: Stateful Modules
- **ModuleState**: Serializable key-value store with checkpoint/restore semantics
- **StatefulAppModule**: AppModule base class with lifecycle hooks (onInit, onTupleArrival, onCheckpoint, onRestore)
- **CacheModule**: Reference LRU cache implementation with hit/miss tracking and configurable MI costs
- **StatefulController**: GraphAwareController extension managing module initialization and checkpointing

#### Module D5: Runtime Topology Events
- **TopologyEventType**: Enum for device failures, recoveries, and link changes
- **TopologyEvent**: Event representation with scheduling and metadata
- **TopologyEventScheduler**: SimEntity processing topology events and rebuilding affected routing tables
- **FaultInjector**: Builder for convenient scheduling of device failures, link degradations, and periodic pulses

#### Module D6: High-Resolution Timing
- **NanoTimeKeeper**: TimeKeeper singleton using nanosecond-precision wall-clock measurements
- **TimingStats**: Welford online algorithm for computing mean, variance, min, max without storing measurements
- **TimingReport**: Pretty-printed report generator with confidence intervals and detailed statistics
- **MicrobenchmarkRunner**: Utility for running calibrated microbenchmarks with warmup phase

#### Module D7: Sensor Energy Modeling
- **BatteryModel**: Battery model with transmission cost and idle drain
- **EnergyAwareSensor**: Sensor extension with battery tracking and transmission failure on exhaustion
- **SensorEnergyReport**: Report generator collecting and displaying energy statistics across all sensors

### Examples

- **SmartEmergencyHetFS**: 25-node road network demonstrating all modules with fault injection
- **ArbitraryTopologyExample**: Graph topology construction and routing table verification
- **FPGAAccelerationExample**: Heterogeneous hardware comparison (CPU, FPGA, GPU, TPU)
- **FaultInjectionExample**: Fault scheduling patterns and event timeline generation

### Build & Deployment
- Maven POM with Java 8 compatibility
- Fat JAR creation via maven-assembly-plugin
- iFogSim 2.0.0 as provided dependency
- JUnit 4 testing framework
- Comprehensive Javadoc generation

### Documentation
- Detailed README with quick-start examples for each module
- API Javadoc on all public classes and methods
- Contributing guidelines (CONTRIBUTING.md)
- Changelog tracking all releases (CHANGELOG.md)
- Apache License 2.0

## Future Releases

### [1.1.0] - Planned
- ML-based workload prediction module
- Byzantine fault tolerance
- Distributed state management across devices
- Extended energy models (photovoltaic, hybrid)

### [2.0.0] - Planned
- GPU memory model refinement
- Network protocol simulation
- Inter-device migration support

## Known Issues

- None currently reported

## Notes for Maintainers

- Keep CHANGELOG.md updated with each release
- Tag releases as vX.Y.Z
- Update pom.xml version before release
- Run full test suite before deployment
