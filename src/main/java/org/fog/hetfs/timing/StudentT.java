package org.fog.hetfs.timing;

/**
 * Two-sided 95% critical values of Student's t distribution (module D6). The normal
 * value 1.96 is only correct for large samples; with the five replications commonly
 * used in simulation studies the correct value is 2.776, which widens the interval
 * by 42%.
 */
public final class StudentT {

    private static final double[] Q975 = {
        Double.NaN, 12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
        2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
        2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042};

    private StudentT() {
    }

    /** 0.975 quantile of t with {@code df} degrees of freedom. */
    public static double quantile975(int df) {
        if (df < 1) {
            throw new IllegalArgumentException("degrees of freedom must be positive: " + df);
        }
        if (df < Q975.length) {
            return Q975[df];
        }
        if (df <= 40) {
            return 2.021;
        }
        if (df <= 60) {
            return 2.000;
        }
        if (df <= 120) {
            return 1.980;
        }
        return 1.960;
    }
}
