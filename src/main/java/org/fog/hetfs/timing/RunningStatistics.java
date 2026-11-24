package org.fog.hetfs.timing;

import java.util.Locale;

/**
 * Online mean and variance (Welford's algorithm), with a Student-t confidence
 * interval for the mean (module D6).
 *
 * <p>{@code TimeKeeper} keeps a running average per loop and nothing else, so iFogSim
 * results come without any measure of dispersion. Welford's update
 * <pre>
 *   m_n = m_{n-1} + (x_n - m_{n-1}) / n,   M_n = M_{n-1} + (x_n - m_{n-1})(x_n - m_n)
 * </pre>
 * is numerically stable and needs constant memory.
 */
public final class RunningStatistics {

    private long n;
    private double mean;
    private double m2;
    private double min = Double.POSITIVE_INFINITY;
    private double max = Double.NEGATIVE_INFINITY;

    public void add(double x) {
        n++;
        double delta = x - mean;
        mean += delta / n;
        m2 += delta * (x - mean);
        min = Math.min(min, x);
        max = Math.max(max, x);
    }

    public long getCount() {
        return n;
    }

    public double getMean() {
        return n == 0 ? Double.NaN : mean;
    }

    /** Sample variance (n - 1 denominator). */
    public double getVariance() {
        return n < 2 ? Double.NaN : m2 / (n - 1);
    }

    public double getStandardDeviation() {
        return Math.sqrt(getVariance());
    }

    public double getMin() {
        return n == 0 ? Double.NaN : min;
    }

    public double getMax() {
        return n == 0 ? Double.NaN : max;
    }

    /** Half-width of the two-sided 95% confidence interval of the mean (Student t, n - 1 df). */
    public double getConfidenceHalfWidth95() {
        if (n < 2) {
            return Double.NaN;
        }
        return StudentT.quantile975((int) Math.min(Integer.MAX_VALUE, n - 1)) * getStandardDeviation() / Math.sqrt(n);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "n=%d mean=%.6f sd=%.6f ci95=+/-%.6f [%.6f, %.6f]",
                n, getMean(), getStandardDeviation(), getConfidenceHalfWidth95(), getMin(), getMax());
    }
}
