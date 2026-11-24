package org.fog.hetfs.examples.emergency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
import org.fog.hetfs.energy.BatteryModel;
import org.fog.hetfs.energy.SensorEnergyReport;
import org.fog.hetfs.events.FaultInjector;
import org.fog.hetfs.events.TopologyEventScheduler;
import org.fog.hetfs.examples.support.Options;
import org.fog.hetfs.examples.support.ResultRow;
import org.fog.hetfs.hardware.AccelerationProfile;
import org.fog.hetfs.hardware.ComplexityWorkload;
import org.fog.hetfs.hardware.ComputeArchitecture;
import org.fog.hetfs.hardware.HardwareProfile;
import org.fog.hetfs.stateful.LruCacheBehaviour;
import org.fog.hetfs.timing.LatencyTracer;
import org.fog.hetfs.timing.LatencyTracer.Point;
import org.fog.hetfs.timing.RunningStatistics;
import org.fog.hetfs.topology.GraphRouter;
import org.fog.hetfs.topology.PhysicalGraph;
import org.fog.hetfs.validation.ApplicationValidator;
import org.fog.hetfs.validation.Finding;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementMapping;
import org.fog.utils.Config;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * The smart emergency response architecture of the companion paper (Zekeng Ndadji,
 * arXiv:2604.23122), with one command-line option per iFogSim-HetFS module.
 *
 * <p>With {@code --devices vanilla} and no module option the scenario is identical to
 * {@code SmartEmergencyResponseSimulation} of the companion reproducibility package
 * (Zenodo, doi:10.5281/zenodo.22234733): same device tree (cloud, edge server node
 * ESN, five zone devices CAD, eight unit devices IOPS), same application graph, same
 * placement, same {@code AppLoop}s, and the same seeded &plusmn;2&nbsp;% Gaussian jitter
 * on the four propagation latencies, drawn in the same order. It reproduces the
 * published results to the last digit, which {@code BackwardCompatibilityIT} checks.
 *
 * <p>Options:
 * <pre>
 *   --zones z1,z2              zones whose sensor fires (default z1)
 *   --focal-zone z1            zone tracked by the alert AppLoop (default: first zone)
 *   --focal-unit c1            unit tracked by the dispatch AppLoop (default c1)
 *   --seed 1                   jitter seed
 *   --devices hetfs            vanilla | hetfs
 *   --scheduling-interval 10   PowerDatacenter scheduling interval of every device (ms)
 *   --min-time-between-events 0.01   CloudSim's minimal time between events (ms)
 *   --sim-time 2000            simulated duration (ms)
 *   --sensor-interval 10       emission period of the zone sensors (ms)
 *
 *   --graph none               D1: none | tree | mesh (mesh adds a ring of CAD links)
 *   --routing-kernel           D2: Dijkstra unit ranking as a module (esn-router) on the ESN
 *   --road-nodes 25            D2: size of the road network (relay junctions added beyond 25)
 *   --mi-fixed a --mi-per-op c D2: workload a + c (V+E) log2 V in MI
 *   --accel fpga:10[:f[:o]]    D2: accelerator for esn-router (speedup, fraction, overhead ms)
 *   --exact-completion         D3: completion events at the exact finish time
 *   --monitor                  D3: per-module execution monitor
 *   --cache n[:hitMi[:ttlMs]]  D4: LRU path cache of n zones on esn-router
 *   --checkpoint-interval ms   D4: checkpoint period of the ESN's stateful modules
 *   --fail-device esn@500-800  D5: device outage; several separated by ';'
 *   --fail-link cad-z1~esn@300-900   D5: link outage in both directions (needs --graph)
 *   --trace                    D6: lineage tracing of the focal branch
 *   --trace-since 0            D6: ignore the tuples emitted before this time (ms)
 *   --battery C:tx[:idle]      D7: battery of C J for every zone sensor, tx J per alert, idle W
 *   --shared-incident-type     all zone sensors emit the tuple type INCIDENT (challenge C8)
 *   --hetfs-sensors            zone sensors are HetFSSensors with an explicit destination
 *   --validate                 run ApplicationValidator before the simulation
 *
 *   --out results.csv          append the result row to this file (default: print it)
 *   --label l --run r          identifiers copied into the row
 * </pre>
 */
public final class EmergencyScenario {

    /** Application identifier. */
    public static final String APP_ID = "smart_emergency_response";

    // Devices (Table 3 of the companion paper)
    static final long ESN_MIPS = 10000;
    static final int ESN_RAM = 8192;
    static final long CAD_MIPS = 2000;
    static final int CAD_RAM = 1024;
    static final long IOPS_MIPS = 1500;
    static final int IOPS_RAM = 512;

    // Propagation latencies (ms)
    static final double LAT_SAS_TO_CAD = 5.0;
    static final double LAT_CAD_TO_ESN = 100.0;
    static final double LAT_ESN_TO_IOPS = 80.0;
    static final double LAT_IOPS_TO_ITGS = 15.0;
    static final double LAT_ESN_TO_CLOUD = 50.0;
    static final double LAT_CAD_RING = 20.0;
    static final double JITTER = 0.02;

    // Application
    static final double SENSOR_INTERVAL = 10.0;
    static final long TUPLE_BYTES = 256;
    static final long LINK_BANDWIDTH = 10_000_000;
    static final String ACTUATOR_TYPE = "RESPONSE_UNIT";

    /**
     * Default Dijkstra workload a + c (V+E) log2 V on the 10 000-MIPS ESN: least-squares
     * fit of the median times measured by {@link CalibrateDijkstra} on 25- to 400-node
     * networks (5 JVMs, 5 000 timed runs per size; experiments/results/calibration.csv).
     */
    static final double DEFAULT_MI_FIXED = 4.40;
    static final double DEFAULT_MI_PER_OP = 0.0487;
    static final long DEFAULT_CACHE_HIT_MI = 8;
    static final long ROAD_SCALING_SEED = 7;

    private static final String[] OPTIONS = {
        "zones", "focal-zone", "focal-unit", "seed", "devices", "scheduling-interval", "min-time-between-events",
        "sim-time", "sensor-interval", "graph", "routing-kernel", "road-nodes", "mi-fixed", "mi-per-op", "accel",
        "exact-completion", "monitor", "cache", "checkpoint-interval", "fail-device", "fail-link", "trace",
        "trace-since", "battery", "shared-incident-type", "hetfs-sensors", "validate", "out", "label", "run"};

    // Configuration
    private final Options opt;
    private final Set<String> activeZones;
    private final String focalZone;
    private final String focalUnit;
    private final long seed;
    private final boolean hetfs;
    private final String graphMode;

    // Entities
    private final List<FogDevice> devices = new ArrayList<>();
    private final List<Sensor> sensors = new ArrayList<>();
    private final List<Actuator> actuators = new ArrayList<>();
    private final Map<String, FogDevice> devicesByName = new LinkedHashMap<>();
    private final Map<String, String> modulePins = new LinkedHashMap<>();

    // Jittered latencies of this run
    private double latSasToCad;
    private double latCadToEsn;
    private double latEsnToIops;
    private double latIopsToItgs;

    // Components
    private PhysicalGraph graph;
    private ExecutionMonitor monitor;
    private LatencyTracer tracer;
    private RoadNetwork road;
    private ComplexityWorkload routingWorkload;
    private AccelerationProfile acceleration;
    private LruCacheBehaviour cache;
    private TopologyEventScheduler faults;
    private List<Finding> findings;

    // Results
    private int loopAlertId = -1;
    private int loopDispatchId = -1;
    private long wallStart;
    private final ResultRow result = new ResultRow();

    private EmergencyScenario(Options opt) {
        this.opt = opt;
        this.activeZones = new LinkedHashSet<>(Arrays.asList(opt.get("zones", "z1").split(",")));
        this.focalZone = opt.get("focal-zone", activeZones.iterator().next());
        this.focalUnit = opt.get("focal-unit", "c1");
        this.seed = opt.getLong("seed", 1);
        this.hetfs = !"vanilla".equals(opt.get("devices", "hetfs"));
        this.graphMode = opt.get("graph", "none");
        checkOptions();
    }

    public static void main(String[] args) throws Exception {
        new EmergencyScenario(Options.parse(args, OPTIONS)).run();
    }

    private void checkOptions() {
        if (!activeZones.contains(focalZone)) {
            throw new IllegalArgumentException("the focal zone must be active: " + focalZone);
        }
        if (!hetfs) {
            for (String o : new String[] {"graph", "routing-kernel", "exact-completion", "monitor", "trace",
                "battery", "hetfs-sensors", "fail-device", "fail-link"}) {
                if (opt.has(o)) {
                    throw new IllegalArgumentException("--" + o + " requires --devices hetfs");
                }
            }
        }
        if (!routingKernel()) {
            for (String o : new String[] {"accel", "cache", "checkpoint-interval", "road-nodes"}) {
                if (opt.has(o)) {
                    throw new IllegalArgumentException("--" + o + " requires --routing-kernel");
                }
            }
        }
        if (opt.has("fail-link") && "none".equals(graphMode)) {
            throw new IllegalArgumentException("--fail-link requires --graph");
        }
    }

    // ==================================================================
    // Simulation
    // ==================================================================

    private void run() throws Exception {
        drawJitter();
        Log.disable();
        if (opt.has("sim-time")) {
            Config.MAX_SIMULATION_TIME = opt.getInt("sim-time", Config.MAX_SIMULATION_TIME);
        }
        if (opt.has("min-time-between-events")) {
            CloudSim.init(1, Calendar.getInstance(), false, opt.getDouble("min-time-between-events", 0.01));
        } else {
            CloudSim.init(1, Calendar.getInstance(), false);
        }

        FogBroker broker = new FogBroker("broker");
        Application app = createApplication(broker.getId());
        app.setUserId(broker.getId());
        createDevices(broker.getId());
        configureRoutingKernel();

        ModuleMapping mapping = ModuleMapping.createModuleMapping();
        mapping.addModuleToDevice("esn-classifier", "esn");
        mapping.addModuleToDevice("esn-dispatcher", "esn");
        modulePins.forEach(mapping::addModuleToDevice);
        if (opt.has("validate")) {
            findings = ApplicationValidator.validate(app, sensors, ApplicationValidator.Placement.MAPPING, mapping);
            findings.forEach(System.err::println);
        }

        HetFSController controller = new HetFSController("master-controller", devices, sensors, actuators);
        controller.setExitOnStop(false);
        controller.submitApplication(app, 0, new ModulePlacementMapping(devices, app, mapping));
        controller.addEndListener(this::collectResults);
        configureFaults();

        TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
        wallStart = System.nanoTime();
        CloudSim.startSimulation();
        result.output(opt.get("out", null));
    }

    /** Same draws, in the same order, as the companion implementation. */
    private void drawJitter() {
        Random rnd = new Random(seed);
        latSasToCad = LAT_SAS_TO_CAD * (1.0 + rnd.nextGaussian() * JITTER);
        latCadToEsn = LAT_CAD_TO_ESN * (1.0 + rnd.nextGaussian() * JITTER);
        latEsnToIops = LAT_ESN_TO_IOPS * (1.0 + rnd.nextGaussian() * JITTER);
        latIopsToItgs = LAT_IOPS_TO_ITGS * (1.0 + rnd.nextGaussian() * JITTER);
    }

    // ==================================================================
    // Devices, sensors and actuators
    // ==================================================================

    private void createDevices(int userId) {
        FogDevice cloud = device(DeviceBuilder.named("cloud").mips(44800).ram(40000)
                .bandwidth(LINK_BANDWIDTH, LINK_BANDWIDTH).level(0).ratePerMips(0.01).power(16 * 103.0, 16 * 83.25));
        cloud.setParentId(-1);

        FogDevice esn = device(DeviceBuilder.named("esn").mips(ESN_MIPS).ram(ESN_RAM)
                .bandwidth(LINK_BANDWIDTH, LINK_BANDWIDTH).level(1).ratePerMips(0.01).power(16 * 103.0, 16 * 83.25));
        esn.setParentId(cloud.getId());
        esn.setUplinkLatency(LAT_ESN_TO_CLOUD);

        for (String zone : RoadNetwork.ZONES) {
            FogDevice cad = device(DeviceBuilder.named("cad-" + zone).mips(CAD_MIPS).ram(CAD_RAM)
                    .bandwidth(LINK_BANDWIDTH, LINK_BANDWIDTH).level(2).ratePerMips(0.005).power(107.339, 83.4333));
            cad.setParentId(esn.getId());
            cad.setUplinkLatency(latCadToEsn);
            if (activeZones.contains(zone)) {
                Sensor sas = createSensor("sas-" + zone, incidentType(zone), userId);
                if (sas instanceof HetFSSensor) {
                    sas.setDestModuleName(cadModule(zone));
                }
                sas.setGatewayDeviceId(cad.getId());
                sas.setLatency(latSasToCad);
                sensors.add(sas);
            }
        }
        for (String unit : RoadNetwork.UNITS) {
            FogDevice iops = device(DeviceBuilder.named("iops-" + unit).mips(IOPS_MIPS).ram(IOPS_RAM)
                    .bandwidth(LINK_BANDWIDTH, LINK_BANDWIDTH).level(2).ratePerMips(0.002).power(87.53, 82.44));
            iops.setParentId(esn.getId());
            iops.setUplinkLatency(latEsnToIops);
            Actuator itgs = createActuator("itgs-" + unit, userId);
            itgs.setGatewayDeviceId(iops.getId());
            itgs.setLatency(latIopsToItgs);
            actuators.add(itgs);
        }
        if (!"none".equals(graphMode)) {
            buildGraph();
        }
    }

    private FogDevice device(DeviceBuilder builder) {
        if (opt.has("scheduling-interval")) {
            builder.schedulingInterval(opt.getDouble("scheduling-interval", 10));
        }
        FogDevice device;
        if (hetfs) {
            HetFSFogDevice h = builder.build();
            h.setExactCompletionEvents(opt.has("exact-completion"));
            if (opt.has("trace")) {
                h.setTracer(tracer());
            }
            if (opt.has("monitor")) {
                h.setExecutionMonitor(monitor());
            }
            device = h;
        } else {
            device = builder.buildVanilla();
        }
        devices.add(device);
        devicesByName.put(device.getName(), device);
        return device;
    }

    private Sensor createSensor(String name, String tupleType, int userId) {
        DeterministicDistribution period =
                new DeterministicDistribution(opt.getDouble("sensor-interval", SENSOR_INTERVAL));
        if (!opt.has("trace") && !opt.has("battery") && !opt.has("hetfs-sensors")) {
            return new Sensor(name, tupleType, userId, APP_ID, period);
        }
        HetFSSensor sensor = new HetFSSensor(name, tupleType, userId, APP_ID, period);
        if (opt.has("trace")) {
            sensor.setTracer(tracer());
        }
        if (opt.has("battery")) {
            String[] f = opt.require("battery").split(":");
            double idle = f.length > 2 ? Double.parseDouble(f[2]) : 0;
            sensor.setBattery(new BatteryModel(Double.parseDouble(f[0]), Double.parseDouble(f[1]), 0, idle));
        }
        return sensor;
    }

    private Actuator createActuator(String name, int userId) {
        if (!opt.has("trace")) {
            return new Actuator(name, userId, APP_ID, ACTUATOR_TYPE);
        }
        HetFSActuator actuator = new HetFSActuator(name, userId, APP_ID, ACTUATOR_TYPE);
        actuator.setTracer(tracer());
        return actuator;
    }

    private void buildGraph() {
        graph = PhysicalGraph.fromTree(devices);
        if ("mesh".equals(graphMode)) {
            String[] zones = RoadNetwork.ZONES;
            for (int i = 0; i < zones.length; i++) {
                int a = devicesByName.get("cad-" + zones[i]).getId();
                int b = devicesByName.get("cad-" + zones[(i + 1) % zones.length]).getId();
                graph.addBidirectionalLink(a, b, LAT_CAD_RING, LINK_BANDWIDTH);
            }
        } else if (!"tree".equals(graphMode)) {
            throw new IllegalArgumentException("unknown --graph value: " + graphMode);
        }
        GraphRouter router = new GraphRouter(graph);
        for (FogDevice d : devices) {
            ((HetFSFogDevice) d).setRouter(router);
        }
    }

    private LatencyTracer tracer() {
        if (tracer == null) {
            tracer = new LatencyTracer();
        }
        return tracer;
    }

    private ExecutionMonitor monitor() {
        if (monitor == null) {
            monitor = new ExecutionMonitor();
        }
        return monitor;
    }

    private HetFSFogDevice esn() {
        return (HetFSFogDevice) devicesByName.get("esn");
    }

    private HetFSFogDevice hetfsDevice(String name) {
        FogDevice d = devicesByName.get(name);
        if (d == null) {
            throw new IllegalArgumentException("unknown device: " + name);
        }
        return (HetFSFogDevice) d;
    }

    // ==================================================================
    // Application (identical to the companion implementation)
    // ==================================================================

    private static String cadModule(String zone) {
        return "cad-" + zone + "-module";
    }

    private static String iopsModule(String unit) {
        return "iops-" + unit + "-module";
    }

    private String incidentType(String zone) {
        return opt.has("shared-incident-type") ? "INCIDENT" : "INCIDENT_" + zone;
    }

    private boolean routingKernel() {
        return opt.has("routing-kernel");
    }

    private Application createApplication(int userId) {
        Application app = Application.createApplication(APP_ID, userId);
        app.addAppModule("esn-classifier", 512);
        app.addAppModule("esn-dispatcher", 512);

        for (String zone : activeZones) {
            String module = cadModule(zone);
            String incident = incidentType(zone);
            app.addAppModule(module, 256);
            app.addAppEdge(incident, module, 500, TUPLE_BYTES, incident, Tuple.UP, AppEdge.SENSOR);
            app.addAppEdge(module, "esn-classifier", 2000, TUPLE_BYTES, "ALERT_MSG", Tuple.UP, AppEdge.MODULE);
            app.addTupleMapping(module, incident, "ALERT_MSG", new FractionalSelectivity(1.0));
            modulePins.put(module, "cad-" + zone);
        }
        String dispatchInput = addClassifierToDispatcher(app);

        for (String unit : RoadNetwork.UNITS) {
            String module = iopsModule(unit);
            String dispatch = "DISPATCH_" + unit;
            String guidance = "GUIDANCE_" + unit;
            app.addAppModule(module, 256);
            app.addAppEdge("esn-dispatcher", module, 1000, TUPLE_BYTES, dispatch, Tuple.DOWN, AppEdge.MODULE);
            app.addAppEdge(module, ACTUATOR_TYPE, 100, TUPLE_BYTES, guidance, Tuple.DOWN, AppEdge.ACTUATOR);
            app.addTupleMapping("esn-dispatcher", dispatchInput, dispatch, new FractionalSelectivity(1.0));
            app.addTupleMapping(module, dispatch, guidance, new FractionalSelectivity(1.0));
            modulePins.put(module, "iops-" + unit);
        }

        AppLoop loopAlert = new AppLoop(new ArrayList<>(List.of(incidentType(focalZone), cadModule(focalZone),
                "esn-classifier")));
        AppLoop loopDispatch = new AppLoop(new ArrayList<>(List.of("esn-dispatcher", iopsModule(focalUnit),
                ACTUATOR_TYPE)));
        app.setLoops(new ArrayList<>(List.of(loopAlert, loopDispatch)));
        loopAlertId = loopAlert.getLoopId();
        loopDispatchId = loopDispatch.getLoopId();
        return app;
    }

    /**
     * Connects the classifier to the dispatcher, directly or through the routing kernel.
     *
     * @return the tuple type the dispatcher receives
     */
    private String addClassifierToDispatcher(Application app) {
        if (!routingKernel()) {
            app.addAppEdge("esn-classifier", "esn-dispatcher", 3000, TUPLE_BYTES, "CLASSIFIED_MSG", Tuple.UP,
                    AppEdge.MODULE);
            app.addTupleMapping("esn-classifier", "ALERT_MSG", "CLASSIFIED_MSG", new FractionalSelectivity(1.0));
            return "CLASSIFIED_MSG";
        }
        // The length of ROUTE_REQUEST is a placeholder: the workload model of esn-router
        // replaces it by the Dijkstra cost on the road network.
        app.addAppModule("esn-router", 512);
        app.addAppEdge("esn-classifier", "esn-router", 1, TUPLE_BYTES, "ROUTE_REQUEST", Tuple.UP, AppEdge.MODULE);
        app.addTupleMapping("esn-classifier", "ALERT_MSG", "ROUTE_REQUEST", new FractionalSelectivity(1.0));
        app.addAppEdge("esn-router", "esn-dispatcher", 3000, TUPLE_BYTES, "ROUTE_RESULT", Tuple.UP, AppEdge.MODULE);
        app.addTupleMapping("esn-router", "ROUTE_REQUEST", "ROUTE_RESULT", new FractionalSelectivity(1.0));
        modulePins.put("esn-router", "esn");
        return "ROUTE_RESULT";
    }

    // ==================================================================
    // Modules D2, D4 (routing kernel) and D5 (faults)
    // ==================================================================

    private void configureRoutingKernel() {
        if (!routingKernel()) {
            return;
        }
        int nodes = opt.getInt("road-nodes", 25);
        road = nodes == 25 ? RoadNetwork.base() : RoadNetwork.scaled(nodes, ROAD_SCALING_SEED);
        routingWorkload = new ComplexityWorkload(ComplexityWorkload.Complexity.DIJKSTRA_BINARY_HEAP,
                opt.getDouble("mi-fixed", DEFAULT_MI_FIXED), opt.getDouble("mi-per-op", DEFAULT_MI_PER_OP),
                road.nodeCount(), road.roadCount());
        esn().setWorkload("esn-router", routingWorkload);
        if (opt.has("accel")) {
            acceleration = parseAcceleration(opt.require("accel"));
            esn().setHardwareProfile(HardwareProfile.cpu().accelerate("esn-router", acceleration));
        }
        if (opt.has("cache")) {
            String[] f = opt.require("cache").split(":");
            long hitMi = f.length > 1 ? Long.parseLong(f[1]) : DEFAULT_CACHE_HIT_MI;
            double ttlMs = f.length > 2 ? Double.parseDouble(f[2]) : Double.POSITIVE_INFINITY;
            cache = new LruCacheBehaviour(EmergencyScenario::incidentZone, Integer.parseInt(f[0]), hitMi, ttlMs);
            esn().setBehaviour("esn-router", cache);
        }
        if (opt.has("checkpoint-interval")) {
            esn().setCheckpointInterval(opt.getDouble("checkpoint-interval", 0));
        }
    }

    /** Incident zone a tuple descends from, read from the zone module it went through. */
    static String incidentZone(Tuple tuple) {
        for (String module : tuple.getModuleCopyMap().keySet()) {
            if (module.startsWith("cad-") && module.endsWith("-module")) {
                return module.substring("cad-".length(), module.length() - "-module".length());
            }
        }
        return null;
    }

    /** Parses {@code arch:p[:f[:o]]}. */
    static AccelerationProfile parseAcceleration(String spec) {
        String[] f = spec.split(":");
        ComputeArchitecture arch = ComputeArchitecture.valueOf(f[0].toUpperCase(Locale.ROOT));
        double speedup = Double.parseDouble(f[1]);
        double fraction = f.length > 2 ? Double.parseDouble(f[2]) : 1.0;
        double overheadMs = f.length > 3 ? Double.parseDouble(f[3]) : 0.0;
        return new AccelerationProfile(arch, speedup, fraction, overheadMs);
    }

    private void configureFaults() {
        if (!opt.has("fail-device") && !opt.has("fail-link")) {
            return;
        }
        faults = new TopologyEventScheduler("fault-injector", graph);
        FaultInjector injector = FaultInjector.on(faults);
        for (String spec : opt.get("fail-device", "").split(";")) {
            if (!spec.isEmpty()) {
                String[] parts = spec.split("@");
                double[] window = parseWindow(parts[1]);
                HetFSFogDevice device = hetfsDevice(parts[0]);
                injector.failDevice(device, window[0]);
                if (!Double.isInfinite(window[1])) {
                    injector.recoverDevice(device, window[1]);
                }
            }
        }
        for (String spec : opt.get("fail-link", "").split(";")) {
            if (!spec.isEmpty()) {
                String[] parts = spec.split("@");
                String[] ends = parts[0].split("~");
                double[] window = parseWindow(parts[1]);
                injector.failLink(hetfsDevice(ends[0]), hetfsDevice(ends[1]), window[0], window[1]);
            }
        }
    }

    /** Parses {@code from[-to]} (ms); a missing end means "until the end of the run". */
    static double[] parseWindow(String spec) {
        String[] f = spec.split("-");
        double to = f.length > 1 ? Double.parseDouble(f[1]) : Double.POSITIVE_INFINITY;
        return new double[] {Double.parseDouble(f[0]), to};
    }

    // ==================================================================
    // Results
    // ==================================================================

    private void collectResults() {
        double wallMs = (System.nanoTime() - wallStart) / 1e6;
        Map<Integer, Double> loopLatency = TimeKeeper.getInstance().getLoopIdToCurrentAverage();
        Double alert = loopLatency.get(loopAlertId);
        Double dispatch = loopLatency.get(loopDispatchId);
        result.put("label", opt.get("label", ""))
              .put("run", opt.get("run", ""))
              .put("devices", hetfs ? "hetfs" : "vanilla")
              .put("graph", graphMode)
              .put("active_zones", String.join(";", activeZones))
              .put("focal_zone", focalZone)
              .put("focal_unit", focalUnit)
              .put("seed", seed)
              .put("alert_latency_ms", alert)
              .put("dispatch_latency_ms", dispatch)
              .put("coordination_latency_ms", (alert == null ? 0 : alert) + (dispatch == null ? 0 : dispatch))
              .put("exact_completion", opt.has("exact-completion"));
        if (findings != null) {
            collectValidation();
        }
        if (hetfs) {
            collectDeviceCounters();
        }
        if (tracer != null) {
            collectTrace();
        }
        if (opt.has("battery")) {
            collectBattery();
        }
        if (routingKernel()) {
            collectRoutingKernel();
        }
        if (monitor != null) {
            collectModule("esn", "esn-classifier", "classifier");
            collectModule("esn", "esn-dispatcher", "dispatcher");
            collectModule("iops-" + focalUnit, iopsModule(focalUnit), "iops");
        }
        result.put("wall_clock_ms", wallMs);
    }

    private void collectValidation() {
        Set<String> codes = new LinkedHashSet<>();
        long errors = 0;
        for (Finding f : findings) {
            codes.add(f.getCode());
            if (f.getSeverity() == Finding.Severity.ERROR) {
                errors++;
            }
        }
        ModuleExecutionStats focalCad = monitor == null ? null : monitor.get("cad-" + focalZone, cadModule(focalZone));
        result.put("validation_errors", errors)
              .put("validation_codes", String.join(";", codes))
              .put("focal_cad_executed", focalCad == null ? 0 : focalCad.getCompleted());
    }

    private void collectDeviceCounters() {
        long dropped = 0;
        long forwarded = 0;
        for (FogDevice d : devices) {
            dropped += ((HetFSFogDevice) d).getTuplesDropped();
            forwarded += ((HetFSFogDevice) d).getTuplesForwarded();
        }
        result.put("tuples_forwarded", forwarded)
              .put("tuples_dropped", dropped)
              .put("topology_events", faults == null ? 0 : faults.getLog().size());
    }

    /**
     * Latencies of the focal branch (focal zone's sensor to focal unit's actuator)
     * measured on tuple lineages: with the boundaries of the two TimeKeeper loops, and
     * with boundaries placed after the contended modules.
     */
    private void collectTrace() {
        String sas = "sas-" + focalZone;
        String itgs = "itgs-" + focalUnit;
        double since = opt.getDouble("trace-since", 0);
        RunningStatistics e2e = tracer.segment(sas, itgs, Point.emit(), Point.actuator(), since);
        result.put("trace_emitted", tracer.emittedCount(sas))
              .put("trace_delivered", tracer.deliveredCount(sas, itgs))
              .put("e2e_n", e2e.getCount())
              .put("e2e_mean_ms", e2e.getMean())
              .put("e2e_sd_ms", e2e.getStandardDeviation())
              .put("e2e_ci95_ms", e2e.getConfidenceHalfWidth95())
              .put("e2e_max_ms", e2e.getMax())
              .put("trace_alert_tk_ms", tracer.segment(sas, itgs, Point.emit(),
                      Point.arrival("esn-classifier"), since).getMean())
              .put("trace_dispatch_tk_ms", tracer.segment(sas, itgs, Point.completion("esn-dispatcher"),
                      Point.actuator(), since).getMean())
              .put("trace_alert_full_ms", tracer.segment(sas, itgs, Point.emit(),
                      Point.completion("esn-classifier"), since).getMean())
              .put("trace_esn_stage_ms", tracer.segment(sas, itgs, Point.arrival("esn-classifier"),
                      Point.completion("esn-dispatcher"), since).getMean())
              .put("trace_iops_stage_ms", tracer.segment(sas, itgs, Point.arrival(iopsModule(focalUnit)),
                      Point.completion(iopsModule(focalUnit)), since).getMean())
              .put("trace_dropped", tracer.getDrops().values().stream().mapToLong(Long::longValue).sum());
    }

    private void collectBattery() {
        for (SensorEnergyReport.Entry e : SensorEnergyReport.collect(sensors, CloudSim.clock())) {
            if (e.sensor.equals("sas-" + focalZone)) {
                result.put("battery_tx", e.transmissions)
                      .put("battery_tx_j", e.transmitJ)
                      .put("battery_idle_j", e.idleJ)
                      .put("battery_remaining", e.remainingFraction)
                      .put("battery_exhausted_ms", e.exhaustedAtMs)
                      .put("battery_lifetime_ms", e.projectedLifetimeMs);
            }
        }
    }

    private void collectRoutingKernel() {
        long mi = routingWorkload.cpuLength(null, 1);
        double effectiveMi = acceleration == null ? mi : acceleration.effectiveLength(mi, ESN_MIPS);
        result.put("road_nodes", road.nodeCount())
              .put("road_edges", road.roadCount())
              .put("router_cpu_mi", mi)
              .put("router_cpu_ms", mi / (double) ESN_MIPS)
              .put("accel", acceleration == null ? "none" : opt.require("accel"))
              .put("router_effective_ms", effectiveMi / ESN_MIPS);
        if (monitor != null) {
            collectModule("esn", "esn-router", "router");
        }
        if (cache != null) {
            long hits = cache.getHits();
            long misses = cache.getMisses();
            result.put("cache_hits", hits)
                  .put("cache_misses", misses)
                  .put("cache_hit_ratio", hits + misses == 0 ? 0.0 : hits / (double) (hits + misses))
                  .put("cache_entries", LruCacheBehaviour.size(esn().getModuleState("esn-router")))
                  .put("checkpoints", esn().getCheckpointsTaken())
                  .put("checkpoint_bytes", esn().getCheckpointBytes());
        }
    }

    private void collectModule(String device, String module, String prefix) {
        ModuleExecutionStats s = monitor.get(device, module);
        result.put(prefix + "_n", s == null ? 0 : s.getCompleted())
              .put(prefix + "_sojourn_ms", s == null ? null : s.getMeanSojournMs())
              .put(prefix + "_nominal_ms", s == null ? null : s.getMeanNominalMs())
              .put(prefix + "_excess_ms", s == null ? null : s.getMeanExcessMs())
              .put(prefix + "_max_excess_ms", s == null ? null : s.getMaxExcessMs())
              .put(prefix + "_max_in_progress", s == null ? 0 : s.getMaxInProgress());
    }
}
