package org.fog.hetfs.examples.reference;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.hetfs.concurrent.ExecutionMonitor;
import org.fog.hetfs.concurrent.ModuleExecutionStats;
import org.fog.hetfs.core.DeviceBuilder;
import org.fog.hetfs.core.HetFSActuator;
import org.fog.hetfs.core.HetFSController;
import org.fog.hetfs.core.HetFSFogDevice;
import org.fog.hetfs.core.HetFSSensor;
import org.fog.hetfs.examples.support.Options;
import org.fog.hetfs.examples.support.ResultRow;
import org.fog.hetfs.timing.LatencyTracer;
import org.fog.hetfs.topology.GraphRouter;
import org.fog.hetfs.topology.PhysicalGraph;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacement;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.utils.Config;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * The two case studies of the original iFogSim paper (Gupta et al., Software:
 * Practice and Experience, 2017), as distributed with iFogSim in
 * {@code org.fog.test.perfeval.VRGameFog} and {@code DCNSFog} (MIT licence,
 * Copyright (c) 2025 The CLOUDS Laboratory), re-expressed so that they can run on
 * plain {@code FogDevice}s or on {@link HetFSFogDevice}s:
 * <ul>
 *   <li>{@code vrgame}: EEG Tractor Beam game. A cloud, a proxy server, {@code groups}
 *       gateways and {@code per-group} smartphones per gateway, each with an EEG
 *       sensor (one reading every 5 ms) and a display; loop EEG, client, concentration
 *       calculator, client, display.</li>
 *   <li>{@code dcns}: distributed camera network for intelligent surveillance. A
 *       cloud, a proxy server, {@code groups} area routers and {@code per-group} smart
 *       cameras per router (one frame every 5 ms) with a PTZ actuator; loops motion
 *       detector, object detector, object tracker, and object tracker, PTZ control.</li>
 * </ul>
 * Topology parameters, module costs, tuple sizes, selectivities and placements are
 * those of the original classes.
 *
 * <p>The applications use {@code FractionalSelectivity}, which draws from
 * {@code Math.random()}. With {@code --seed s} the generator behind
 * {@code Math.random()} is reseeded before the run so that two runs with the same
 * seed draw the same sequence; this needs the JVM option
 * {@code --add-opens java.base/java.lang=ALL-UNNAMED}.
 *
 * <p>Options:
 * <pre>
 *   --app vrgame               vrgame | dcns
 *   --placement edgeward       edgeward | cloud
 *   --groups 1                 gateways (vrgame) or area routers (dcns)
 *   --per-group 4              end devices per group
 *   --devices vanilla          vanilla | hetfs
 *   --scheduling-interval 10   PowerDatacenter scheduling interval of every device (ms)
 *   --seed s                   seed of the generator behind Math.random()
 *   --sim-time ms              simulated duration
 *   --graph                    D1: route over the graph equivalent to the tree
 *   --exact-completion         D3: completion events at the exact finish time
 *   --monitor                  D3: per-module execution monitor
 *   --trace                    D6: HetFS sensors and actuators and a lineage tracer
 *   --out results.csv          append the result row to this file (default: print it)
 *   --label l --run r          identifiers copied into the row
 * </pre>
 */
public final class ReferenceScenarios {

    private static final String[] OPTIONS = {
        "app", "placement", "groups", "per-group", "devices", "scheduling-interval", "seed", "sim-time", "graph",
        "exact-completion", "monitor", "trace", "out", "label", "run"};

    // Configuration
    private final Options opt;
    private final String appName;
    private final boolean cloudPlacement;
    private final int groups;
    private final int perGroup;
    private final boolean hetfs;

    // Entities
    private final List<FogDevice> devices = new ArrayList<>();
    private final List<Sensor> sensors = new ArrayList<>();
    private final List<Actuator> actuators = new ArrayList<>();
    private final Map<Integer, String> loopNames = new LinkedHashMap<>();

    // Components
    private ExecutionMonitor monitor;
    private LatencyTracer tracer;

    // Results
    private long wallStart;
    private final ResultRow result = new ResultRow();

    private ReferenceScenarios(Options opt) {
        this.opt = opt;
        this.appName = opt.get("app", "vrgame");
        this.cloudPlacement = "cloud".equals(opt.get("placement", "edgeward"));
        this.groups = opt.getInt("groups", 1);
        this.perGroup = opt.getInt("per-group", 4);
        this.hetfs = "hetfs".equals(opt.get("devices", "vanilla"));
        if (!"vrgame".equals(appName) && !"dcns".equals(appName)) {
            throw new IllegalArgumentException("unknown --app value: " + appName);
        }
        if (!hetfs) {
            for (String o : new String[] {"graph", "exact-completion", "monitor", "trace"}) {
                if (opt.has(o)) {
                    throw new IllegalArgumentException("--" + o + " requires --devices hetfs");
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new ReferenceScenarios(Options.parse(args, OPTIONS)).run();
    }

    /** Reseeds the generator behind {@code Math.random()}. */
    static void seedMathRandom(long seed) throws ReflectiveOperationException {
        Class<?> holder = Class.forName("java.lang.Math$RandomNumberGeneratorHolder");
        Field f = holder.getDeclaredField("randomNumberGenerator");
        f.setAccessible(true);
        ((Random) f.get(null)).setSeed(seed);
    }

    // ==================================================================
    // Simulation
    // ==================================================================

    private void run() throws Exception {
        if (opt.has("seed")) {
            seedMathRandom(opt.getLong("seed", 0));
        }
        Log.disable();
        if (opt.has("sim-time")) {
            Config.MAX_SIMULATION_TIME = opt.getInt("sim-time", Config.MAX_SIMULATION_TIME);
        }
        CloudSim.init(1, Calendar.getInstance(), false);
        FogBroker broker = new FogBroker("broker");
        Application app = "dcns".equals(appName) ? dcnsApplication(broker.getId()) : vrgameApplication(broker.getId());
        app.setUserId(broker.getId());
        createDevices(broker.getId(), app.getAppId());
        if (opt.has("graph")) {
            GraphRouter router = new GraphRouter(PhysicalGraph.fromTree(devices));
            for (FogDevice d : devices) {
                ((HetFSFogDevice) d).setRouter(router);
            }
        }

        ModuleMapping mapping = ModuleMapping.createModuleMapping();
        if ("dcns".equals(appName)) {
            for (FogDevice d : devices) {
                if (d.getName().startsWith("m")) {
                    mapping.addModuleToDevice("motion_detector", d.getName());
                }
            }
            mapping.addModuleToDevice("user_interface", "cloud");
            if (cloudPlacement) {
                mapping.addModuleToDevice("object_detector", "cloud");
                mapping.addModuleToDevice("object_tracker", "cloud");
            }
        } else {
            mapping.addModuleToDevice("connector", "cloud");
            if (cloudPlacement) {
                mapping.addModuleToDevice("concentration_calculator", "cloud");
                for (FogDevice d : devices) {
                    if (d.getName().startsWith("m")) {
                        mapping.addModuleToDevice("client", d.getName());
                    }
                }
            }
        }
        HetFSController controller = new HetFSController("master-controller", devices, sensors, actuators);
        controller.setExitOnStop(false);
        ModulePlacement placement = cloudPlacement ? new ModulePlacementMapping(devices, app, mapping)
                : new ModulePlacementEdgewards(devices, sensors, actuators, app, mapping);
        controller.submitApplication(app, 0, placement);
        controller.addEndListener(this::collect);
        TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
        wallStart = System.nanoTime();
        CloudSim.startSimulation();
        result.output(opt.get("out", null));
    }

    // ==================================================================
    // Devices, sensors and actuators
    // ==================================================================

    private FogDevice device(DeviceBuilder b, int parentId, double uplinkLatency) {
        if (opt.has("scheduling-interval")) {
            b.schedulingInterval(opt.getDouble("scheduling-interval", 10));
        }
        FogDevice d = hetfs ? b.build() : b.buildVanilla();
        if (hetfs) {
            HetFSFogDevice h = (HetFSFogDevice) d;
            h.setExactCompletionEvents(opt.has("exact-completion"));
            if (opt.has("trace")) {
                h.setTracer(tracer());
            }
            if (opt.has("monitor")) {
                if (monitor == null) {
                    monitor = new ExecutionMonitor();
                }
                h.setExecutionMonitor(monitor);
            }
        }
        d.setParentId(parentId);
        d.setUplinkLatency(uplinkLatency);
        devices.add(d);
        return d;
    }

    private LatencyTracer tracer() {
        if (tracer == null) {
            tracer = new LatencyTracer();
        }
        return tracer;
    }

    private Sensor sensor(String name, String type, int userId, String appId) {
        if (!opt.has("trace")) {
            return new Sensor(name, type, userId, appId, new DeterministicDistribution(5));
        }
        HetFSSensor s = new HetFSSensor(name, type, userId, appId, new DeterministicDistribution(5));
        s.setTracer(tracer());
        return s;
    }

    private Actuator actuator(String name, int userId, String appId, String type) {
        if (!opt.has("trace")) {
            return new Actuator(name, userId, appId, type);
        }
        HetFSActuator a = new HetFSActuator(name, userId, appId, type);
        a.setTracer(tracer());
        return a;
    }

    private static DeviceBuilder spec(String name, long mips, int ram, long up, long down, int level,
                                      double rate, double busy, double idle) {
        return DeviceBuilder.named(name).mips(mips).ram(ram).bandwidth(up, down).level(level)
                .ratePerMips(rate).power(busy, idle);
    }

    private void createDevices(int userId, String appId) {
        FogDevice cloud = device(spec("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16 * 103, 16 * 83.25), -1, 0);
        FogDevice proxy = device(spec("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333),
                cloud.getId(), 100);
        boolean dcns = "dcns".equals(appName);
        for (int g = 0; g < groups; g++) {
            FogDevice gw = device(spec("d-" + g, 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333),
                    proxy.getId(), dcns ? 2 : 4);
            for (int i = 0; i < perGroup; i++) {
                String id = g + "-" + i;
                if (dcns) {
                    FogDevice cam = device(spec("m-" + id, 500, 1000, 10000, 10000, 3, 0, 87.53, 82.44), gw.getId(), 2);
                    Sensor s = sensor("s-" + id, "CAMERA", userId, appId);
                    s.setGatewayDeviceId(cam.getId());
                    s.setLatency(1.0);
                    sensors.add(s);
                    Actuator a = actuator("ptz-" + id, userId, appId, "PTZ_CONTROL");
                    a.setGatewayDeviceId(cam.getId());
                    a.setLatency(1.0);
                    actuators.add(a);
                } else {
                    FogDevice mob = device(spec("m-" + id, 1000, 1000, 10000, 270, 3, 0, 87.53, 82.44), gw.getId(), 2);
                    Sensor s = sensor("s-" + id, "EEG", userId, appId);
                    s.setGatewayDeviceId(mob.getId());
                    s.setLatency(6.0);
                    sensors.add(s);
                    Actuator a = actuator("a-" + id, userId, appId, "DISPLAY");
                    a.setGatewayDeviceId(mob.getId());
                    a.setLatency(1.0);
                    actuators.add(a);
                }
            }
        }
    }

    @SuppressWarnings("serial")
    // ==================================================================
    // Applications (as in VRGameFog and DCNSFog)
    // ==================================================================

    private Application vrgameApplication(int userId) {
        Application a = Application.createApplication("vr_game", userId);
        a.addAppModule("client", 10);
        a.addAppModule("concentration_calculator", 10);
        a.addAppModule("connector", 10);
        // EEG_TRANSMISSION_TIME = 5 in VRGameFog, hence the 3000 MI branch
        a.addAppEdge("EEG", "client", 3000, 500, "EEG", Tuple.UP, AppEdge.SENSOR);
        a.addAppEdge("client", "concentration_calculator", 3500, 500, "_SENSOR", Tuple.UP, AppEdge.MODULE);
        a.addAppEdge("concentration_calculator", "connector", 100, 1000, 1000, "PLAYER_GAME_STATE", Tuple.UP,
                AppEdge.MODULE);
        a.addAppEdge("concentration_calculator", "client", 14, 500, "CONCENTRATION", Tuple.DOWN, AppEdge.MODULE);
        a.addAppEdge("connector", "client", 100, 28, 1000, "GLOBAL_GAME_STATE", Tuple.DOWN, AppEdge.MODULE);
        a.addAppEdge("client", "DISPLAY", 1000, 500, "SELF_STATE_UPDATE", Tuple.DOWN, AppEdge.ACTUATOR);
        a.addAppEdge("client", "DISPLAY", 1000, 500, "GLOBAL_STATE_UPDATE", Tuple.DOWN, AppEdge.ACTUATOR);
        a.addTupleMapping("client", "EEG", "_SENSOR", new FractionalSelectivity(0.9));
        a.addTupleMapping("client", "CONCENTRATION", "SELF_STATE_UPDATE", new FractionalSelectivity(1.0));
        a.addTupleMapping("concentration_calculator", "_SENSOR", "CONCENTRATION", new FractionalSelectivity(1.0));
        a.addTupleMapping("client", "GLOBAL_GAME_STATE", "GLOBAL_STATE_UPDATE", new FractionalSelectivity(1.0));
        final AppLoop loop = new AppLoop(new ArrayList<String>() {{
            add("EEG");
            add("client");
            add("concentration_calculator");
            add("client");
            add("DISPLAY");
        }});
        a.setLoops(new ArrayList<AppLoop>() {{
            add(loop);
        }});
        loopNames.put(loop.getLoopId(), "loop_eeg_display");
        return a;
    }

    @SuppressWarnings("serial")
    private Application dcnsApplication(int userId) {
        Application a = Application.createApplication("dcns", userId);
        a.addAppModule("object_detector", 10);
        a.addAppModule("motion_detector", 10);
        a.addAppModule("object_tracker", 10);
        a.addAppModule("user_interface", 10);
        a.addAppEdge("CAMERA", "motion_detector", 1000, 20000, "CAMERA", Tuple.UP, AppEdge.SENSOR);
        a.addAppEdge("motion_detector", "object_detector", 2000, 2000, "MOTION_VIDEO_STREAM", Tuple.UP, AppEdge.MODULE);
        a.addAppEdge("object_detector", "user_interface", 500, 2000, "DETECTED_OBJECT", Tuple.UP, AppEdge.MODULE);
        a.addAppEdge("object_detector", "object_tracker", 1000, 100, "OBJECT_LOCATION", Tuple.UP, AppEdge.MODULE);
        a.addAppEdge("object_tracker", "PTZ_CONTROL", 100, 28, 100, "PTZ_PARAMS", Tuple.DOWN, AppEdge.ACTUATOR);
        a.addTupleMapping("motion_detector", "CAMERA", "MOTION_VIDEO_STREAM", new FractionalSelectivity(1.0));
        a.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "OBJECT_LOCATION", new FractionalSelectivity(1.0));
        a.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "DETECTED_OBJECT", new FractionalSelectivity(0.05));
        final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
            add("motion_detector");
            add("object_detector");
            add("object_tracker");
        }});
        final AppLoop loop2 = new AppLoop(new ArrayList<String>() {{
            add("object_tracker");
            add("PTZ_CONTROL");
        }});
        a.setLoops(new ArrayList<AppLoop>() {{
            add(loop1);
            add(loop2);
        }});
        loopNames.put(loop1.getLoopId(), "loop_motion_tracker");
        loopNames.put(loop2.getLoopId(), "loop_tracker_ptz");
        return a;
    }

    // ==================================================================
    // Results
    // ==================================================================

    private void collect() {
        double wallMs = (System.nanoTime() - wallStart) / 1e6;
        result.put("label", opt.get("label", ""));
        result.put("run", opt.get("run", ""));
        result.put("app", appName);
        result.put("placement", cloudPlacement ? "cloud" : "edgeward");
        result.put("groups", groups);
        result.put("per_group", perGroup);
        result.put("devices", hetfs ? "hetfs" : "vanilla");
        result.put("exact_completion", opt.has("exact-completion"));
        result.put("graph", opt.has("graph"));
        result.put("trace", opt.has("trace"));
        result.put("device_count", devices.size());
        result.put("seed", opt.get("seed", ""));
        Map<Integer, Double> lat = TimeKeeper.getInstance().getLoopIdToCurrentAverage();
        Map<Integer, Integer> num = TimeKeeper.getInstance().getLoopIdToCurrentNum();
        for (Map.Entry<Integer, String> e : loopNames.entrySet()) {
            result.put(e.getValue() + "_ms", lat.get(e.getKey()));
            result.put(e.getValue() + "_n", num.get(e.getKey()));
        }
        double cloudEnergy = 0;
        double edgeEnergy = 0;
        double endEnergy = 0;
        double cloudCost = 0;
        for (FogDevice d : devices) {
            if (d.getName().equals("cloud")) {
                cloudEnergy += d.getEnergyConsumption();
                cloudCost = d.getTotalCost();
            } else if (d.getName().startsWith("m-")) {
                endEnergy += d.getEnergyConsumption();
            } else {
                edgeEnergy += d.getEnergyConsumption();
            }
        }
        result.put("energy_cloud_j", cloudEnergy);
        result.put("energy_edge_j", edgeEnergy);
        result.put("energy_end_j", endEnergy);
        result.put("network_usage", NetworkUsageMonitor.getNetworkUsage() / Config.MAX_SIMULATION_TIME);
        result.put("cloud_cost", cloudCost);
        if (tracer != null) {
            result.put("trace_records", tracer.getRecordCount());
        }
        if (monitor != null) {
            double maxExcess = 0;
            for (ModuleExecutionStats s : monitor.getAll()) {
                maxExcess = Math.max(maxExcess, s.getMaxExcessMs());
            }
            result.put("max_module_excess_ms", maxExcess);
        }
        result.put("wall_clock_ms", wallMs);
    }
}
