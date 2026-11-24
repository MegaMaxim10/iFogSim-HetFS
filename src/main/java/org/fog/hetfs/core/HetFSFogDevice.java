package org.fog.hetfs.core;

import java.util.ArrayList;
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
import org.fog.hetfs.topology.GraphRouter;
import org.fog.hetfs.topology.PhysicalGraph;
import org.fog.hetfs.topology.PhysicalLink;
import org.fog.hetfs.topology.RoutableDevice;
import org.fog.utils.FogEvents;
import org.fog.utils.Logger;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;

/**
 * The single device class of iFogSim-HetFS.
 *
 * <p>A {@code HetFSFogDevice} is a {@link FogDevice} to which the optional components
 * of the library are attached. Each component is {@code null} by default, and a
 * device without any component behaves exactly like a {@code FogDevice}: same
 * routing, same execution, same {@code TimeKeeper} instrumentation (this is checked
 * by the test suite on the companion case study). The components are:
 * <ul>
 *   <li>D1 - a {@link GraphRouter}, which replaces tree routing by hop-by-hop
 *       forwarding over a {@link PhysicalGraph};</li>
 *   <li>D2 - a {@link HardwareProfile} (per-module accelerators) and
 *       {@link WorkloadModel}s (input-dependent module costs);</li>
 *   <li>D4 - {@link ModuleBehaviour}s with a {@link ModuleState} per module instance,
 *       and periodic checkpoints;</li>
 *   <li>D3 - exact completion events ({@link #setExactCompletionEvents(boolean)}) and
 *       an {@link ExecutionMonitor};</li>
 * </ul>
 * Components of the other modules are attached in the same way (see the
 * corresponding setters).
 *
 * <p>Using one device class with optional components, rather than one subclass per
 * module, is what makes the modules composable: Java has no multiple inheritance, and
 * a chain such as {@code HeterogeneousFogDevice extends GraphFogDevice} would force
 * every user of D2 to adopt D1.
 */
public class HetFSFogDevice extends FogDevice implements RoutableDevice {

    // D1
    private GraphRouter router;

    // D2
    private HardwareProfile hardwareProfile;
    private final Map<String, WorkloadModel> workloads = new HashMap<>();

    // D4
    private final Map<String, ModuleBehaviour> behaviours = new HashMap<>();
    private final Map<String, ModuleState> states = new HashMap<>();
    private final Map<String, byte[]> checkpoints = new HashMap<>();
    private double checkpointIntervalMs;
    private long checkpointsTaken;
    private long checkpointBytes;

    // D3
    private boolean exactCompletionEvents;
    private ExecutionMonitor executionMonitor;

    // counters
    private long tuplesForwarded;
    private long tuplesDropped;
    private final Map<String, Long> dropReasons = new HashMap<>();

    public HetFSFogDevice(String name, FogDeviceCharacteristics characteristics,
                          VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList,
                          double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth,
                          double uplinkLatency, double ratePerMips) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval,
                uplinkBandwidth, downlinkBandwidth, uplinkLatency, ratePerMips);
    }

    // ==================================================================
    // Component wiring
    // ==================================================================

    /**
     * Attaches the device to a graph router (D1). The device must be a node of the
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

    /** D2. Replaces the fixed edge length of {@code moduleName} by an input-dependent cost. */
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

    /** D4. Attaches a stateful behaviour to the instance of {@code moduleName} on this device. */
    public void setBehaviour(String moduleName, ModuleBehaviour behaviour) {
        behaviours.put(moduleName, behaviour);
        states.putIfAbsent(moduleName, new ModuleState());
    }

    public ModuleBehaviour getBehaviour(String moduleName) {
        return behaviours.get(moduleName);
    }

    /** D4. State of the stateful module {@code moduleName} on this device, or null. */
    public ModuleState getModuleState(String moduleName) {
        return states.get(moduleName);
    }

    /**
     * D4. Snapshots the state of every stateful module every {@code intervalMs} of
     * simulated time (0 disables checkpointing). The snapshots are what a failed device
     * recovers (module D5).
     */
    public void setCheckpointInterval(double intervalMs) {
        this.checkpointIntervalMs = intervalMs;
    }

    public double getCheckpointInterval() {
        return checkpointIntervalMs;
    }

    public long getCheckpointsTaken() {
        return checkpointsTaken;
    }

    /** Total size of all snapshots taken so far, in bytes. */
    public long getCheckpointBytes() {
        return checkpointBytes;
    }

    private ModuleContext context(String moduleName) {
        return new ModuleContext(getName(), getId(), moduleName, states.get(moduleName));
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

    /** D3. Attaches a per-module execution monitor (may be shared by several devices). */
    public void setExecutionMonitor(ExecutionMonitor monitor) {
        this.executionMonitor = monitor;
    }

    public ExecutionMonitor getExecutionMonitor() {
        return executionMonitor;
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
        for (Vm vm : getHost().getVmList()) {
            if (vm.getId() == vmId) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasActuatorOfType(String actuatorType) {
        for (Pair<Integer, Double> a : getAssociatedActuatorIds()) {
            Object e = CloudSim.getEntity(a.getFirst());
            if (e instanceof Actuator && actuatorType.equals(((Actuator) e).getActuatorType())) {
                return true;
            }
        }
        return false;
    }

    // ==================================================================
    // Event handling
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
            checkpoint();
            send(getId(), checkpointIntervalMs, HetFSEvents.CHECKPOINT);
        } else {
            super.processOtherEvent(ev);
        }
    }

    @Override
    protected void processTupleArrival(SimEvent ev) {
        Tuple tuple = (Tuple) ev.getData();
        if (router != null) {
            int target = router.resolveTarget(this, tuple);
            if (target == GraphRouter.UNPLACED) {
                drop(tuple, "destination module not placed on any device");
                return;
            }
            if (target == GraphRouter.UNREACHABLE) {
                drop(tuple, "no usable path to destination");
                return;
            }
            if (target != getId()) {
                send(ev.getSource(), CloudSim.getMinTimeBetweenEvents(), FogEvents.TUPLE_ACK);
                forward(tuple, target);
                return;
            }
        }
        if (willExecuteLocally(tuple)) {
            prepareLocalExecution(tuple, tuple.getDestModuleName());
        }
        super.processTupleArrival(ev);
    }

    /** Mirrors the test performed by {@code FogDevice#processTupleArrival} before executing a tuple. */
    protected boolean willExecuteLocally(Tuple tuple) {
        if (tuple.getDirection() == Tuple.ACTUATOR || tuple.getDestModuleName() == null
                || !hostsModule(tuple.getAppId(), tuple.getDestModuleName())) {
            return false;
        }
        int vmId = -1;
        for (Vm vm : getHost().getVmList()) {
            if (((AppModule) vm).getName().equals(tuple.getDestModuleName())) {
                vmId = vm.getId();
            }
        }
        Integer pinned = tuple.getModuleCopyMap().get(tuple.getDestModuleName());
        return vmId >= 0 && (pinned == null || pinned == vmId);
    }

    /**
     * Called just before a tuple that will be executed on this device is handed over to
     * iFogSim. Subclasses and the other modules use it to adjust the tuple's
     * computational length and to record its arrival.
     */
    protected void prepareLocalExecution(Tuple tuple, String moduleName) {
        long length = baseLength(tuple, moduleName);
        boolean bypass = false;
        ModuleBehaviour behaviour = behaviours.get(moduleName);
        if (behaviour != null) {
            ModuleContext ctx = context(moduleName);
            length = behaviour.onTupleArrival(tuple, length, ctx);
            bypass = ctx.consumeBypassAcceleration();
        }
        if (hardwareProfile != null && !bypass) {
            length = hardwareProfile.effectiveLength(moduleName, length, getHost().getTotalMips());
        }
        if (length != tuple.getCloudletLength()) {
            tuple.setCloudletLength(Math.max(1L, length));
        }
        if (executionMonitor != null) {
            double nominalMs = tuple.getCloudletLength() / (double) getHost().getTotalMips();
            executionMonitor.onArrival(getName(), moduleName, tuple, CloudSim.clock(), nominalMs);
        }
    }

    /** Length (MI) of the tuple before acceleration: the edge length or the workload model's. */
    protected long baseLength(Tuple tuple, String moduleName) {
        WorkloadModel model = workloads.get(moduleName);
        return model == null ? tuple.getCloudletLength() : model.cpuLength(tuple, tuple.getCloudletLength());
    }

    // ==================================================================
    // D3: exact completion events
    // ==================================================================

    @Override
    protected void updateCloudletProcessing() {
        super.updateCloudletProcessing();
        if (exactCompletionEvents) {
            scheduleNextCompletion();
        }
    }

    @Override
    protected void executeTuple(SimEvent ev, String moduleName) {
        super.executeTuple(ev, moduleName);
        if (exactCompletionEvents) {
            scheduleNextCompletion();
        }
    }

    private void scheduleNextCompletion() {
        double now = CloudSim.clock();
        // With a zero time span this call only refreshes the estimates; it returns the
        // absolute time of the earliest expected completion (0 or MAX_VALUE if idle).
        double next = getHost().updateCloudletsProcessing(now);
        if (next > now && next < Double.MAX_VALUE) {
            send(getId(), next - now, CloudActionTags.VM_DATACENTER_EVENT);
        }
    }

    /**
     * Same as {@code FogDevice#checkCloudletCompletion}, with a hook invoked for every
     * completed tuple once its resultant tuples are known and before they are sent.
     */
    @Override
    protected void checkCloudletCompletion() {
        boolean cloudletCompleted = false;
        List<? extends Host> list = getVmAllocationPolicy().getHostList();
        for (int i = 0; i < list.size(); i++) {
            Host host = list.get(i);
            for (Vm vm : host.getVmList()) {
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
                        boolean emit = onTupleCompleted(tuple, (AppModule) vm, resultantTuples);
                        if (emit) {
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

    /**
     * Hook invoked when {@code tuple} has finished executing on {@code module}.
     *
     * @return {@code false} to discard the resultant tuples instead of sending them
     */
    protected boolean onTupleCompleted(Tuple tuple, AppModule module, List<Tuple> resultantTuples) {
        if (executionMonitor != null) {
            executionMonitor.onCompletion(tuple, CloudSim.clock());
        }
        ModuleBehaviour behaviour = behaviours.get(module.getName());
        if (behaviour != null) {
            behaviour.onTupleCompleted(tuple, resultantTuples, context(module.getName()));
        }
        return true;
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

    /** D4. Takes a snapshot of every stateful module now. */
    public void checkpoint() {
        for (Map.Entry<String, ModuleState> e : states.entrySet()) {
            byte[] snap = e.getValue().snapshot();
            checkpoints.put(e.getKey(), snap);
            checkpointBytes += snap.length;
            checkpointsTaken++;
        }
    }

    /** Discards the in-memory state of every stateful module (the device has failed). */
    protected void loseVolatileState() {
        for (ModuleState st : states.values()) {
            st.clear();
        }
    }

    /** Restores every stateful module from its last checkpoint (empty if none). */
    protected void restoreFromCheckpoints() {
        for (Map.Entry<String, ModuleState> e : states.entrySet()) {
            byte[] snap = checkpoints.get(e.getKey());
            if (snap != null) {
                e.getValue().restore(snap);
            } else {
                e.getValue().clear();
            }
            ModuleBehaviour b = behaviours.get(e.getKey());
            if (b != null) {
                b.onRestore(context(e.getKey()), snap != null);
            }
        }
    }

    // ==================================================================
    // D1: hop-by-hop forwarding over the physical graph
    // ==================================================================

    private void forward(Tuple tuple, int target) {
        int next = router.nextHop(getId(), target);
        PhysicalLink link = next < 0 ? null : router.getGraph().getLink(getId(), next);
        if (link == null || !router.getGraph().isUsable(link)) {
            drop(tuple, "no usable path to destination");
            return;
        }
        tuplesForwarded++;
        onForward(tuple, link);
        if (link.isBusy()) {
            link.getQueue().add(tuple);
        } else {
            transmit(link, tuple);
        }
    }

    /** Hook invoked when a tuple is put on a link (queued or transmitted). */
    protected void onForward(Tuple tuple, PhysicalLink link) {
    }

    private void transmit(PhysicalLink link, Tuple tuple) {
        double serialisation = link.serialisationDelay(tuple.getCloudletFileSize());
        link.setBusy(true);
        send(getId(), serialisation, HetFSEvents.LINK_FREE, link);
        send(link.getDestinationId(), serialisation + link.getLatencyMs(), FogEvents.TUPLE_ARRIVAL, tuple);
        NetworkUsageMonitor.sendingTuple(link.getLatencyMs(), tuple.getCloudletFileSize());
    }

    private void onLinkFree(PhysicalLink link) {
        if (!link.isUp() || link.getQueue().isEmpty()) {
            link.setBusy(false);
            return;
        }
        transmit(link, link.getQueue().poll());
    }

    // ==================================================================
    // Drops
    // ==================================================================

    protected void drop(Tuple tuple, String reason) {
        tuplesDropped++;
        dropReasons.merge(reason, 1L, Long::sum);
        onDrop(tuple, reason);
    }

    /** Hook invoked for every tuple this device discards. */
    protected void onDrop(Tuple tuple, String reason) {
    }

    public long getTuplesForwarded() {
        return tuplesForwarded;
    }

    public long getTuplesDropped() {
        return tuplesDropped;
    }

    public Map<String, Long> getDropReasons() {
        return new HashMap<>(dropReasons);
    }

    /** Names of the modules placed on this device for {@code appId}. */
    public List<String> getPlacedModules(String appId) {
        List<String> m = appToModulesMap.get(appId);
        return m == null ? new ArrayList<String>() : new ArrayList<>(m);
    }
}
