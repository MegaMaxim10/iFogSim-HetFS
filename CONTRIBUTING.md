# Contributing to iFogSim-HetFS

## Reporting problems

Open an issue with the iFogSim commit you build against, your JDK version, a minimal simulation that shows the problem, the result you expected and the result you obtained. When the problem concerns a difference with plain iFogSim, please include the same simulation run with `FogDevice` (`DeviceBuilder#buildVanilla()`).

## Development set-up

* JDK 21.
* `scripts/install-ifogsim.sh` builds iFogSim 5f68d39 into `lib/`.
* `./build.sh test` compiles the library and runs every test class in its own JVM. Maven users can run `mvn test` after the installation script has installed the iFogSim jar in the local repository.

## Rules for changes

1. **Do not modify iFogSim.** New behaviour is added by subclassing and by overriding protected methods. If an iFogSim method has to be copied to insert a hook (as `HetFSFogDevice#checkCloudletCompletion` does), keep the copy line for line and say so in its Javadoc.
2. **A component left unset must change nothing.** Every new feature of `HetFSFogDevice`, `HetFSSensor` or `HetFSActuator` is off by default. `BackwardCompatibilityTest` and `ReferenceScenariosTest` must keep passing unchanged: they compare HetFS devices without components with plain iFogSim on published results.
3. **Test inside the engine.** Unit tests for the data structures, and at least one scenario test that runs a full simulation in a separate JVM (`ScenarioProcess`) for every feature that changes simulated behaviour.
4. **State what is not modelled.** Every model (accelerator, battery, cache, failure) documents its assumptions and limits in its Javadoc and in `docs/USER_GUIDE.md`.
5. **One commit per coherent change**, with a message that explains why the change is needed, not only what it does.

## Code style

* Java 21 language level; no dependency beyond those of iFogSim.
* Javadoc on public classes and public methods.
* Simulated times are in milliseconds, lengths in MI, bandwidths in iFogSim's units.
