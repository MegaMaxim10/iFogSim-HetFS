# Contributing to iFogSim-HetFS

## Reporting problems

Open an issue on [GitHub](https://github.com/MegaMaxim10/iFogSim-HetFS/issues) with the iFogSim commit you build against, your JDK version, a minimal simulation that shows the problem, the result you expected and the result you obtained. When the problem concerns a difference with plain iFogSim, please include the same simulation run with `FogDevice` (`DeviceBuilder#buildVanilla()`).

## Development set-up

* JDK 21; Maven 3.6+ (optional, see `build.sh`).
* `scripts/install-ifogsim.sh` builds iFogSim 5f68d39, installs it in the local Maven repository and copies the jars into `lib/`.
* `mvn verify` (or `./build.sh verify`) compiles both modules and runs every test class in its own JVM. Run it before every commit.

## Project structure

* `hetfs-core` holds the library and nothing else. A class goes in the package of the module (D1 to D7) whose limitation it addresses; `core` holds the entities shared by all modules.
* `hetfs-examples` holds the example programs, their shared support classes (`examples.support`) and the integration tests.
* Experiment scripts, raw results and generated tables go in `experiments/`; they are regenerated, never edited by hand.

## Rules for changes

1. **Do not modify iFogSim.** New behaviour is added by subclassing and by overriding protected methods. If an iFogSim method has to be copied to insert a hook (as `HetFSFogDevice#checkCloudletCompletion` does), keep the copy line for line and say so in its Javadoc.
2. **A component left unset must change nothing.** Every new feature of `HetFSFogDevice`, `HetFSSensor` or `HetFSActuator` is off by default. `BackwardCompatibilityIT` and `ReferenceScenariosIT` must keep passing unchanged: they compare HetFS devices without components with plain iFogSim on published results.
3. **Test inside the engine.** Unit tests (`*Test`, in `hetfs-core`) cover the data structures and models. Every feature that changes simulated behaviour also needs an integration test (`*IT`, in `hetfs-examples`) that runs a full simulation in a separate JVM through `ScenarioProcess`, because CloudSim and iFogSim keep global static state.
4. **State what is not modelled.** Every model (accelerator, battery, cache, failure) documents its assumptions and limits in its Javadoc and in `docs/USER_GUIDE.md`.
5. **Keep the documentation in step.** A change of the public API updates the Javadoc, the user guide and `CHANGELOG.md` in the same commit.
6. **One commit per coherent change**, with a short imperative subject line and a body that explains why the change is needed, not only what it does.

## Code style

* Java 21 language level; no dependency beyond those of iFogSim. The build compiles with `-Xlint:all` and must stay free of warnings in HetFS code.
* Formatting follows `.editorconfig`: UTF-8, LF, four-space indentation, lines of at most 120 characters, no wildcard imports, imports grouped `java.*` then the others in alphabetical order.
* Classes not meant for extension are `final`; fields are `private`; constants are `static final` in upper case.
* Javadoc on every public class and public method, and a `package-info.java` for every package.
* Simulated times are in milliseconds, lengths in MI, bandwidths in iFogSim's units.
