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
import org.fog.hetfs.core.DeviceBuilder;
import org.fog.hetfs.core.HetFSController;
import org.fog.hetfs.core.HetFSFogDevice;
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
        CloudSim.init(1, Calendar.getInstance(), false);
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
                Sensor sas = createSensor("sas-" + zone, "INCIDENT_" + zone, userId);
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

    FogDevice device(DeviceBuilder b) {
        if (opt.containsKey("scheduling-interval")) {
            b.schedulingInterval(Double.parseDouble(opt.get("scheduling-interval")));
        }
        FogDevice d = hetfs ? b.build() : b.buildVanilla();
        devices.add(d);
        byName.put(d.getName(), d);
        return d;
    }

    Sensor createSensor(String name, String tupleType, int userId) {
        return new Sensor(name, tupleType, userId, APP_ID, new DeterministicDistribution(ACTIVE_INTERVAL));
    }

    Actuator createActuator(String name, int userId) {
        return new Actuator(name, userId, APP_ID, "RESPONSE_UNIT");
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

    @SuppressWarnings("serial")
    Application createApplication(int userId) {
        Application app = Application.createApplication(APP_ID, userId);
        app.addAppModule("esn-classifier", 512);
        app.addAppModule("esn-dispatcher", 512);

        for (String zone : activeZones) {
            String module = "cad-" + zone + "-module";
            String incident = "INCIDENT_" + zone;
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
        final String focalIncident = "INCIDENT_" + focalZone;
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
        app.addAppEdge("esn-classifier", "esn-dispatcher", 3000, TUPLE_BYTES, "CLASSIFIED_MSG", Tuple.UP, AppEdge.MODULE);
        app.addTupleMapping("esn-classifier", "ALERT_MSG", "CLASSIFIED_MSG", new FractionalSelectivity(1.0));
    }

    String dispatchInputType() {
        return "CLASSIFIED_MSG";
    }

    /** Extension point for the module options; nothing to do in the base scenario. */
    void configureModules(Application app) {
    }

    /** Extension point for options that need the controller (e.g. fault injection). */
    void configureAfterController(HetFSController controller) {
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
        return v.toString();
    }
}
