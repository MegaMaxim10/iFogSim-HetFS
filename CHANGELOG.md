# Changelog

## [2.0.0] - unreleased

Complete re-implementation. The 1.0.0 sources did not compile against iFogSim (56 errors in all seven packages) and had no tests; nothing of their code is kept, only the package layout and the D1 to D7 numbering.

### Build
- Valid `pom.xml`; iFogSim built from the official repository at commit 5f68d39 by `scripts/install-ifogsim.sh`; Maven-free `build.sh`. Java 21 is required.

### Core
- `HetFSFogDevice`: single device class with optional components; identical to `FogDevice` when no component is set (tested against published results).
- `DeviceBuilder`, `HetFSController` (end listeners, no `System.exit`), `HetFSEvents`.
- `HetFSSensor` (deterministic edge resolution, tracing, battery), `HetFSActuator` (tracing).

### Modules
- D1 `topology`: `PhysicalGraph`, `PhysicalLink`, `GraphRouter`, `RoutableDevice`.
- D2 `hardware`: `AccelerationProfile`, `HardwareProfile`, `ComputeArchitecture`, `WorkloadModel`, `ComplexityWorkload`, `WorkloadCalibrator`.
- D3 `concurrent`: exact completion events, `ExecutionMonitor`, `ModuleExecutionStats`.
- D4 `stateful`: `ModuleBehaviour`, `ModuleState`, `ModuleContext`, `LruCacheBehaviour`, periodic checkpoints.
- D5 `events`: `TopologyEventScheduler`, `TopologyEvent`, `TopologyEventType`, `FaultInjector`.
- D6 `timing`: `LatencyTracer`, `RunningStatistics`, `StudentT`.
- D7 `energy`: `BatteryModel`, `SensorEnergyReport`.
- `validation`: `ApplicationValidator`, `Finding`.

### Examples and evaluation
- `EmergencyScenario` (companion case study), `ReferenceScenarios` (case studies of the original iFogSim paper), `CalibrateDijkstra`.
- `experiments/run_experiments.py` and `experiments/analyse.py`; raw results in `experiments/results/`.

## [1.0.0] - 2026-04-19
- Initial import (did not compile against iFogSim).
