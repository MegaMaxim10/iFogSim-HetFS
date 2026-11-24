package org.fog.hetfs.hardware;

/**
 * How an accelerator changes the cost of one module (module D2).
 *
 * <p>iFogSim expresses every computation as a length in MI executed at the MIPS
 * rating of the device, so the only native way to represent an accelerator is to
 * raise the MIPS of the whole device, which speeds up every module it hosts. An
 * {@code AccelerationProfile} applies to a single module and follows Amdahl's law:
 * a fraction {@code f} of the work is accelerated by a factor {@code p}, the rest
 * runs at the device's own speed, and every invocation pays a fixed overhead
 * {@code o} (data transfer to and from the accelerator, kernel launch, reconfiguration):
 * <pre>
 *   L' = L ((1 - f) + f / p) + o * MIPS
 * </pre>
 * where {@code L} is the module's length in MI for this tuple and {@code L'} the
 * length actually executed. With {@code f = 1} and {@code o = 0} this is the constant
 * divisor used in the companion paper.
 *
 * <p>The resulting length is executed by the iFogSim engine like any other, so the
 * accelerated module shares the device and contends with the other tuples, which a
 * divisor applied after the simulation cannot reproduce.
 */
public final class AccelerationProfile {

    private final ComputeArchitecture architecture;
    private final double speedup;
    private final double parallelFraction;
    private final double overheadMs;

    public AccelerationProfile(ComputeArchitecture architecture, double speedup,
                               double parallelFraction, double overheadMs) {
        if (speedup < 1) {
            throw new IllegalArgumentException("speedup must be >= 1: " + speedup);
        }
        if (parallelFraction < 0 || parallelFraction > 1) {
            throw new IllegalArgumentException("parallel fraction must be in [0,1]: " + parallelFraction);
        }
        if (overheadMs < 0) {
            throw new IllegalArgumentException("overhead must be non-negative: " + overheadMs);
        }
        this.architecture = architecture;
        this.speedup = speedup;
        this.parallelFraction = parallelFraction;
        this.overheadMs = overheadMs;
    }

    /** Fully parallel kernel without overhead: the length is divided by {@code speedup}. */
    public static AccelerationProfile divisor(ComputeArchitecture architecture, double speedup) {
        return new AccelerationProfile(architecture, speedup, 1.0, 0.0);
    }

    /** Length in MI to execute on a device of {@code deviceMips} for a nominal length {@code mi}. */
    public long effectiveLength(long mi, double deviceMips) {
        double scaled = mi * ((1 - parallelFraction) + parallelFraction / speedup) + overheadMs * deviceMips;
        return Math.max(1L, Math.round(scaled));
    }

    /** Overall speedup of the module, overhead excluded (Amdahl). */
    public double amdahlSpeedup() {
        return 1.0 / ((1 - parallelFraction) + parallelFraction / speedup);
    }

    public ComputeArchitecture getArchitecture() {
        return architecture;
    }

    public double getSpeedup() {
        return speedup;
    }

    public double getParallelFraction() {
        return parallelFraction;
    }

    public double getOverheadMs() {
        return overheadMs;
    }

    @Override
    public String toString() {
        return architecture + "(p=" + speedup + ", f=" + parallelFraction + ", o=" + overheadMs + " ms)";
    }
}
