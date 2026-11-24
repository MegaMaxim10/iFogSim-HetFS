package org.fog.hetfs.concurrent;

/**
 * Execution statistics of one module instance on one device (module D3).
 *
 * <p>For every tuple the monitor records its <em>sojourn</em> on the module (from the
 * moment it is handed to the module to the moment its completion is detected by the
 * engine) and its <em>nominal service time</em>, i.e. its length in MI divided by the
 * device MIPS, which is the time it would take alone on the device. The difference,
 * the <em>excess</em>, is the delay caused by sharing the processor with the other
 * tuples in progress, plus any delay in detecting the completion.
 */
public class ModuleExecutionStats {

    private final String device;
    private final String module;
    private long completed;
    private double sumSojourn;
    private double sumNominal;
    private double sumExcess;
    private double maxExcess;
    private int inProgress;
    private int maxInProgress;
    private long arrivalsWhileBusy;

    ModuleExecutionStats(String device, String module) {
        this.device = device;
        this.module = module;
    }

    void arrival() {
        if (inProgress > 0) {
            arrivalsWhileBusy++;
        }
        inProgress++;
        maxInProgress = Math.max(maxInProgress, inProgress);
    }

    void completion(double sojourn, double nominal) {
        inProgress = Math.max(0, inProgress - 1);
        completed++;
        sumSojourn += sojourn;
        sumNominal += nominal;
        double excess = Math.max(0, sojourn - nominal);
        sumExcess += excess;
        maxExcess = Math.max(maxExcess, excess);
    }

    public String getDevice() {
        return device;
    }

    public String getModule() {
        return module;
    }

    public long getCompleted() {
        return completed;
    }

    public double getMeanSojournMs() {
        return completed == 0 ? 0 : sumSojourn / completed;
    }

    public double getMeanNominalMs() {
        return completed == 0 ? 0 : sumNominal / completed;
    }

    /** Mean delay beyond the nominal service time. */
    public double getMeanExcessMs() {
        return completed == 0 ? 0 : sumExcess / completed;
    }

    public double getMaxExcessMs() {
        return maxExcess;
    }

    /** Largest number of tuples executing simultaneously on this module. */
    public int getMaxInProgress() {
        return maxInProgress;
    }

    /** Number of tuples that arrived while at least one other tuple was executing. */
    public long getArrivalsWhileBusy() {
        return arrivalsWhileBusy;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT,
                "%s/%s: n=%d sojourn=%.4f nominal=%.4f excess=%.4f (max %.4f) maxInProgress=%d",
                device, module, completed, getMeanSojournMs(), getMeanNominalMs(), getMeanExcessMs(),
                maxExcess, maxInProgress);
    }
}
