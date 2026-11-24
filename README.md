# iFogSim-HetFS

iFogSim-HetFS is an extension library for [iFogSim](https://github.com/Cloudslab/iFogSim). It removes, without modifying iFogSim, the modelling limitations documented in the source-code analysis of iFogSim by Zekeng Ndadji (arXiv:2604.23122): tree-only topologies, hardware expressed only as MIPS, fixed module costs, stateless modules, static topologies, latency instrumentation bounded by `AppLoop`s, no sensor energy, and several configurations that iFogSim accepts but executes wrongly.

The library is organised in seven modules (D1 to D7, numbered after the development directions of that analysis) and a validator:

| Module | Package | What it adds | Limitation addressed |
|---|---|---|---|
| D1 | `org.fog.hetfs.topology` | Directed graph of links (latency, bandwidth, FIFO queue each), Dijkstra routing with route caching, nearest-replica resolution | C1 (tree topology) |
| D2 | `org.fog.hetfs.hardware` | Per-module accelerators (Amdahl's law plus invocation overhead), input-dependent workloads `a + c * ops(V,E)`, calibration from measured runs | C2 (accelerators), C3 (algorithm as an MI constant) |
| D3 | `org.fog.hetfs.concurrent` | Exact completion events, per-module execution monitor | C5 (concurrency), completion-detection artefact |
| D4 | `org.fog.hetfs.stateful` | Module behaviours with state, LRU cache, periodic checkpoints | C4 (stateless modules) |
| D5 | `org.fog.hetfs.events` | Scheduled device and link failures, recoveries, latency and bandwidth changes | C7 (static topology) |
| D6 | `org.fog.hetfs.timing` | Tuple-lineage latency tracer, Welford statistics, Student-t confidence intervals | C6 (timing), C9 (AppLoop boundaries) |
| D7 | `org.fog.hetfs.energy` | Sensor battery model and energy report | O6 (sensor energy) |
| | `org.fog.hetfs.validation` | Static checks for silent configuration failures | C8 (placement and sensor routing) |

All modules are attached to one device class, `HetFSFogDevice`, as optional components. A `HetFSFogDevice` without any component behaves exactly like an iFogSim `FogDevice`; the test suite checks this against published iFogSim results.

## Requirements

* JDK 21 (iFogSim at commit `5f68d39` bundles CloudSim 7, which needs Java 21).
* iFogSim built from the official repository at commit `5f68d3947e450d8d2b4af42670be819206be68c9`. The script below does it.
* Maven 3.6+ is optional: `build.sh` builds and tests the project with `javac` alone.
* Python 3.9+ with the packages of `experiments/requirements.txt`, to reproduce the evaluation.

## Project layout

```
pom.xml                  parent POM (dependency and plugin versions)
hetfs-core/              the library, artifact ifogsim-hetfs
  src/main/java          org.fog.hetfs.{core,topology,hardware,concurrent,stateful,events,timing,energy,validation}
  src/test/java          unit tests (*Test)
hetfs-examples/          example programs, artifact ifogsim-hetfs-examples
  src/main/java          org.fog.hetfs.examples.{emergency,reference,support}
  src/test/java          integration tests (*IT), each simulation in its own JVM
experiments/             evaluation scripts, raw results (CSV) and LaTeX tables
docs/USER_GUIDE.md       user guide
scripts/                 installation of iFogSim
build.sh                 Maven-free build
```

Applications depend on `hetfs-core` only. The examples and the integration tests live in a separate module so that the library jar contains nothing else.

## Building

```bash
git clone https://github.com/MegaMaxim10/iFogSim-HetFS.git
cd iFogSim-HetFS
```

With Maven:

```bash
scripts/install-ifogsim.sh     # clones and builds iFogSim 5f68d39, installs it in ~/.m2 and lib/
mvn verify                     # unit tests (surefire) and integration tests (failsafe)
mvn -pl hetfs-core package     # hetfs-core/target/ifogsim-hetfs-2.0.0-SNAPSHOT.jar
```

Without Maven (`lib/` must also contain `junit-4.13.2.jar` and `hamcrest-core-1.3.jar` for the tests):

```bash
scripts/install-ifogsim.sh
./build.sh test                # unit tests
./build.sh verify              # unit and integration tests
./build.sh jar                 # hetfs-core/target/ifogsim-hetfs.jar
./build.sh run <main class> [options...]
```

## A first example

```java
// devices: same host configuration as the iFogSim examples
HetFSFogDevice esn = DeviceBuilder.named("esn").mips(10000).ram(8192)
        .bandwidth(10_000_000, 10_000_000).level(1).build();
HetFSFogDevice cadZ1 = DeviceBuilder.named("cad-z1").mips(2000).ram(1024)
        .bandwidth(10_000_000, 10_000_000).uplinkLatency(100).level(2).build();
HetFSFogDevice cadZ2 = DeviceBuilder.named("cad-z2").mips(2000).ram(1024)
        .bandwidth(10_000_000, 10_000_000).uplinkLatency(100).level(2).build();
cadZ1.setParentId(esn.getId());   // the tree is still used by iFogSim's placement
cadZ2.setParentId(esn.getId());
List<FogDevice> devices = List.of(esn, cadZ1, cadZ2);   // plus sensors, actuators, application

// D1: route over a graph instead of the tree
PhysicalGraph graph = PhysicalGraph.fromTree(devices);
graph.addBidirectionalLink(cadZ1.getId(), cadZ2.getId(), 20, 10_000_000);  // lateral link
GraphRouter router = new GraphRouter(graph);
for (FogDevice d : devices) ((HetFSFogDevice) d).setRouter(router);

// D3: completion events at their exact time
esn.setExactCompletionEvents(true);

// D2: a Dijkstra kernel whose cost follows the network size, on an FPGA (x10)
esn.setWorkload("esn-router", new ComplexityWorkload(
        ComplexityWorkload.Complexity.DIJKSTRA_BINARY_HEAP, 4.40, 0.0487, 25, 42));
esn.setHardwareProfile(HardwareProfile.cpu().accelerate("esn-router",
        new AccelerationProfile(ComputeArchitecture.FPGA, 10, 1.0, 0.0)));

// D6: lineage tracing
LatencyTracer tracer = new LatencyTracer();
esn.setTracer(tracer);          // and every other device, sensor and actuator

// end of run without System.exit
HetFSController controller = new HetFSController("controller", devices, sensors, actuators);
controller.setExitOnStop(false);
controller.addEndListener(() ->
        System.out.println(tracer.endToEnd("sas-z1", "itgs-c1")));
```

The user guide (`docs/USER_GUIDE.md`) describes every module, its options and its limits.

## Examples

* `org.fog.hetfs.examples.emergency.EmergencyScenario`: the smart emergency response case study of the companion paper, with one command-line option per module. With `--devices vanilla` it reproduces the companion reproducibility package (Zenodo, doi:10.5281/zenodo.22234733) bit for bit.
* `org.fog.hetfs.examples.reference.ReferenceScenarios`: the two case studies of the original iFogSim paper (Gupta et al., 2017), the EEG Tractor Beam game and the intelligent-surveillance camera network, runnable on plain or HetFS devices.
* `org.fog.hetfs.examples.emergency.CalibrateDijkstra`: measures the road-network Dijkstra and prints the data used to calibrate the D2 workload model.

Both scenarios print one CSV row (header and values) or append it to the file given by `--out`. An unknown option is rejected; the class Javadoc lists the options.

```bash
./build.sh run org.fog.hetfs.examples.emergency.EmergencyScenario \
    --zones z1,z2 --exact-completion --trace --monitor
```

## Reproducing the evaluation

```bash
./build.sh compile
python3 -m pip install -r experiments/requirements.txt
python3 experiments/run_experiments.py     # all experiments, CSV files in experiments/results/
python3 experiments/analyse.py             # summary tables in experiments/tables/
```

Each simulation runs in its own JVM, because iFogSim and CloudSim keep global static state. `ReferenceScenarios --seed` reseeds the generator behind `Math.random()` (used by iFogSim's `FractionalSelectivity`); the JVM then needs `--add-opens java.base/java.lang=ALL-UNNAMED`, which the experiment script passes.

## Contributing

See `CONTRIBUTING.md`. Changes are listed in `CHANGELOG.md`.

## Licence

Apache License 2.0 (`LICENSE`). The reference scenarios are adapted from iFogSim examples distributed under the MIT licence (Copyright (c) 2025 The CLOUDS Laboratory); `NOTICE` gives the attribution.
