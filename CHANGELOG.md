# Changelog

All notable changes to this project are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - Unreleased

### Changed
- Complete re-implementation. The 1.0.0 sources did not compile against iFogSim (56 errors in all seven packages) and had no tests; nothing of their code is kept, only the package layout and the D1 to D7 numbering.

### Added

**Build**

- Maven multi-module project: parent POM with dependency and plugin management, `hetfs-core` (the library, artifact `ifogsim-hetfs`) and `hetfs-examples` (example programs and integration tests).
- iFogSim built from the official repository at commit 5f68d39 by `scripts/install-ifogsim.sh`; Maven-free `build.sh` with the same conventions (`compile`, `test`, `verify`, `jar`, `run`, `clean`). Java 21 is required.
- Unit tests (`*Test`, surefire) and integration tests (`*IT`, failsafe), one JVM per test class.
- `.editorconfig`, `NOTICE` (attribution of the iFogSim examples), `experiments/requirements.txt`, a `package-info.java` for every package.

**Core**

- `HetFSFogDevice`: single device class with optional components; identical to `FogDevice` when no component is set (tested against published results). Discarded tuples are counted per `DropReason`.
- `DeviceBuilder`, `HetFSController` (end listeners, no `System.exit`), `HetFSEvents`.
- `HetFSSensor` (deterministic edge resolution, tracing, battery), `HetFSActuator` (tracing).

**Modules**

- D1 `topology`: `PhysicalGraph`, `PhysicalLink`, `GraphRouter`, `RoutableDevice`.
- D2 `hardware`: `AccelerationProfile`, `HardwareProfile`, `ComputeArchitecture`, `WorkloadModel`, `ComplexityWorkload`, `WorkloadCalibrator`.
- D3 `concurrent`: exact completion events, `ExecutionMonitor`, `ModuleExecutionStats`.
- D4 `stateful`: `ModuleBehaviour`, `ModuleState`, `ModuleContext`, `LruCacheBehaviour`, periodic checkpoints.
- D5 `events`: `TopologyEventScheduler`, `TopologyEvent`, `TopologyEventType`, `FaultInjector`.
- D6 `timing`: `LatencyTracer`, `RunningStatistics`, `StudentT`.
- D7 `energy`: `BatteryModel`, `SensorEnergyReport`.
- `validation`: `ApplicationValidator`, `Finding`.

**Examples and evaluation**

- `EmergencyScenario` (companion case study), `ReferenceScenarios` (case studies of the original iFogSim paper), `CalibrateDijkstra`.
- `Options` (command-line options; unknown options are rejected) and `ResultRow` (CSV result rows) shared by the scenarios.
- `experiments/run_experiments.py` and `experiments/analyse.py`; raw results in `experiments/results/`.

## [1.0.0] - 2026-04-19

### Added
- Initial import (did not compile against iFogSim).
