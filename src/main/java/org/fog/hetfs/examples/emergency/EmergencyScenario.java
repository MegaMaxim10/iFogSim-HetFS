package org.fog.hetfs.examples.emergency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
import org.fog.hetfs.energy.BatteryModel;
import org.fog.hetfs.energy.SensorEnergyReport;
import org.fog.hetfs.events.FaultInjector;
import org.fog.hetfs.events.TopologyEventScheduler;
import org.fog.hetfs.hardware.AccelerationProfile;
import org.fog.hetfs.hardware.ComplexityWorkload;
import org.fog.hetfs.hardware.ComputeArchitecture;
import org.fog.hetfs.hardware.HardwareProfile;
import org.fog.hetfs.stateful.LruCacheBehaviour;
import org.fog.hetfs.stateful.ModuleState;
import org.fog.hetfs.core.HetFSController;
import org.fog.hetfs.core.HetFSActuator;
import org.fog.hetfs.core.HetFSFogDevice;
import org.fog.hetfs.core.HetFSSensor;
import org.fog.hetfs.timing.LatencyTracer;
import org.fog.hetfs.validation.ApplicationValidator;
import org.fog.hetfs.validation.Finding;
import org.fog.hetfs.timing.RunningStatistics;
import org.fog.hetfs.topology.GraphRouter;
import org.fog.hetfs.topology.PhysicalGraph;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementMapping;
import org.fog.utils.Config;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * The smart emergency response architecture of the companion paper, built so that
 * every iFogSim-HetFS module can be switched on individually from the command line.
 *
 * <p>With {@code --devices vanilla} and no module option the scenario is identical to
 * {@code SmartEmergencyResponseSimulation} of the companion reproducibility package:
 * same device tree (cloud, ESN, five CAD zone devices, eight IOPS unit devices), same
 * application graph, same placement, same {@code AppLoop}s, and the same seeded
 * &plusmn;2&nbsp;% Gaussian jitter on the four propagation latencies, drawn in the
 * same order. It therefore reproduces the companion results bit for bit, which the
 * test suite checks.
 *
 * <p>Options (all {@code --name value}):
 * <pre>
 *   --zones z1,z2        zones whose SAS sensor fires (default z1)
 *   --focal-zone z1      zone tracked by the alert AppLoop (default: first zone)
 *   --focal-unit c1      unit tracked by the dispatch AppLoop (default c1)
 *   --seed 1             jitter seed
 *   --devices hetfs      vanilla | hetfs
 *   --graph none         none | tree | mesh   (D1; hetfs devices only)
 *   --exact-completion   detect tuple completions at their exact time (D3)
 *   --monitor            per-module execution monitor; adds the columns of the
 *                        ESN modules and of the focal IOPS module (D3)
 *   --scheduling-interval 10   PowerDatacenter scheduling interval of every device
 *   --min-time-between-events 0.01   CloudSim's event-time resolution (ms)
 *   --routing-kernel     model the Dijkstra unit ranking as a module (esn-router) on the
 *                        ESN, between the classifier and the dispatcher, whose cost
 *                        follows (V+E) log2 V on the road network (D2)
 *   --road-nodes 25      size of the road network (25, or more with relay junctions)
 *   --mi-fixed a --mi-per-op c   Dijkstra workload a + c (V+E) log2 V in MI
 *   --accel fpga:10[:f[:o]]   accelerator for esn-router on the ESN: architecture,
 *                        kernel speedup p, parallel fraction f (1), overhead o in ms (0)
 *   --cache n[:hitMi[:ttlMs]]  LRU path cache of n zones on esn-router, keyed by the
 *                        incident zone; a hit costs hitMi (default 8) MI (D4)
 *   --checkpoint-interval ms   checkpoint period of the ESN's stateful modules (D4)
 *   --fail-device esn@500-800  device outage between 500 and 800 ms (D5); repeatable
 *                        with ';' (e.g. "esn@500-800;cad-z2@100-200")
 *   --fail-link cad-z1~esn@300-900   outage of a link, both directions (D5, needs --graph)
 *   --battery C:tx[:idle]  battery of C joules for every SAS, tx joules per transmission
 *                        and idle W (D7); implies HetFS sensors
 *   --sensor-interval ms  SAS emission period (default 10)
 *   --sim-time ms        simulated duration (iFogSim default 2000)
 *   --shared-incident-type   all SAS sensors emit the same tuple type INCIDENT (the
 *                        configuration the companion paper had to avoid, C8)
 *   --validate           run ApplicationValidator and report its findings
 *   --hetfs-sensors      use HetFSSensor (deterministic edge resolution) for the SAS
 *   --trace              lineage tracing (D6): HetFS sensors and actuators, and the
 *                        end-to-end and full-boundary latencies of the focal branch
 *   --out file.csv       CSV file to which one result row is appended
 *   --label exp --run 1  free-text identifiers copied into the row
 * </pre>
 * In {@code mesh} mode the CAD devices are additionally connected in a ring by
 * metropolitan links of {@value #LAT_CAD_RING} ms.
 */
public class EmergencyScenario {

    static final long ESN_MIPS = 10000;
    static final int ESN_RAM = 8192;
    static final long CAD_MIPS = 2000;
    static final int CAD_RAM = 1024;
    static final long IOPS_MIPS = 1500;
    static final int IOPS_RAM = 512;

    static final double LAT_SAS_TO_CAD = 5.0;
    static final double LAT_CAD_TO_ESN = 100.0;
    static final double LAT_ESN_TO_IOPS = 80.0;
    static final double LAT_IOPS_TO_ITGS = 15.0;
    static final double LAT_ESN_TO_CLOUD = 50.0;
    static final double LAT_CAD_RING = 20.0;

    static final double ACTIVE_INTERVAL = 10.0;
    /** Default Dijkstra workload; the experiments pass calibrated values explicitly. */
    static final double DEFAULT_MI_FIXED = 0.0;
    static final double DEFAULT_MI_PER_OP = 0.05;
    static final long TUPLE_BYTES = 256;
    static final long LINK_BANDWIDTH = 10_000_000;

    public static final String APP_ID = "smart_emergency_response";

    // ------------------------------------------------------------------
    final Map<String, String> opt;
    final Set<String> activeZones;
    final String focalZone;
    final String focalUnit;
    final long seed;
    final boolean hetfs;
    final String graphMode;

    final List<FogDevice> devices = new ArrayList<>();
    final List<Sensor> sensors = new ArrayList<>();
    final List<Actuator> actuators = new ArrayList<>();
    final Map<String, FogDevice> byName = new LinkedHashMap<>();
    final Map<String, String> pins = new LinkedHashMap<>();
    double latSasToCad, latCadToEsn, latEsnToIops, latIopsToItgs;
    int loopAlertId = -1, loopDispatchId = -1;
    PhysicalGraph graph;
    GraphRouter router;
    ExecutionMonitor monitor;
    LatencyTracer tracer;
    long wallStart;

    /** Result columns, in output order. */
    final Map<String, Object> result = new LinkedHashMap<>();

    EmergencyScenario(Map<String, String> opt) {
        this.opt = opt;
        this.activeZones = new LinkedHashSet<>(Arrays.asList(opt.getOrDefault("zones", "z1").split(",")));
        this.focalZone = opt.getOrDefault("focal-zone", activeZones.iterator().next());
        this.focalUnit = opt.getOrDefault("focal-unit", "c1");
        this.seed = Long.parseLong(opt.getOrDefault("seed", "1"));
        this.hetfs = !"vanilla".equals(opt.getOrDefault("devices", "hetfs"));
        this.graphMode = opt.getOrDefault("graph", "none");
        if (!activeZones.contains(focalZone)) {
            throw new IllegalArgumentException("focal zone must be active: " + focalZone);
        }
        if (!hetfs && !"none".equals(graphMode)) {
            throw new IllegalArgumentException("--graph requires --devices hetfs");
        }
    }

    public static void main(String[] args) throws Exception {
        new EmergencyScenario(parse(args)).run();
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument " + args[i]);
            }
            String key = args[i].substring(2);
            String value = i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "true";
            m.put(key, value);
        }
        return m;
    }

    void run() throws Exception {
        Random rnd = new Random(seed);
        latSasToCad = LAT_SAS_TO_CAD * (1.0 + rnd.nextGaussian() * 0.02);
        latCadToEsn = LAT_CAD_TO_ESN * (1.0 + rnd.nextGaussian() * 0.02);
        latEsnToIops = LAT_ESN_TO_IOPS * (1.0 + rnd.nextGaussian() * 0.02);
        latIopsToItgs = LAT_IOPS_TO_ITGS * (1.0 + rnd.nextGaussian() * 0.02);

        Log.disable();
        if (opt.containsKey("sim-time")) {
            Config.MAX_SIMULATION_TIME = Integer.parseInt(opt.get("sim-time"));
        }
        if (opt.containsKey("min-time-between-events")) {
            CloudSim.init(1, Calendar.getInstance(), false,
                    Double.parseDouble(opt.get("min-time-between-events")));
        } else {
            CloudSim.init(1, Calendar.getInstance(), false);
        }
        FogBroker broker = new FogBroker("broker");
        Application app = createApplication(broker.getId());
        app.setUserId(broker.getId());
        createDevices(broker.getId());
        configureModules(app);

        ModuleMapping mapping = ModuleMapping.createModuleMapping();
        mapping.addModuleToDevice("esn-classifier", "esn");
        mapping.addModuleToDevice("esn-dispatcher", "esn");
        for (Map.Entry<String, String> pin : pins.entrySet()) {
            mapping.addModuleToDevice(pin.getKey(), pin.getValue());
        }

        if (opt.containsKey("validate")) {
            List<Finding> findings = ApplicationValidator.validate(app, sensors, ApplicationValidator.Placement.MAPPING, mapping);
            int errors = 0;
            StringBuilder codes = new StringBuilder();
            for (Finding f : findings) {
                System.err.println(f);
                if (f.getSeverity() == Finding.Severity.ERROR) {
                    errors++;
                }
                if (codes.indexOf(f.getCode()) < 0) {
                    codes.append(codes.length() == 0 ? "" : ";").append(f.getCode());
                }
            }
            validationErrors = errors;
            validationCodes = codes.toString();
        }

        HetFSController controller = new HetFSController("master-controller", devices, sensors, actuators);
        controller.setExitOnStop(false);
        controller.submitApplication(app, 0, new ModulePlacementMapping(devices, app, mapping));
        controller.addEndListener(this::collect);
        configureAfterController(controller);

        TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
        wallStart = System.nanoTime();
        CloudSim.startSimulation();
        write();
    }

    // ==================================================================
    // Devices
    // ==================================================================

    void createDevices(int userId) {
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
                    sas.setDestModuleName("cad-" + zone + "-module");
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

    boolean tracing() {
        return opt.containsKey("trace");
    }

    LatencyTracer tracer() {
        if (tracer == null) {
            tracer = new LatencyTracer();
        }
        return tracer;
    }

    FogDevice device(DeviceBuilder b) {
        if (opt.containsKey("scheduling-interval")) {
            b.schedulingInterval(Double.parseDouble(opt.get("scheduling-interval")));
        }
        FogDevice d = hetfs ? b.build() : b.buildVanilla();
        if (hetfs) {
            HetFSFogDevice h = (HetFSFogDevice) d;
            h.setExactCompletionEvents(opt.containsKey("exact-completion"));
            if (tracing()) {
                h.setTracer(tracer());
            }
            if (opt.containsKey("monitor")) {
                if (monitor == null) {
                    monitor = new ExecutionMonitor();
                }
                h.setExecutionMonitor(monitor);
            }
        } else if (opt.containsKey("exact-completion") || opt.containsKey("monitor") || tracing()) {
            throw new IllegalArgumentException("--exact-completion, --monitor and --trace require --devices hetfs");
        }
        devices.add(d);
        byName.put(d.getName(), d);
        return d;
    }

    Sensor createSensor(String name, String tupleType, int userId) {
        double interval = Double.parseDouble(opt.getOrDefault("sensor-interval", "" + ACTIVE_INTERVAL));
        if (!tracing() && !opt.containsKey("battery") && !opt.containsKey("hetfs-sensors")) {
            return new Sensor(name, tupleType, userId, APP_ID, new DeterministicDistribution(interval));
        }
        HetFSSensor s = new HetFSSensor(name, tupleType, userId, APP_ID, new DeterministicDistribution(interval));
        if (tracing()) {
            s.setTracer(tracer());
        }
        if (opt.containsKey("battery")) {
            String[] f = opt.get("battery").split(":");
            s.setBattery(new BatteryModel(Double.parseDouble(f[0]), Double.parseDouble(f[1]), 0,
                    f.length > 2 ? Double.parseDouble(f[2]) : 0));
        }
        return s;
    }

    Actuator createActuator(String name, int userId) {
        if (!tracing()) {
            return new Actuator(name, userId, APP_ID, "RESPONSE_UNIT");
        }
        HetFSActuator a = new HetFSActuator(name, userId, APP_ID, "RESPONSE_UNIT");
        a.setTracer(tracer());
        return a;
    }

    void buildGraph() {
        graph = PhysicalGraph.fromTree(devices);
        if ("mesh".equals(graphMode)) {
            String[] z = RoadNetwork.ZONES;
            for (int i = 0; i < z.length; i++) {
                int a = byName.get("cad-" + z[i]).getId();
                int b = byName.get("cad-" + z[(i + 1) % z.length]).getId();
                graph.addBidirectionalLink(a, b, LAT_CAD_RING, LINK_BANDWIDTH);
            }
        } else if (!"tree".equals(graphMode)) {
            throw new IllegalArgumentException("unknown --graph " + graphMode);
        }
        router = new GraphRouter(graph);
        for (FogDevice d : devices) {
            ((HetFSFogDevice) d).setRouter(router);
        }
    }

    // ==================================================================
    // Application (identical to the companion implementation)
    // ==================================================================

    String incidentType(String zone) {
        return opt.containsKey("shared-incident-type") ? "INCIDENT" : "INCIDENT_" + zone;
    }

    boolean routingKernel() {
        return opt.containsKey("routing-kernel");
    }

    @SuppressWarnings("serial")
    Application createApplication(int userId) {
        Application app = Application.createApplication(APP_ID, userId);
        app.addAppModule("esn-classifier", 512);
        app.addAppModule("esn-dispatcher", 512);

        for (String zone : activeZones) {
            String module = "cad-" + zone + "-module";
            String incident = incidentType(zone);
            app.addAppModule(module, 256);
            app.addAppEdge(incident, module, 500, TUPLE_BYTES, incident, Tuple.UP, AppEdge.SENSOR);
            app.addAppEdge(module, "esn-classifier", 2000, TUPLE_BYTES, "ALERT_MSG", Tuple.UP, AppEdge.MODULE);
            app.addTupleMapping(module, incident, "ALERT_MSG", new FractionalSelectivity(1.0));
            pins.put(module, "cad-" + zone);
        }
        addClassifierToDispatcher(app);

        for (String unit : RoadNetwork.UNITS) {
            String module = "iops-" + unit + "-module";
            String dispatch = "DISPATCH_" + unit;
            String guidance = "GUIDANCE_" + unit;
            app.addAppModule(module, 256);
            app.addAppEdge("esn-dispatcher", module, 1000, TUPLE_BYTES, dispatch, Tuple.DOWN, AppEdge.MODULE);
            app.addAppEdge(module, "RESPONSE_UNIT", 100, TUPLE_BYTES, guidance, Tuple.DOWN, AppEdge.ACTUATOR);
            app.addTupleMapping("esn-dispatcher", dispatchInputType(), dispatch, new FractionalSelectivity(1.0));
            app.addTupleMapping(module, dispatch, guidance, new FractionalSelectivity(1.0));
            pins.put(module, "iops-" + unit);
        }

        final String focalCad = "cad-" + focalZone + "-module";
        final String focalIncident = incidentType(focalZone);
        final AppLoop loopAlert = new AppLoop(new ArrayList<String>() {{
            add(focalIncident);
            add(focalCad);
            add("esn-classifier");
        }});
        final AppLoop loopDispatch = new AppLoop(new ArrayList<String>() {{
            add("esn-dispatcher");
            add("iops-" + focalUnit + "-module");
            add("RESPONSE_UNIT");
        }});
        app.setLoops(new ArrayList<AppLoop>() {{
            add(loopAlert);
            add(loopDispatch);
        }});
        loopAlertId = loopAlert.getLoopId();
        loopDispatchId = loopDispatch.getLoopId();
        return app;
    }

    /** The classifier-to-dispatcher stage; overridden when the routing kernel is modelled. */
    void addClassifierToDispatcher(Application app) {
        if (!routingKernel()) {
            app.addAppEdge("esn-classifier", "esn-dispatcher", 3000, TUPLE_BYTES, "CLASSIFIED_MSG", Tuple.UP, AppEdge.MODULE);
            app.addTupleMapping("esn-classifier", "ALERT_MSG", "CLASSIFIED_MSG", new FractionalSelectivity(1.0));
            return;
        }
        // The edge length of ROUTE_REQUEST is a placeholder: the workload model of
        // esn-router replaces it by the Dijkstra cost on the road network.
        app.addAppModule("esn-router", 512);
        app.addAppEdge("esn-classifier", "esn-router", 1, TUPLE_BYTES, "ROUTE_REQUEST", Tuple.UP, AppEdge.MODULE);
        app.addTupleMapping("esn-classifier", "ALERT_MSG", "ROUTE_REQUEST", new FractionalSelectivity(1.0));
        app.addAppEdge("esn-router", "esn-dispatcher", 3000, TUPLE_BYTES, "ROUTE_RESULT", Tuple.UP, AppEdge.MODULE);
        app.addTupleMapping("esn-router", "ROUTE_REQUEST", "ROUTE_RESULT", new FractionalSelectivity(1.0));
        pins.put("esn-router", "esn");
    }

    String dispatchInputType() {
        return routingKernel() ? "ROUTE_RESULT" : "CLASSIFIED_MSG";
    }

    RoadNetwork road;
    ComplexityWorkload routingWorkload;
    AccelerationProfile acceleration;
    boolean cache;

    void configureModules(Application app) {
        if (routingKernel()) {
            if (!hetfs) {
                throw new IllegalArgumentException("--routing-kernel requires --devices hetfs");
            }
            int nodes = Integer.parseInt(opt.getOrDefault("road-nodes", "25"));
            road = nodes == 25 ? RoadNetwork.base() : RoadNetwork.scaled(nodes, 7);
            double a = Double.parseDouble(opt.getOrDefault("mi-fixed", "" + DEFAULT_MI_FIXED));
            double c = Double.parseDouble(opt.getOrDefault("mi-per-op", "" + DEFAULT_MI_PER_OP));
            routingWorkload = new ComplexityWorkload(ComplexityWorkload.Complexity.DIJKSTRA_BINARY_HEAP, a, c,
                    road.nodeCount(), road.roadCount());
            HetFSFogDevice esn = (HetFSFogDevice) byName.get("esn");
            esn.setWorkload("esn-router", routingWorkload);
            if (opt.containsKey("accel")) {
                acceleration = parseAcceleration(opt.get("accel"));
                esn.setHardwareProfile(HardwareProfile.cpu().accelerate("esn-router", acceleration));
            }
            if (opt.containsKey("cache")) {
                String[] f = opt.get("cache").split(":");
                int capacity = Integer.parseInt(f[0]);
                long hitMi = f.length > 1 ? Long.parseLong(f[1]) : 8;
                double ttl = f.length > 2 ? Double.parseDouble(f[2]) : Double.POSITIVE_INFINITY;
                esn.setBehaviour("esn-router", new LruCacheBehaviour(EmergencyScenario::incidentZone, capacity, hitMi, ttl));
                cache = true;
            }
            if (opt.containsKey("checkpoint-interval")) {
                esn.setCheckpointInterval(Double.parseDouble(opt.get("checkpoint-interval")));
            }
        } else if (opt.containsKey("accel") || opt.containsKey("cache")) {
            throw new IllegalArgumentException("--accel and --cache require --routing-kernel");
        }
    }

    /** Incident zone a tuple descends from, read from the CAD module it went through. */
    static String incidentZone(Tuple t) {
        for (String m : t.getModuleCopyMap().keySet()) {
            if (m.startsWith("cad-") && m.endsWith("-module")) {
                return m.substring(4, m.length() - 7);
            }
        }
        return null;
    }

    static AccelerationProfile parseAcceleration(String spec) {
        String[] f = spec.split(":");
        ComputeArchitecture arch = ComputeArchitecture.valueOf(f[0].toUpperCase(Locale.ROOT));
        double p = Double.parseDouble(f[1]);
        double frac = f.length > 2 ? Double.parseDouble(f[2]) : 1.0;
        double o = f.length > 3 ? Double.parseDouble(f[3]) : 0.0;
        return new AccelerationProfile(arch, p, frac, o);
    }

    TopologyEventScheduler faults;
    int validationErrors = -1;
    String validationCodes = "";

    void configureAfterController(HetFSController controller) {
        if (!opt.containsKey("fail-device") && !opt.containsKey("fail-link")) {
            return;
        }
        if (!hetfs) {
            throw new IllegalArgumentException("fault injection requires --devices hetfs");
        }
        faults = new TopologyEventScheduler("fault-injector", graph);
        FaultInjector inj = FaultInjector.on(faults);
        for (String spec : opt.getOrDefault("fail-device", "").split(";")) {
            if (spec.isEmpty()) {
                continue;
            }
            String[] p = spec.split("@");
            double[] w = window(p[1]);
            HetFSFogDevice d = (HetFSFogDevice) require(p[0]);
            inj.failDevice(d, w[0]);
            if (!Double.isInfinite(w[1])) {
                inj.recoverDevice(d, w[1]);
            }
        }
        for (String spec : opt.getOrDefault("fail-link", "").split(";")) {
            if (spec.isEmpty()) {
                continue;
            }
            String[] p = spec.split("@");
            String[] ends = p[0].split("~");
            double[] w = window(p[1]);
            inj.failLink((HetFSFogDevice) require(ends[0]), (HetFSFogDevice) require(ends[1]), w[0], w[1]);
        }
    }

    FogDevice require(String name) {
        FogDevice d = byName.get(name);
        if (d == null) {
            throw new IllegalArgumentException("unknown device " + name);
        }
        return d;
    }

    static double[] window(String spec) {
        String[] f = spec.split("-");
        return new double[] {Double.parseDouble(f[0]),
            f.length > 1 ? Double.parseDouble(f[1]) : Double.POSITIVE_INFINITY};
    }

    // ==================================================================
    // Results
    // ==================================================================

    void collect() {
        double wallMs = (System.nanoTime() - wallStart) / 1e6;
        Map<Integer, Double> lat = TimeKeeper.getInstance().getLoopIdToCurrentAverage();
        Double alert = lat.get(loopAlertId);
        Double dispatch = lat.get(loopDispatchId);
        result.put("label", opt.getOrDefault("label", ""));
        result.put("run", opt.getOrDefault("run", ""));
        result.put("devices", hetfs ? "hetfs" : "vanilla");
        result.put("graph", graphMode);
        result.put("active_zones", String.join(";", activeZones));
        result.put("focal_zone", focalZone);
        result.put("focal_unit", focalUnit);
        result.put("seed", seed);
        result.put("alert_latency_ms", alert);
        result.put("dispatch_latency_ms", dispatch);
        result.put("coordination_latency_ms", (alert == null ? 0 : alert) + (dispatch == null ? 0 : dispatch));
        collectModules();
        result.put("wall_clock_ms", wallMs);
    }

    /** Extension point: adds module-specific result columns. */
    void collectModules() {
        result.put("exact_completion", opt.containsKey("exact-completion"));
        if (validationErrors >= 0) {
            result.put("validation_errors", validationErrors);
            result.put("validation_codes", validationCodes);
            result.put("focal_cad_executed", monitor == null || monitor.get("cad-" + focalZone, "cad-" + focalZone + "-module") == null
                    ? 0 : monitor.get("cad-" + focalZone, "cad-" + focalZone + "-module").getCompleted());
        }
        if (hetfs) {
            long dropped = 0;
            long forwarded = 0;
            for (FogDevice d : devices) {
                dropped += ((HetFSFogDevice) d).getTuplesDropped();
                forwarded += ((HetFSFogDevice) d).getTuplesForwarded();
            }
            result.put("tuples_forwarded", forwarded);
            result.put("tuples_dropped", dropped);
            result.put("topology_events", faults == null ? 0 : faults.getLog().size());
        }
        if (tracing()) {
            traceColumns();
        }
        if (opt.containsKey("battery")) {
            double now = CloudSim.clock();
            for (SensorEnergyReport.Entry e : SensorEnergyReport.collect(sensors, now)) {
                if (e.sensor.equals("sas-" + focalZone)) {
                    result.put("battery_tx", e.transmissions);
                    result.put("battery_tx_j", e.transmitJ);
                    result.put("battery_idle_j", e.idleJ);
                    result.put("battery_remaining", e.remainingFraction);
                    result.put("battery_exhausted_ms", e.exhaustedAtMs);
                    result.put("battery_lifetime_ms", e.projectedLifetimeMs);
                }
            }
        }
        if (routingKernel()) {
            long mi = routingWorkload.cpuLength(null, 1);
            result.put("road_nodes", road.nodeCount());
            result.put("road_edges", road.roadCount());
            result.put("router_cpu_mi", mi);
            result.put("router_cpu_ms", mi / (double) ESN_MIPS);
            result.put("accel", acceleration == null ? "none" : opt.get("accel"));
            result.put("router_effective_ms", acceleration == null ? mi / (double) ESN_MIPS
                    : acceleration.effectiveLength(mi, ESN_MIPS) / (double) ESN_MIPS);
            if (monitor != null) {
                monitorColumns("esn", "esn-router", "router");
            }
            if (cache) {
                HetFSFogDevice esn = (HetFSFogDevice) byName.get("esn");
                ModuleState st = esn.getModuleState("esn-router");
                LruCacheBehaviour lru = (LruCacheBehaviour) esn.getBehaviour("esn-router");
                long h = lru.getHits();
                long m = lru.getMisses();
                result.put("cache_hits", h);
                result.put("cache_misses", m);
                result.put("cache_hit_ratio", h + m == 0 ? 0.0 : h / (double) (h + m));
                result.put("cache_entries", LruCacheBehaviour.size(st));
                result.put("checkpoints", esn.getCheckpointsTaken());
                result.put("checkpoint_bytes", esn.getCheckpointBytes());
            }
        }
        if (monitor != null) {
            monitorColumns("esn", "esn-classifier", "classifier");
            monitorColumns("esn", "esn-dispatcher", "dispatcher");
            monitorColumns("iops-" + focalUnit, "iops-" + focalUnit + "-module", "iops");
        }
    }

    /**
     * Latencies of the focal branch (focal zone's SAS to focal unit's ITGS) measured on
     * tuple lineages, after a warm-up of {@code --trace-since} ms (default 0).
     */
    void traceColumns() {
        String sas = "sas-" + focalZone;
        String itgs = "itgs-" + focalUnit;
        double since = Double.parseDouble(opt.getOrDefault("trace-since", "0"));
        String cad = "cad-" + focalZone + "-module";
        String iops = "iops-" + focalUnit + "-module";
        LatencyTracer.Point emit = LatencyTracer.Point.emit();
        RunningStatistics e2e = tracer.segment(sas, itgs, emit, LatencyTracer.Point.actuator(), since);
        // same boundaries as the TimeKeeper loops
        RunningStatistics alertTk = tracer.segment(sas, itgs, emit, LatencyTracer.Point.arrival("esn-classifier"), since);
        RunningStatistics dispatchTk = tracer.segment(sas, itgs, LatencyTracer.Point.completion("esn-dispatcher"),
                LatencyTracer.Point.actuator(), since);
        // boundaries moved past the contended modules
        RunningStatistics alertFull = tracer.segment(sas, itgs, emit,
                LatencyTracer.Point.completion("esn-classifier"), since);
        RunningStatistics esnStage = tracer.segment(sas, itgs, LatencyTracer.Point.arrival("esn-classifier"),
                LatencyTracer.Point.completion("esn-dispatcher"), since);
        RunningStatistics iopsStage = tracer.segment(sas, itgs, LatencyTracer.Point.arrival(iops),
                LatencyTracer.Point.completion(iops), since);
        result.put("trace_emitted", tracer.emittedCount(sas));
        result.put("trace_delivered", tracer.deliveredCount(sas, itgs));
        result.put("e2e_n", e2e.getCount());
        result.put("e2e_mean_ms", e2e.getMean());
        result.put("e2e_sd_ms", e2e.getStandardDeviation());
        result.put("e2e_ci95_ms", e2e.getConfidenceHalfWidth95());
        result.put("e2e_max_ms", e2e.getMax());
        result.put("trace_alert_tk_ms", alertTk.getMean());
        result.put("trace_dispatch_tk_ms", dispatchTk.getMean());
        result.put("trace_alert_full_ms", alertFull.getMean());
        result.put("trace_esn_stage_ms", esnStage.getMean());
        result.put("trace_iops_stage_ms", iopsStage.getMean());
        long drops = 0;
        for (long d : tracer.getDrops().values()) {
            drops += d;
        }
        result.put("trace_dropped", drops);
        result.put("cad_module", cad);
    }

    void monitorColumns(String device, String module, String prefix) {
        ModuleExecutionStats s = monitor.get(device, module);
        result.put(prefix + "_n", s == null ? 0 : s.getCompleted());
        result.put(prefix + "_sojourn_ms", s == null ? null : s.getMeanSojournMs());
        result.put(prefix + "_nominal_ms", s == null ? null : s.getMeanNominalMs());
        result.put(prefix + "_excess_ms", s == null ? null : s.getMeanExcessMs());
        result.put(prefix + "_max_excess_ms", s == null ? null : s.getMaxExcessMs());
        result.put(prefix + "_max_in_progress", s == null ? 0 : s.getMaxInProgress());
    }

    void write() throws IOException {
        String out = opt.get("out");
        StringBuilder header = new StringBuilder();
        StringBuilder row = new StringBuilder();
        for (Map.Entry<String, Object> e : result.entrySet()) {
            if (header.length() > 0) {
                header.append(',');
                row.append(',');
            }
            header.append(e.getKey());
            row.append(format(e.getValue()));
        }
        if (out == null) {
            System.out.println(header);
            System.out.println(row);
            return;
        }
        Path p = Paths.get(out);
        if (p.getParent() != null) {
            Files.createDirectories(p.getParent());
        }
        if (!Files.exists(p) || Files.size(p) == 0) {
            Files.write(p, (header + "\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            String existing = Files.readAllLines(p, StandardCharsets.UTF_8).get(0);
            if (!existing.equals(header.toString())) {
                throw new IllegalStateException("CSV header mismatch in " + out + ":\n  " + existing + "\n  " + header);
            }
        }
        Files.write(p, (row + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
    }

    static String format(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof Double || v instanceof Float) {
            return String.format(Locale.ROOT, "%.6f", ((Number) v).doubleValue());
        }
        String text = v.toString();
        if (text.contains(",")) {
            throw new IllegalArgumentException("CSV value contains a comma: " + text);
        }
        return text;
    }
}
