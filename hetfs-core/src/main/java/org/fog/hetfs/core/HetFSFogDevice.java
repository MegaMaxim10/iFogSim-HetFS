package org.fog.hetfs.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudActionTags;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Tuple;
import org.fog.hetfs.concurrent.ExecutionMonitor;
import org.fog.hetfs.hardware.HardwareProfile;
import org.fog.hetfs.hardware.WorkloadModel;
import org.fog.hetfs.stateful.ModuleBehaviour;
import org.fog.hetfs.stateful.ModuleContext;
import org.fog.hetfs.stateful.ModuleState;
import org.fog.hetfs.timing.LatencyTracer;
import org.fog.hetfs.topology.GraphRouter;
import org.fog.hetfs.topology.PhysicalGraph;
import org.fog.hetfs.topology.PhysicalLink;
import org.fog.hetfs.topology.RoutableDevice;
import org.fog.utils.FogEvents;
import org.fog.utils.Logger;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;

/**
 * The device class of iFogSim-HetFS.
 *
 * <p>A {@code HetFSFogDevice} is a {@link FogDevice} to which the optional components
 * of the library are attached. Every component is unset by default, and a device
 * without any component behaves exactly like a {@code FogDevice}: same routing, same
 * execution, same {@code TimeKeeper} instrumentation (checked by the integration tests
 * against published results). The components are:
 * <ul>
 *   <li>D1: a {@link GraphRouter}, which replaces tree routing by hop-by-hop forwarding
 *       over a {@link PhysicalGraph};</li>
 *   <li>D2: a {@link HardwareProfile} (per-module accelerators) and {@link WorkloadModel}s
 *       (input-dependent module costs);</li>
 *   <li>D3: exact completion events and an {@link ExecutionMonitor};</li>
 *   <li>D4: {@link ModuleBehaviour}s, each with a {@link ModuleState}, and periodic
 *       checkpoints;</li>
 *   <li>D5: failure and recovery ({@link #fail()}, {@link #recover()}), normally driven
 *       by a {@code TopologyEventScheduler};</li>
 *   <li>D6: a {@link LatencyTracer} recording the lineage of every traced tuple.</li>
 * </ul>
 *
 * <p>One device class with optional components, rather than one subclass per module,
 * is what makes the modules composable: Java has no multiple inheritance, and a chain
 * such as "heterogeneous device extends graph device" would force every user of D2 to
 * adopt D1.
 *
 * <p>A tuple handed to a module on this device goes through the following steps, in
 * this order: D5 drop if the device is failed; D1 resolution of the destination and
 * forwarding; D2 workload model; D4 behaviour; D2 accelerator; D6 and D3 recording.
 */
public class HetFSFogDevice extends FogDevice implements RoutableDevice {

    // D1
    private GraphRouter router;

    // D2
    private HardwareProfile hardwareProfile;
    private final Map<String, WorkloadModel> workloads = new HashMap<>();

    // D3
    private boolean exactCompletionEvents;
    private ExecutionMonitor executionMonitor;

    // D4
    private final Map<String, ModuleBehaviour> behaviours = new HashMap<>();
    private final Map<String, ModuleState> states = new HashMap<>();
    private final Map<String, byte[]> checkpoints = new HashMap<>();
    private double checkpointIntervalMs;
    private long checkpointsTaken;
    private long checkpointBytes;

    // D5
    private boolean failed;
    private long failures;
    private double failedSince;
    private double downtimeMs;

    // D6
    private LatencyTracer tracer;

    // Statistics
    private long tuplesForwarded;
    private long tuplesDropped;
    private final Map<DropReason, Long> drops = new EnumMap<>(DropReason.class);

    /**
     * Same parameters as the corresponding {@link FogDevice} constructor. Most users
     * create devices with {@link DeviceBuilder} instead.
     *
     * @throws Exception as thrown by the {@code FogDevice} constructor
     */
    public HetFSFogDevice(String name, FogDeviceCharacteristics characteristics,
                          VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList,
                          double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth,
                          double uplinkLatency, double ratePerMips) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval,
                uplinkBandwidth, downlinkBandwidth, uplinkLatency, ratePerMips);
    }

    // ==================================================================
    // Configuration
    // ==================================================================

    /**
     * D1. Attaches the device to a graph router. The device must be a node of the
     * router's graph; it is registered with the router.
     */
    public void setRouter(GraphRouter router) {
        this.router = router;
        if (router != null) {
            router.register(this);
        }
    }

    public GraphRouter getRouter() {
        return router;
    }

    /** D2. Declares the accelerators available on this device. */
    public void setHardwareProfile(HardwareProfile profile) {
        this.hardwareProfile = profile;
    }

    public HardwareProfile getHardwareProfile() {
        return hardwareProfile;
    }

    /**
     * D2. Replaces the fixed edge length of {@code moduleName} by an input-dependent
     * cost; {@code null} restores the edge length.
     */
    public void setWorkload(String moduleName, WorkloadModel model) {
        if (model == null) {
            workloads.remove(moduleName);
        } else {
            workloads.put(moduleName, model);
        }
    }

    public WorkloadModel getWorkload(String moduleName) {
        return workloads.get(moduleName);
    }

    /**
     * D3. When enabled, the completion of every executing tuple is detected at its
     * exact finish time.
     *
     * <p>iFogSim inherits from CloudSim's {@code PowerDatacenter} a processing update
     * that, each time a tuple is submitted to a device on which another tuple is still
     * executing, cancels every pending {@code VM_DATACENTER_EVENT} (including the
     * finish event of the running tuple) and schedules a single new one
     * {@code schedulingInterval} later. The completion of the running tuple is then
     * detected up to one scheduling interval late, which iFogSim reports as latency.
     * With this option the device schedules, after every processing update and every
     * submission, an event at the earliest estimated finish time of the tuples it is
     * executing.
     */
    public void setExactCompletionEvents(boolean exact) {
        this.exactCompletionEvents = exact;
    }

    public boolean isExactCompletionEvents() {
        return exactCompletionEvents;
    }

    /** D3. Attaches a per-module execution monitor, which may be shared by several devices. */
    public void setExecutionMonitor(ExecutionMonitor monitor) {
        this.executionMonitor = monitor;
    }

    public ExecutionMonitor getExecutionMonitor() {
        return executionMonitor;
    }

    /** D4. Attaches a stateful behaviour to the instance of {@code moduleName} on this device. */
    public void setBehaviour(String moduleName, ModuleBehaviour behaviour) {
        behaviours.put(moduleName, behaviour);
        states.putIfAbsent(moduleName, new ModuleState());
    }

    public ModuleBehaviour getBehaviour(String moduleName) {
        return behaviours.get(moduleName);
    }

    /** D4. State of the stateful module {@code moduleName} on this device, or {@code null}. */
    public ModuleState getModuleState(String moduleName) {
        return states.get(moduleName);
    }

    /**
     * D4. Snapshots the state of every stateful module every {@code intervalMs} of
     * simulated time (0 disables checkpointing). The snapshots are what a failed
     * device recovers (D5). Must be set before the simulation starts.
     */
    public void setCheckpointInterval(double intervalMs) {
        if (intervalMs < 0) {
            throw new IllegalArgumentException("checkpoint interval must be non-negative: " + intervalMs);
        }
        this.checkpointIntervalMs = intervalMs;
    }

    public double getCheckpointInterval() {
        return checkpointIntervalMs;
    }

    /** D6. Attaches a lineage tracer, normally shared with the sensors and actuators. */
    public void setTracer(LatencyTracer tracer) {
        this.tracer = tracer;
    }

    public LatencyTracer getTracer() {
        return tracer;
    }

    // ==================================================================
    // RoutableDevice
    // ==================================================================

    @Override
    public boolean hostsModule(String appId, String moduleName) {
        List<String> modules = appToModulesMap.get(appId);
        return modules != null && modules.contains(moduleName);
    }

    @Override
    public boolean hostsModuleInstance(int vmId) {
        for (Vm vm : moduleInstances()) {
            if (vm.getId() == vmId) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasActuatorOfType(String actuatorType) {
        for (Pair<Integer, Double> association : getAssociatedActuatorIds()) {
            Object entity = CloudSim.getEntity(association.getFirst());
            if (entity instanceof Actuator && actuatorType.equals(((Actuator) entity).getActuatorType())) {
                return true;
            }
        }
        return false;
    }

    // ==================================================================
    // Event processing
    // ==================================================================

    @Override
    public void startEntity() {
        super.startEntity();
        if (checkpointIntervalMs > 0 && !behaviours.isEmpty()) {
            send(getId(), checkpointIntervalMs, HetFSEvents.CHECKPOINT);
        }
    }

    @Override
    protected void processOtherEvent(SimEvent ev) {
        if (ev.getTag() == HetFSEvents.LINK_FREE) {
            onLinkFree((PhysicalLink) ev.getData());
        } else if (ev.getTag() == HetFSEvents.CHECKPOINT) {
            if (!failed) {
                checkpoint();
            }
            send(getId(), checkpointIntervalMs, HetFSEvents.CHECKPOINT);
        } else {
            super.processOtherEvent(ev);
        }
    }

    @Override
    protected void processTupleArrival(SimEvent ev) {
        Tuple tuple = (Tuple) ev.getData();
        if (failed) {
            drop(tuple, DropReason.DEVICE_FAILED);
            return;
        }
        if (router != null) {
            int target = router.resolveTarget(this, tuple);
            if (target == GraphRouter.UNPLACED) {
                drop(tuple, DropReason.UNPLACED_DESTINATION);
                return;
            }
            if (target == GraphRouter.UNREACHABLE) {
                drop(tuple, DropReason.UNREACHABLE_DESTINATION);
                return;
            }
            if (target != getId()) {
                send(ev.getSourceId(), CloudSim.getMinTimeBetweenEvents(), FogEvents.TUPLE_ACK);
                forward(tuple, target);
                return;
            }
        }
        if (willExecuteLocally(tuple)) {
            prepareLocalExecution(tuple, tuple.getDestModuleName());
        }
        super.processTupleArrival(ev);
    }

    @Override
    protected void processModuleArrival(SimEvent ev) {
        super.processModuleArrival(ev);
        AppModule module = (AppModule) ev.getData();
        ModuleBehaviour behaviour = behaviours.get(module.getName());
        if (behaviour != null) {
            behaviour.onInit(context(module.getName()));
        }
    }

    @Override
    protected void executeTuple(SimEvent ev, String moduleName) {
        super.executeTuple(ev, moduleName);
        if (exactCompletionEvents) {
            scheduleNextCompletion();
        }
    }

    @Override
    protected void updateCloudletProcessing() {
        super.updateCloudletProcessing();
        if (exactCompletionEvents) {
            scheduleNextCompletion();
        }
    }

    /**
     * Copy of {@code FogDevice#checkCloudletCompletion}, kept line for line, with a call
     * to {@link #onTupleCompleted} for every completed tuple once its resultant tuples
     * are known and before they are sent.
     */
    @Override
    protected void checkCloudletCompletion() {
        boolean cloudletCompleted = false;
        List<? extends Host> list = getVmAllocationPolicy().getHostList();
        for (int i = 0; i < list.size(); i++) {
            Host host = list.get(i);
            for (Vm vm : host.<Vm>getGuestList()) {
                while (vm.getCloudletScheduler().isFinishedCloudlets()) {
                    Cloudlet cl = vm.getCloudletScheduler().getNextFinishedCloudlet();
                    if (cl != null) {
                        cloudletCompleted = true;
                        Tuple tuple = (Tuple) cl;
                        TimeKeeper.getInstance().tupleEndedExecution(tuple);
                        Application application = getApplicationMap().get(tuple.getAppId());
                        Logger.debug(getName(), "Completed execution of tuple " + tuple.getCloudletId()
                                + "on " + tuple.getDestModuleName());
                        List<Tuple> resultantTuples = application.getResultantTuples(
                                tuple.getDestModuleName(), tuple, getId(), vm.getId());
                        if (onTupleCompleted(tuple, (AppModule) vm, resultantTuples)) {
                            for (Tuple resTuple : resultantTuples) {
                                resTuple.setModuleCopyMap(new HashMap<String, Integer>(tuple.getModuleCopyMap()));
                                resTuple.getModuleCopyMap().put(((AppModule) vm).getName(), vm.getId());
                                updateTimingsOnSending(resTuple);
                                sendToSelf(resTuple);
                            }
                        }
                        sendNow(cl.getUserId(), CloudActionTags.CLOUDLET_RETURN, cl);
                    }
                }
            }
        }
        if (cloudletCompleted) {
            updateAllocatedMips(null);
        }
    }

    // ==================================================================
    // Execution of a tuple on a local module
    // ==================================================================

    /** Mirrors the test performed by {@code FogDevice#processTupleArrival} before executing a tuple. */
    protected boolean willExecuteLocally(Tuple tuple) {
        if (tuple.getDirection() == Tuple.ACTUATOR || tuple.getDestModuleName() == null
                || !hostsModule(tuple.getAppId(), tuple.getDestModuleName())) {
            return false;
        }
        int vmId = -1;
        for (Vm vm : moduleInstances()) {
            if (((AppModule) vm).getName().equals(tuple.getDestModuleName())) {
                vmId = vm.getId();
            }
        }
        Integer pinned = tuple.getModuleCopyMap().get(tuple.getDestModuleName());
        return vmId >= 0 && (pinned == null || pinned == vmId);
    }

    /**
     * Called just before a tuple that will be executed on this device is handed over to
     * iFogSim: sets the length to execute (D2, D4) and records the arrival (D6, D3).
     * Subclasses overriding it must call this implementation.
     */
    protected void prepareLocalExecution(Tuple tuple, String moduleName) {
        long length = baseLength(tuple, moduleName);
        boolean bypassAccelerator = false;
        ModuleBehaviour behaviour = behaviours.get(moduleName);
        if (behaviour != null) {
            ModuleContext ctx = context(moduleName);
            length = behaviour.onTupleArrival(tuple, length, ctx);
            bypassAccelerator = ctx.consumeBypassAcceleration();
        }
        if (hardwareProfile != null && !bypassAccelerator) {
            length = hardwareProfile.effectiveLength(moduleName, length, getHost().getTotalMips());
        }
        if (length != tuple.getCloudletLength()) {
            tuple.setCloudletLength(Math.max(1L, length));
        }
        double now = CloudSim.clock();
        if (tracer != null) {
            tracer.onModuleArrival(tuple, moduleName, now);
        }
        if (executionMonitor != null) {
            double nominalMs = tuple.getCloudletLength() / getHost().getTotalMips();
            executionMonitor.onArrival(getName(), moduleName, tuple, now, nominalMs);
        }
    }

    /** Length (MI) of the tuple before D4 and D2 acceleration: the edge length or the workload model's. */
    protected long baseLength(Tuple tuple, String moduleName) {
        WorkloadModel model = workloads.get(moduleName);
        return model == null ? tuple.getCloudletLength() : model.cpuLength(tuple, tuple.getCloudletLength());
    }

    /**
     * Called when {@code tuple} has finished executing on {@code module}, with the tuples
     * it produced. Subclasses overriding it must call this implementation.
     *
     * @return {@code false} to discard the resultant tuples instead of sending them
     */
    protected boolean onTupleCompleted(Tuple tuple, AppModule module, List<Tuple> resultantTuples) {
        double now = CloudSim.clock();
        if (executionMonitor != null) {
            executionMonitor.onCompletion(tuple, now);
        }
        if (tracer != null) {
            tracer.onModuleCompletion(tuple, resultantTuples, now);
        }
        if (failed) {
            // the tuple was executing when the device failed: its results are lost
            for (Tuple lost : resultantTuples) {
                drop(lost, DropReason.DEVICE_FAILED);
            }
            return false;
        }
        ModuleBehaviour behaviour = behaviours.get(module.getName());
        if (behaviour != null) {
            behaviour.onTupleCompleted(tuple, resultantTuples, context(module.getName()));
        }
        return true;
    }

    private ModuleContext context(String moduleName) {
        return new ModuleContext(getName(), getId(), moduleName, states.get(moduleName));
    }

    private List<Vm> moduleInstances() {
        return getHost().getGuestList();
    }

    // ==================================================================
    // D1: hop-by-hop forwarding over the physical graph
    // ==================================================================

    private void forward(Tuple tuple, int target) {
        PhysicalGraph graph = router.getGraph();
        int next = router.nextHop(getId(), target);
        PhysicalLink link = next < 0 ? null : graph.getLink(getId(), next);
        if (link == null || !graph.isUsable(link)) {
            drop(tuple, DropReason.UNREACHABLE_DESTINATION);
            return;
        }
        tuplesForwarded++;
        if (link.isBusy()) {
            link.getQueue().add(tuple);
        } else {
            transmit(link, tuple);
        }
    }

    private void transmit(PhysicalLink link, Tuple tuple) {
        link.countTransmission();
        double serialisation = link.serialisationDelay(tuple.getCloudletFileSize());
        link.setBusy(true);
        send(getId(), serialisation, HetFSEvents.LINK_FREE, link);
        send(link.getDestinationId(), serialisation + link.getLatencyMs(), FogEvents.TUPLE_ARRIVAL, tuple);
        NetworkUsageMonitor.sendingTuple(link.getLatencyMs(), tuple.getCloudletFileSize());
    }

    private void onLinkFree(PhysicalLink link) {
        if (failed || !link.isUp() || link.getQueue().isEmpty()) {
            link.setBusy(false);
            return;
        }
        transmit(link, link.getQueue().poll());
    }

    // ==================================================================
    // D3: exact completion events
    // ==================================================================

    private void scheduleNextCompletion() {
        double now = CloudSim.clock();
        // With a zero time span this call only refreshes the estimates; it returns the
        // absolute time of the earliest expected completion (0 or MAX_VALUE if idle).
        double next = getHost().updateCloudletsProcessing(now);
        if (next > now && next < Double.MAX_VALUE) {
            send(getId(), next - now, CloudActionTags.VM_DATACENTER_EVENT);
        }
    }

    // ==================================================================
    // D4: checkpoints
    // ==================================================================

    /** Takes a snapshot of every stateful module now. */
    public void checkpoint() {
        for (Map.Entry<String, ModuleState> entry : states.entrySet()) {
            byte[] snapshot = entry.getValue().snapshot();
            checkpoints.put(entry.getKey(), snapshot);
            checkpointBytes += snapshot.length;
            checkpointsTaken++;
        }
    }

    public long getCheckpointsTaken() {
        return checkpointsTaken;
    }

    /** Total size of all the snapshots taken so far, in bytes. */
    public long getCheckpointBytes() {
        return checkpointBytes;
    }

    private void restoreFromCheckpoints() {
        for (Map.Entry<String, ModuleState> entry : states.entrySet()) {
            byte[] snapshot = checkpoints.get(entry.getKey());
            if (snapshot != null) {
                entry.getValue().restore(snapshot);
            } else {
                entry.getValue().clear();
            }
            ModuleBehaviour behaviour = behaviours.get(entry.getKey());
            if (behaviour != null) {
                behaviour.onRestore(context(entry.getKey()), snapshot != null);
            }
        }
    }

    // ==================================================================
    // D5: failure and recovery
    // ==================================================================

    /**
     * Fails the device: tuples that arrive are dropped, tuples that were executing
     * produce no output, tuples queued on its outgoing links are dropped, the state of
     * its stateful modules is lost, and routes over the physical graph avoid it.
     * Failing a failed device has no effect.
     */
    public void fail() {
        if (failed) {
            return;
        }
        failed = true;
        failures++;
        failedSince = CloudSim.clock();
        for (ModuleState state : states.values()) {
            state.clear();
        }
        if (router != null) {
            for (PhysicalLink link : router.getGraph().getOutgoingLinks(getId())) {
                while (!link.getQueue().isEmpty()) {
                    drop(link.getQueue().poll(), DropReason.DEVICE_FAILED);
                }
                link.setBusy(false);
            }
            router.getGraph().setDeviceUp(getId(), false);
        }
    }

    /**
     * Recovers the device; stateful modules restart from their last checkpoint, or
     * empty if none was taken. Recovering a working device has no effect.
     */
    public void recover() {
        if (!failed) {
            return;
        }
        failed = false;
        downtimeMs += CloudSim.clock() - failedSince;
        restoreFromCheckpoints();
        if (router != null) {
            router.getGraph().setDeviceUp(getId(), true);
        }
    }

    public boolean isFailed() {
        return failed;
    }

    public long getFailures() {
        return failures;
    }

    /** Total time spent failed, up to the last recovery. */
    public double getDowntimeMs() {
        return downtimeMs;
    }

    // ==================================================================
    // Drops and statistics
    // ==================================================================

    private void drop(Tuple tuple, DropReason reason) {
        tuplesDropped++;
        drops.merge(reason, 1L, Long::sum);
        if (tracer != null) {
            tracer.onDrop(tuple, reason.toString());
        }
    }

    /** Number of tuples this device has put on a link of the physical graph (D1). */
    public long getTuplesForwarded() {
        return tuplesForwarded;
    }

    /** Number of tuples this device has discarded, all reasons together. */
    public long getTuplesDropped() {
        return tuplesDropped;
    }

    /** Number of discarded tuples by reason. */
    public Map<DropReason, Long> getDrops() {
        return Collections.unmodifiableMap(new EnumMap<>(drops));
    }

    /** Names of the modules placed on this device for application {@code appId}. */
    public List<String> getPlacedModules(String appId) {
        List<String> modules = appToModulesMap.get(appId);
        return modules == null ? new ArrayList<String>() : new ArrayList<>(modules);
    }
}
