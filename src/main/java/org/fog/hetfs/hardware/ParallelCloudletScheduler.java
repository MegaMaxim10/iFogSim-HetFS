package org.fog.hetfs.hardware;

import org.cloudbus.cloudsim.CloudletSchedulerSpaceShared;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.*;

/**
 * A CloudletScheduler that processes multiple cloudlets in parallel up to a specified
 * parallelism degree. Extends CloudletSchedulerSpaceShared to support heterogeneous
 * hardware acceleration on FPGA, GPU, and TPU architectures.
 */
public class ParallelCloudletScheduler extends CloudletSchedulerSpaceShared {
    private int parallelismDegree;
    private Queue<ResCloudlet> waitingQueue;
    private Set<Cloudlet> runningCloudlets;

    /**
     * Creates a new parallel cloudlet scheduler with the specified parallelism degree.
     *
     * @param parallelismDegree the maximum number of cloudlets to execute in parallel
     */
    public ParallelCloudletScheduler(int parallelismDegree) {
        super();
        this.parallelismDegree = Math.max(1, parallelismDegree);
        this.waitingQueue = new LinkedList<>();
        this.runningCloudlets = new HashSet<>();
    }

    /**
     * Gets the parallelism degree of this scheduler.
     *
     * @return the number of parallel execution slots
     */
    public int getParallelismDegree() {
        return parallelismDegree;
    }

    /**
     * Gets the number of cloudlets currently running in parallel.
     *
     * @return the count of running cloudlets
     */
    public int getRunningCount() {
        return runningCloudlets.size();
    }

    /**
     * Gets the number of cloudlets waiting in the queue.
     *
     * @return the count of waiting cloudlets
     */
    public int getWaitingCount() {
        return waitingQueue.size();
    }

    /**
     * Determines if a slot is available for parallel execution.
     *
     * @return true if fewer than parallelismDegree cloudlets are running
     */
    public boolean isSlotAvailable() {
        return runningCloudlets.size() < parallelismDegree;
    }

    @Override
    public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
        double timeToNextEvent = Double.MAX_VALUE;

        for (ResCloudlet rcl : getCloudletExecList()) {
            rcl.updateCloudletFinishedSoFar((long) (mipsShare.get(0) * CloudSim.getMinTimeBetweenEvents()));

            if (rcl.getRemainingCloudletLength() == 0.0) {
                timeToNextEvent = 0;
            }
        }

        return timeToNextEvent;
    }

    @Override
    public Cloudlet cloudletSubmit(Cloudlet cloudlet, double fileTransferTime) {
        ResCloudlet rcl = new ResCloudlet(cloudlet);
        rcl.setCloudletStatus(Cloudlet.QUEUED);

        if (isSlotAvailable()) {
            rcl.setCloudletStatus(Cloudlet.INEXEC);
            rcl.setStartExecTime(CloudSim.clock() + fileTransferTime);
            runningCloudlets.add(cloudlet);
            getCloudletExecList().add(rcl);
        } else {
            waitingQueue.offer(rcl);
        }

        return cloudlet;
    }

    @Override
    public Cloudlet cloudletFinish(ResCloudlet rcl) {
        Cloudlet cl = rcl.getCloudlet();
        runningCloudlets.remove(cl);
        cl.setCloudletStatus(Cloudlet.SUCCESS);

        if (!waitingQueue.isEmpty()) {
            ResCloudlet nextRcl = waitingQueue.poll();
            Cloudlet nextCl = nextRcl.getCloudlet();
            nextRcl.setCloudletStatus(Cloudlet.INEXEC);
            nextRcl.setStartExecTime(CloudSim.clock());
            runningCloudlets.add(nextCl);
            getCloudletExecList().add(nextRcl);
        }

        return cl;
    }

    @Override
    public String toString() {
        return String.format("ParallelCloudletScheduler(parallelism=%d, running=%d, waiting=%d)",
                parallelismDegree, runningCloudlets.size(), waitingQueue.size());
    }
}
