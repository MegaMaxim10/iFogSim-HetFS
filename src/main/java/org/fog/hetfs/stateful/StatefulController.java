package org.fog.hetfs.stateful;

import org.fog.hetfs.topology.GraphAwareController;
import org.fog.hetfs.topology.PhysicalGraph;

import java.util.*;

/**
 * Extension of GraphAwareController that manages the lifecycle of stateful application modules.
 * Calls onInit() on all registered modules at simulation start and onCheckpoint() before
 * simulation end.
 */
public class StatefulController extends GraphAwareController {
    private List<StatefulAppModule> statefulModules;

    /**
     * Creates a new StatefulController for managing stateful applications.
     *
     * @param name the controller name
     * @param graph the PhysicalGraph representing the topology
     */
    public StatefulController(String name, PhysicalGraph graph) {
        super(name, graph);
        this.statefulModules = new ArrayList<>();
    }

    /**
     * Registers a stateful application module with this controller.
     *
     * @param module the StatefulAppModule to register
     */
    public void registerStatefulModule(StatefulAppModule module) {
        if (!statefulModules.contains(module)) {
            statefulModules.add(module);
        }
    }

    /**
     * Registers multiple stateful application modules.
     *
     * @param modules the modules to register
     */
    public void registerStatefulModules(List<StatefulAppModule> modules) {
        for (StatefulAppModule module : modules) {
            registerStatefulModule(module);
        }
    }

    /**
     * Initializes all registered stateful modules with empty initial state.
     * This should be called at simulation start.
     */
    public void initializeStatefulModules() {
        for (StatefulAppModule module : statefulModules) {
            ModuleState initialState = new ModuleState();
            module.initialize(initialState);
        }
    }

    /**
     * Initializes all registered stateful modules with provided initial states.
     *
     * @param initialStates a map from module name to initial ModuleState
     */
    public void initializeStatefulModules(Map<String, ModuleState> initialStates) {
        for (StatefulAppModule module : statefulModules) {
            ModuleState state = initialStates.getOrDefault(module.getName(), new ModuleState());
            module.initialize(state);
        }
    }

    /**
     * Checkpoints all registered stateful modules.
     * Should be called before simulation end.
     *
     * @return a map from module name to checkpoint byte array
     */
    public Map<String, byte[]> checkpointAllModules() {
        Map<String, byte[]> checkpoints = new HashMap<>();
        for (StatefulAppModule module : statefulModules) {
            byte[] checkpoint = module.createCheckpoint();
            checkpoints.put(module.getName(), checkpoint);
        }
        return checkpoints;
    }

    /**
     * Gets all registered stateful modules.
     *
     * @return a list of registered StatefulAppModule instances
     */
    public List<StatefulAppModule> getStatefulModules() {
        return new ArrayList<>(statefulModules);
    }

    /**
     * Gets a specific stateful module by name.
     *
     * @param moduleName the name of the module
     * @return the StatefulAppModule, or null if not found
     */
    public StatefulAppModule getStatefulModule(String moduleName) {
        for (StatefulAppModule module : statefulModules) {
            if (module.getName().equals(moduleName)) {
                return module;
            }
        }
        return null;
    }

    @Override
    public void startEntity() {
        super.startEntity();
        initializeStatefulModules();
    }

    @Override
    public void shutdownEntity() {
        checkpointAllModules();
        statefulModules.clear();
        super.shutdownEntity();
    }

    @Override
    public String toString() {
        return String.format("StatefulController(statefulModules=%d, graphDevices=%d)",
                statefulModules.size(), getGraphDevices().size());
    }
}
