# iFogSim-HetFS user guide

This guide explains how to use each module of iFogSim-HetFS in an iFogSim simulation, what each module changes in the behaviour of iFogSim, and what it does not model. It assumes familiarity with the iFogSim programming model (fog devices, sensors, actuators, application modules and edges, module placement, `AppLoop`s and `TimeKeeper`).

Throughout the guide, "the companion paper" refers to the source-code analysis of iFogSim (Zekeng Ndadji, arXiv:2604.23122), whose challenges C1 to C9 and development directions D1 to D7 this library follows.

## 1. Principles

**One device class, optional components.** Every module is a component attached to a `HetFSFogDevice` (or, for D6 and D7, to a `HetFSSensor` and a `HetFSActuator`). A component left `null` changes nothing. Modules therefore compose freely: a study of sensor energy does not have to adopt graph routing, and a study of graph routing does not have to adopt stateful modules.

**No modification of iFogSim.** The library only subclasses iFogSim classes and overrides protected methods. It is compiled against iFogSim at commit `5f68d39` (October 2025), the tree analysed in the companion paper.

**Backward compatibility is tested, not assumed.** On the companion case study and on the two case studies of the original iFogSim paper, a `HetFSFogDevice` without components produces the same latencies, energies, costs and network usage as a `FogDevice`, digit for digit (see `BackwardCompatibilityTest` and `ReferenceScenariosTest`).

**One simulation per JVM.** iFogSim and CloudSim keep global static state (entity registry, `TimeKeeper` singleton, tuple and entity counters). Run each simulation in its own JVM, as the examples and the experiment script do.

## 2. Creating devices and ending a run

`DeviceBuilder` creates devices with the host configuration used by the `createFogDevice` helpers of the iFogSim examples (one processing element, `StreamOperatorScheduler`, `FogLinearPowerModel`, `AppModuleAllocationPolicy`):

```java
HetFSFogDevice gw = DeviceBuilder.named("gateway-1")
        .mips(2800).ram(4000).bandwidth(10000, 10000)
        .uplinkLatency(4).level(1).ratePerMips(0.0)
        .power(107.339, 83.4333)
        .schedulingInterval(10)          // PowerDatacenter scheduling interval (default 10)
        .build();                         // buildVanilla() returns a plain FogDevice
```

iFogSim's `Controller` calls `System.exit(0)` when the simulation ends, so code placed after `CloudSim.startSimulation()` never runs. `HetFSController` notifies end listeners first and, with `setExitOnStop(false)`, terminates the event loop instead of the JVM:

```java
HetFSController controller = new HetFSController("controller", devices, sensors, actuators);
controller.setExitOnStop(false);
controller.addEndListener(() -> writeResults());
controller.submitApplication(app, 0, placement);
CloudSim.startSimulation();   // returns normally
```

## 3. D1: graph topologies

`PhysicalGraph` holds directed `PhysicalLink`s, each with a latency (ms), a bandwidth (in the unit iFogSim uses for `FogDevice` bandwidths) and its own FIFO queue. A tuple of `s` bytes occupies a link for `s / bandwidth` and reaches the far end `latency` later, which is iFogSim's own transmission model applied per link instead of per device interface.

```java
PhysicalGraph g = PhysicalGraph.fromTree(devices);         // same links as the tree
g.addBidirectionalLink(a.getId(), b.getId(), 20, 10_000_000); // lateral link
GraphRouter router = new GraphRouter(g);
for (FogDevice d : devices) ((HetFSFogDevice) d).setRouter(router);
```

With a router, a device no longer sends tuples "up" or "down". For every tuple it resolves the device that must receive it, then forwards it along the minimum-latency path:

* a tuple for an actuator type goes to the nearest device that has an actuator of that type attached;
* a tuple whose module-copy map pins its destination module goes to the device running that instance;
* otherwise the tuple goes to the nearest reachable device hosting its destination module.

Routes are shortest-path trees computed with Dijkstra's algorithm (binary heap, ties broken towards the smaller device id) and cached per source until the graph changes.

**What changes compared with iFogSim.** iFogSim broadcasts a DOWN tuple to every child, and every device of the subtree counts it in the network usage. With a router the tuple travels only towards its destination, so the network usage reported by `NetworkUsageMonitor` is lower for applications with DOWN edges. When several instances of a module exist, one instance receives each tuple; use one tuple type per target to address several instances.

**Placement still uses the tree.** iFogSim's placement policies walk `parentId` links. Keep a tree (for example a spanning tree of the graph) in the `parentId`s for placement; the router uses the graph for delivery.

## 4. D2: accelerators and input-dependent workloads

An `AccelerationProfile` gives the cost of one module on an accelerator with Amdahl's law and a per-invocation overhead:

`L' = L ((1 - f) + f / p) + o * MIPS`

where `L` is the length (MI) the tuple would have, `p` the speedup of the accelerated part, `f` the accelerated fraction and `o` the overhead in ms (data transfers, kernel launch). `AccelerationProfile.divisor(arch, p)` is the plain divisor (`f = 1`, `o = 0`).

```java
esn.setHardwareProfile(HardwareProfile.cpu()
        .accelerate("path-planner", new AccelerationProfile(ComputeArchitecture.FPGA, 10, 0.9, 0.02)));
```

A `WorkloadModel` replaces the fixed edge length of a module by a cost computed from the tuple or from the problem size. `ComplexityWorkload` implements `L = a + c * ops(V, E)` for constant, linear, Dijkstra (binary heap), quadratic and cubic operation counts; the sizes can be suppliers, so the cost follows a graph that changes during the run.

```java
esn.setWorkload("path-planner", new ComplexityWorkload(
        ComplexityWorkload.Complexity.DIJKSTRA_BINARY_HEAP, 4.40, 0.0487, roadGraph::nodes, roadGraph::edges));
```

`WorkloadCalibrator.calibrate(kernel, operations, referenceMips, warmup, samples)` times a real implementation after JIT warm-up and returns the median time and the corresponding MI. Calibrate at several sizes and regress `a` and `c` (see `experiments/analyse.py`). The conversion from measured time to MI assumes that the measuring host and the simulated processor have the same speed; state it with the results.

The order of application on arrival is: workload model, then module behaviour (D4), then accelerator. The length is rewritten before the tuple is handed to iFogSim, so the accelerated module is executed and contends inside the engine.

**Time resolution.** iFogSim adds `CloudSim.getMinTimeBetweenEvents()` (0.01 ms by default) to every hop of a tuple inside a device (`sendToSelf`), and when the completion times of tuples executing together are re-estimated, the cloudlet scheduler never places the next completion closer than this value. A sub-10 µs kernel (a warmed-up Dijkstra on a 25-node graph takes about 2 to 3 µs) is therefore measured with an error of the same order as its duration. Initialise CloudSim with a finer resolution when such durations matter:

```java
CloudSim.init(1, Calendar.getInstance(), false, 0.000001);
```

## 5. D3: exact completion events and execution monitor

iFogSim devices inherit from CloudSim's `PowerDatacenter` a processing update that, whenever a tuple is submitted to a device on which another tuple is still executing, cancels all pending `VM_DATACENTER_EVENT`s, including the finish event of the running tuple, and schedules a single new event `schedulingInterval` later. The running tuple's completion is then detected up to one scheduling interval late (10 ms in most iFogSim examples), and this delay is reported as latency.

```java
device.setExactCompletionEvents(true);
```

schedules, after every processing update and every submission, an event at the earliest estimated finish time of the tuples executing on the device. Without overlapping executions the option changes nothing. A smaller `schedulingInterval` reduces the artefact too, at the cost of many more events.

`ExecutionMonitor` records, for every module instance, the sojourn of each tuple (hand-over to detected completion) against its nominal service time (length / MIPS):

```java
ExecutionMonitor monitor = new ExecutionMonitor();
for (FogDevice d : devices) ((HetFSFogDevice) d).setExecutionMonitor(monitor);
...
ModuleExecutionStats s = monitor.get("esn", "esn-dispatcher");
s.getMeanExcessMs(); s.getMaxExcessMs(); s.getMaxInProgress();
```

With exact completion events, N tuples submitted together to a time-shared module finish after N nominal service times, so the excess is the processor-sharing delay; without them it also contains the detection delay.

## 6. D4: stateful modules

A `ModuleBehaviour` gives a module instance a lifecycle and a `ModuleState` (a key-value store of `Serializable` values):

| Hook | When | Typical use |
|---|---|---|
| `onInit(ctx)` | the module is launched on the device | initial state |
| `onTupleArrival(tuple, proposedLength, ctx)` | before execution; returns the length (MI) to execute | cache lookup, incremental work |
| `onTupleCompleted(tuple, outputs, ctx)` | after execution, with the output tuples | update the state |
| `onRestore(ctx, fromCheckpoint)` | the device recovers from a failure (D5) | rebuild derived data |

`ctx.bypassAcceleration()` in `onTupleArrival` executes the returned length on the processor rather than on the module's accelerator.

`LruCacheBehaviour` is the reference behaviour: keys are inserted when the computation completes, the least recently used entry is evicted at capacity, entries can expire, and hits cost a fixed length. Its hit, miss and eviction counters belong to the behaviour object (one per module instance) and survive failures.

```java
esn.setBehaviour("path-planner", new LruCacheBehaviour(t -> zoneOf(t), 5, 8, Double.POSITIVE_INFINITY));
esn.setCheckpointInterval(100);   // snapshot every 100 ms of simulated time
```

Checkpoints are deep copies obtained by serialisation; `getCheckpointBytes()` reports their cumulative size. They are kept in memory.

## 7. D5: run-time topology events

```java
TopologyEventScheduler faults = new TopologyEventScheduler("faults", graph);   // graph may be null
FaultInjector.on(faults)
        .failDevice(esn, 500).recoverDevice(esn, 800)
        .failLink(cadZ1, esn, 300, 900)                 // both directions, down in [300, 900)
        .setLinkLatency(cadZ1, esn, 250, 1200)
        .pulse(cadZ2, 0, 400, 50, 3);                   // 3 outages of 50 ms every 400 ms
```

The scheduler is a CloudSim entity: create it after `CloudSim.init` and before `startSimulation`. A failed device drops the tuples that reach it, discards the outputs of the tuples it was executing and the tuples queued on its links, loses the state of its stateful modules and is avoided by graph routes. On recovery, stateful modules restart from their last checkpoint (checkpoints are not taken while the device is down). Link events need a graph. Tuples already in flight on a link that fails are delivered.

## 8. D6: lineage tracing and statistics

`LatencyTracer` follows every tuple emitted by a `HetFSSensor`: each derived tuple remembers its parent, and for every module the tracer stores the time the tuple was handed to it and the time its completion was detected. After the run, any segment of the path of a delivered tuple can be measured:

```java
LatencyTracer tracer = new LatencyTracer();
// attach it to every HetFSFogDevice, HetFSSensor and HetFSActuator
RunningStatistics e2e = tracer.endToEnd("sas-z1", "itgs-c1");
RunningStatistics esnStage = tracer.segment("sas-z1", "itgs-c1",
        LatencyTracer.Point.arrival("esn-classifier"),
        LatencyTracer.Point.completion("esn-dispatcher"), 0);
```

This removes two limits of `TimeKeeper` (challenge C9): an `AppLoop` is credited when its tuple arrives at its last module, before that module's queueing and execution, and a loop crossing the start module of another loop loses its timing id there. With TimeKeeper's own boundaries the tracer gives TimeKeeper's figures.

`RunningStatistics` keeps the mean and variance with Welford's algorithm; `getConfidenceHalfWidth95()` uses the Student t quantile for `n - 1` degrees of freedom (2.776 for five replications, not 1.96).

`HetFSSensor` also fixes the sensor routing problem of challenge C8: `Sensor#transmit()` uses the last application edge whose source is the sensor's tuple type, so sensors sharing a tuple type all feed one module. A `HetFSSensor` uses the edge towards its `destModuleName` when one is set and fails explicitly when the choice is ambiguous.

## 9. D7: sensor batteries

```java
HetFSSensor sas = new HetFSSensor("sas-z1", "INCIDENT", userId, appId, new DeterministicDistribution(1000));
sas.setBattery(new BatteryModel(BatteryModel.joules(225, 3.0), 0.05, 0.0, 0.0001));
...
for (SensorEnergyReport.Entry e : SensorEnergyReport.collect(sensors, CloudSim.clock())) System.out.println(e);
```

Each transmission costs `txFixedJ + txPerByteJ * bytes`; between transmissions the sensor draws `idlePowerW`. The sensor stops emitting when a transmission cannot be supplied. The model is linear: rate-capacity effects, recovery and temperature are not represented.

## 10. Validation of iFogSim configurations

```java
List<Finding> warnings = ApplicationValidator.validateOrThrow(app, sensors, placement, mapping);
```

| Code | Severity | Problem |
|---|---|---|
| `SHARED_SENSOR_TUPLE_TYPE` | error | several edges leave a sensor tuple type for different modules; plain sensors all feed the last one |
| `UNMAPPED_MODULE` | error | with `ModulePlacementMapping`, a module mapped to no device is never instantiated and its tuples are forwarded, not executed |
| `EDGEWARDS_DOWNWARD_FANOUT` | warning | `ModulePlacementEdgewards` fails with a `NullPointerException` on DOWN fan-outs |
| `LOOP_SHARES_START_MODULE` | warning | a loop crosses the start module of another loop and is never credited |
| `UNNAMED_SENSOR` | warning | the long `Sensor` constructors leave the sensor name `null`; loops starting at the sensor are not tracked |

## 11. Limits of the current version

* `MicroserviceFogDevice` and the iFogSim2 controllers (mobility, clustering, microservices) are not extended; HetFS components apply to `FogDevice`-based simulations.
* The router selects one destination instance per tuple; iFogSim's downward broadcast to all instances of a subtree is not reproduced.
* There is no parallel event queue: CloudSim processes events one at a time, which is correct for discrete-event semantics and is not a modelling limitation in simulated time.
* The accelerator model is first order (Amdahl plus a constant overhead); memory-bandwidth saturation is not modelled.
* The battery model is linear.
