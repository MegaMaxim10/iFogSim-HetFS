package org.fog.hetfs.hardware;

/**
 * Defines the hardware characteristics of a fog device, including compute architecture,
 * parallelism degree, clock speed, and power consumption model.
 */
public class HardwareProfile {
    private ComputeArchitecture architecture;
    private int parallelismDegree;
    private double clockSpeedGHz;
    private double powerIdleWatts;
    private double powerPeakWatts;

    /**
     * Creates a new hardware profile with specified characteristics.
     *
     * @param architecture the compute architecture (CPU, FPGA, GPU, TPU)
     * @param parallelismDegree the number of parallel execution units
     * @param clockSpeedGHz the clock speed in GHz
     * @param powerIdleWatts the idle power consumption in Watts
     * @param powerPeakWatts the peak power consumption in Watts
     */
    public HardwareProfile(ComputeArchitecture architecture, int parallelismDegree,
                          double clockSpeedGHz, double powerIdleWatts, double powerPeakWatts) {
        this.architecture = architecture;
        this.parallelismDegree = Math.max(1, parallelismDegree);
        this.clockSpeedGHz = clockSpeedGHz;
        this.powerIdleWatts = powerIdleWatts;
        this.powerPeakWatts = powerPeakWatts;
    }

    /**
     * Gets the compute architecture of this profile.
     *
     * @return the ComputeArchitecture
     */
    public ComputeArchitecture getArchitecture() {
        return architecture;
    }

    /**
     * Gets the parallelism degree of this profile.
     *
     * @return the number of parallel execution units
     */
    public int getParallelismDegree() {
        return parallelismDegree;
    }

    /**
     * Gets the clock speed in GHz.
     *
     * @return the clock speed in GHz
     */
    public double getClockSpeedGHz() {
        return clockSpeedGHz;
    }

    /**
     * Gets the idle power consumption in Watts.
     *
     * @return the idle power in Watts
     */
    public double getPowerIdleWatts() {
        return powerIdleWatts;
    }

    /**
     * Gets the peak power consumption in Watts.
     *
     * @return the peak power in Watts
     */
    public double getPowerPeakWatts() {
        return powerPeakWatts;
    }

    /**
     * Sets the clock speed in GHz.
     *
     * @param clockSpeedGHz the new clock speed in GHz
     */
    public void setClockSpeedGHz(double clockSpeedGHz) {
        this.clockSpeedGHz = clockSpeedGHz;
    }

    /**
     * Sets the idle power consumption in Watts.
     *
     * @param powerIdleWatts the new idle power in Watts
     */
    public void setPowerIdleWatts(double powerIdleWatts) {
        this.powerIdleWatts = powerIdleWatts;
    }

    /**
     * Sets the peak power consumption in Watts.
     *
     * @param powerPeakWatts the new peak power in Watts
     */
    public void setPowerPeakWatts(double powerPeakWatts) {
        this.powerPeakWatts = powerPeakWatts;
    }

    @Override
    public String toString() {
        return String.format("HardwareProfile(arch=%s, parallelism=%d, clock=%.2fGHz, " +
                "idle=%.2fW, peak=%.2fW)", architecture, parallelismDegree, clockSpeedGHz,
                powerIdleWatts, powerPeakWatts);
    }
}
