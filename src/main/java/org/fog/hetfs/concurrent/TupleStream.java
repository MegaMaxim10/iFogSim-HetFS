package org.fog.hetfs.concurrent;

import org.fog.entities.Sensor;
import org.fog.application.AppTuple;

import java.util.*;

/**
 * Represents an independent stream of tuples from a single sensor or application
 * incident. Maintains queue semantics and tracks stream-specific statistics.
 */
public class TupleStream {
    private final String streamId;
    private final Sensor sourceSensor;
    private final Queue<AppTuple> tupleQueue;
    private long totalTuplesProcessed;
    private double totalServiceTime;
    private long totalQueueDepth;

    /**
     * Creates a new tuple stream with the specified ID and source sensor.
     *
     * @param streamId the unique stream identifier
     * @param sourceSensor the source sensor for this stream
     */
    public TupleStream(String streamId, Sensor sourceSensor) {
        this.streamId = streamId;
        this.sourceSensor = sourceSensor;
        this.tupleQueue = new LinkedList<>();
        this.totalTuplesProcessed = 0;
        this.totalServiceTime = 0.0;
        this.totalQueueDepth = 0;
    }

    /**
     * Gets the unique identifier for this stream.
     *
     * @return the stream ID
     */
    public String getStreamId() {
        return streamId;
    }

    /**
     * Gets the source sensor for this stream.
     *
     * @return the source Sensor
     */
    public Sensor getSourceSensor() {
        return sourceSensor;
    }

    /**
     * Adds a tuple to the stream's queue.
     *
     * @param tuple the AppTuple to enqueue
     */
    public void enqueue(AppTuple tuple) {
        tupleQueue.offer(tuple);
    }

    /**
     * Removes and returns the next tuple from the stream's queue.
     *
     * @return the next AppTuple, or null if queue is empty
     */
    public AppTuple dequeue() {
        return tupleQueue.poll();
    }

    /**
     * Peeks at the next tuple without removing it.
     *
     * @return the next AppTuple, or null if queue is empty
     */
    public AppTuple peek() {
        return tupleQueue.peek();
    }

    /**
     * Checks if the tuple queue is empty.
     *
     * @return true if the queue has no tuples
     */
    public boolean isEmpty() {
        return tupleQueue.isEmpty();
    }

    /**
     * Gets the current depth of the tuple queue.
     *
     * @return the number of tuples waiting in the queue
     */
    public int getQueueDepth() {
        return tupleQueue.size();
    }

    /**
     * Records completion of a tuple with its service time.
     *
     * @param serviceTimeMs the time spent processing the tuple in milliseconds
     */
    public void recordTupleCompletion(double serviceTimeMs) {
        totalTuplesProcessed++;
        totalServiceTime += serviceTimeMs;
        totalQueueDepth += getQueueDepth();
    }

    /**
     * Gets the total number of tuples processed by this stream.
     *
     * @return the count of processed tuples
     */
    public long getTotalTuplesProcessed() {
        return totalTuplesProcessed;
    }

    /**
     * Gets the total service time accumulated by this stream.
     *
     * @return the total service time in milliseconds
     */
    public double getTotalServiceTime() {
        return totalServiceTime;
    }

    /**
     * Gets the average service time per tuple.
     *
     * @return the average service time in milliseconds, or 0 if no tuples processed
     */
    public double getAverageServiceTime() {
        return totalTuplesProcessed > 0 ? totalServiceTime / totalTuplesProcessed : 0.0;
    }

    /**
     * Gets the accumulated queue depth (sum of depths over time).
     *
     * @return the total accumulated queue depth
     */
    public long getTotalQueueDepth() {
        return totalQueueDepth;
    }

    /**
     * Gets the average queue depth over the lifetime of the stream.
     *
     * @return the average queue depth, or 0 if no tuples processed
     */
    public double getAverageQueueDepth() {
        return totalTuplesProcessed > 0 ? (double) totalQueueDepth / totalTuplesProcessed : 0.0;
    }

    /**
     * Clears all statistics counters without removing queued tuples.
     */
    public void resetStatistics() {
        totalTuplesProcessed = 0;
        totalServiceTime = 0.0;
        totalQueueDepth = 0;
    }

    @Override
    public String toString() {
        return String.format("TupleStream(id=%s, queueDepth=%d, processed=%d, avgServiceTime=%.2fms)",
                streamId, getQueueDepth(), totalTuplesProcessed, getAverageServiceTime());
    }
}
