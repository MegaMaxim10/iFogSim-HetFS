package org.fog.hetfs.concurrent;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.entities.Sensor;
import org.fog.application.AppTuple;

import java.util.*;

/**
 * Manages multiple concurrent tuple streams, interleaving them into the CloudSim
 * event queue while tracking per-stream queue depth, service time, and contention.
 * Emits ConcurrencyStats at the end of simulation.
 */
public class StreamDispatcher extends SimEntity {
    private Map<String, TupleStream> streams;
    private double totalLinkBusyTime;
    private double totalSimulationTime;
    private long totalTuplesDispatched;
    private Queue<AppTuple> dispatchQueue;
    private boolean linkBusy;

    private static final int DISPATCH_EVENT = 1001;
    private static final int STATS_EVENT = 1002;

    /**
     * Creates a new stream dispatcher with the specified name.
     *
     * @param name the dispatcher entity name
     */
    public StreamDispatcher(String name) {
        super(name);
        this.streams = new HashMap<>();
        this.dispatchQueue = new LinkedList<>();
        this.totalLinkBusyTime = 0.0;
        this.totalSimulationTime = 0.0;
        this.totalTuplesDispatched = 0;
        this.linkBusy = false;
    }

    /**
     * Creates or retrieves a tuple stream for the given sensor.
     *
     * @param streamId the unique stream identifier
     * @param sensor the source sensor for the stream
     * @return the TupleStream, creating one if necessary
     */
    public TupleStream getOrCreateStream(String streamId, Sensor sensor) {
        return streams.computeIfAbsent(streamId, id -> new TupleStream(id, sensor));
    }

    /**
     * Submits a tuple for dispatch via a specific stream.
     *
     * @param streamId the target stream ID
     * @param tuple the AppTuple to dispatch
     */
    public void submitTuple(String streamId, AppTuple tuple) {
        TupleStream stream = streams.get(streamId);
        if (stream != null) {
            stream.enqueue(tuple);
            dispatchQueue.offer(tuple);
        }
    }

    /**
     * Gets a registered stream by ID.
     *
     * @param streamId the stream identifier
     * @return the TupleStream, or null if not found
     */
    public TupleStream getStream(String streamId) {
        return streams.get(streamId);
    }

    /**
     * Gets all registered streams.
     *
     * @return a map of all streams keyed by stream ID
     */
    public Map<String, TupleStream> getAllStreams() {
        return new HashMap<>(streams);
    }

    /**
     * Marks the network link as busy for the specified duration.
     *
     * @param durationMs the duration the link is busy in milliseconds
     */
    public void markLinkBusy(double durationMs) {
        if (!linkBusy) {
            linkBusy = true;
        }
        totalLinkBusyTime += durationMs;
    }

    /**
     * Computes the contention ratio based on link busy time.
     *
     * @return the contention ratio between 0 and 1
     */
    public double getContentionRatio() {
        return totalSimulationTime > 0 ? totalLinkBusyTime / totalSimulationTime : 0.0;
    }

    /**
     * Computes aggregate concurrency statistics from all streams.
     *
     * @return a ConcurrencyStats object with aggregated metrics
     */
    public ConcurrencyStats computeStats() {
        ConcurrencyStats stats = new ConcurrencyStats();

        double sumQueueDepth = 0.0;
        double maxQueueDepth = 0.0;
        long totalTuples = 0;

        for (TupleStream stream : streams.values()) {
            sumQueueDepth += stream.getAverageQueueDepth();
            maxQueueDepth = Math.max(maxQueueDepth, stream.getQueueDepth());
            totalTuples += stream.getTotalTuplesProcessed();
        }

        double avgQueueDepth = !streams.isEmpty() ? sumQueueDepth / streams.size() : 0.0;
        double throughput = totalSimulationTime > 0 ? (totalTuplesDispatched / totalSimulationTime) * 1000 : 0.0;

        stats.setAvgQueueDepth(avgQueueDepth);
        stats.setMaxQueueDepth(maxQueueDepth);
        stats.setContentionRatio(getContentionRatio());
        stats.setThroughputTuplesSec(throughput);
        stats.setTotalTuplesProcessed(totalTuplesDispatched);
        stats.setSimulationDurationMs(totalSimulationTime);

        return stats;
    }

    /**
     * Gets the current depth of the dispatch queue.
     *
     * @return the number of tuples waiting to be dispatched
     */
    public int getDispatchQueueDepth() {
        return dispatchQueue.size();
    }

    /**
     * Gets the total number of tuples dispatched.
     *
     * @return the count of dispatched tuples
     */
    public long getTotalTuplesDispatched() {
        return totalTuplesDispatched;
    }

    @Override
    public void startEntity() {
        schedule(getId(), 0.0, DISPATCH_EVENT);
    }

    @Override
    public void shutdownEntity() {
        streams.clear();
        dispatchQueue.clear();
    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case DISPATCH_EVENT:
                processDispatchEvent();
                if (!dispatchQueue.isEmpty()) {
                    schedule(getId(), 1.0, DISPATCH_EVENT);
                }
                break;
            case STATS_EVENT:
                processStatsEvent();
                break;
            default:
                break;
        }
    }

    /**
     * Processes tuple dispatch by dequeuing and potentially scheduling.
     */
    private void processDispatchEvent() {
        if (!dispatchQueue.isEmpty()) {
            AppTuple tuple = dispatchQueue.poll();
            if (tuple != null) {
                totalTuplesDispatched++;
            }
        }
    }

    /**
     * Processes end-of-simulation stats collection.
     */
    private void processStatsEvent() {
        totalSimulationTime = CloudSim.clock();
    }

    /**
     * Schedules the final statistics computation at the end of simulation.
     *
     * @param simulationEndTime the time at which simulation ends
     */
    public void scheduleStatistics(double simulationEndTime) {
        schedule(getId(), simulationEndTime, STATS_EVENT);
    }

    @Override
    public String toString() {
        return String.format("StreamDispatcher(streams=%d, dispatched=%d, queueDepth=%d)",
                streams.size(), totalTuplesDispatched, dispatchQueue.size());
    }
}
