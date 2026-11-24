package org.fog.hetfs.examples;

import org.fog.hetfs.hardware.ComputeArchitecture;
import org.fog.hetfs.hardware.HardwareProfile;
import org.fog.hetfs.hardware.HeterogeneousFogDevice;
import org.fog.hetfs.topology.GraphFogDevice;
import org.fog.hetfs.topology.PhysicalGraph;

import java.util.*;

/**
 * Example demonstrating heterogeneous hardware acceleration with FPGA, GPU, and TPU
 * architectures. Shows how different compute architectures affect execution times
 * and power consumption.
 */
public class FPGAAccelerationExample {

    public static void main(String[] args) {
        System.out.println("=== FPGA Acceleration Example ===\n");

        // Create a simple topology
        PhysicalGraph graph = new PhysicalGraph();
        graph.addDevice(0);
        graph.addDevice(1);
        graph.addDevice(2);
        graph.addDevice(3);

        // Create devices with different hardware profiles
        System.out.println("Creating heterogeneous fog devices:\n");

        // CPU device
        HardwareProfile cpuProfile = new HardwareProfile(
                ComputeArchitecture.CPU,
                1,      // No parallelism
                2.0,    // 2 GHz
                10.0,   // 10W idle
                30.0    // 30W peak
        );

        HeterogeneousFogDevice cpuDevice = new HeterogeneousFogDevice(
                "CPU_Device_0", 1000, 2048, 5000, 0.0, cpuProfile);
        cpuDevice.setPhysicalGraph(graph);

        System.out.println("CPU Device:");
        System.out.println("  " + cpuDevice.getHardwareProfile());
        System.out.println("  Effective MIPS: " + cpuDevice.getEffectiveMips());
        System.out.println("  Execution time for 10000 MI: " +
                cpuDevice.getEffectiveExecutionTime(10000.0 / 1000) + " ms\n");

        // FPGA device
        HardwareProfile fpgaProfile = new HardwareProfile(
                ComputeArchitecture.FPGA,
                8,      // 8-way parallelism
                2.5,    // 2.5 GHz
                15.0,   // 15W idle
                45.0    // 45W peak
        );

        HeterogeneousFogDevice fpgaDevice = new HeterogeneousFogDevice(
                "FPGA_Device_1", 1000, 2048, 5000, 0.0, fpgaProfile);
        fpgaDevice.setPhysicalGraph(graph);

        System.out.println("FPGA Device:");
        System.out.println("  " + fpgaDevice.getHardwareProfile());
        System.out.println("  Effective MIPS: " + fpgaDevice.getEffectiveMips());
        System.out.println("  Execution time for 10000 MI: " +
                fpgaDevice.getEffectiveExecutionTime(10000.0 / 1000) + " ms\n");

        // GPU device
        HardwareProfile gpuProfile = new HardwareProfile(
                ComputeArchitecture.GPU,
                32,     // 32-way parallelism
                1.5,    // 1.5 GHz
                20.0,   // 20W idle
                250.0   // 250W peak
        );

        HeterogeneousFogDevice gpuDevice = new HeterogeneousFogDevice(
                "GPU_Device_2", 2000, 4096, 10000, 0.0, gpuProfile);
        gpuDevice.setPhysicalGraph(graph);

        System.out.println("GPU Device:");
        System.out.println("  " + gpuDevice.getHardwareProfile());
        System.out.println("  Effective MIPS: " + gpuDevice.getEffectiveMips());
        System.out.println("  Execution time for 10000 MI: " +
                gpuDevice.getEffectiveExecutionTime(10000.0 / 2000) + " ms\n");

        // TPU device
        HardwareProfile tpuProfile = new HardwareProfile(
                ComputeArchitecture.TPU,
                128,    // 128-way parallelism
                2.0,    // 2 GHz
                25.0,   // 25W idle
                200.0   // 200W peak
        );

        HeterogeneousFogDevice tpuDevice = new HeterogeneousFogDevice(
                "TPU_Device_3", 4000, 8192, 20000, 0.0, tpuProfile);
        tpuDevice.setPhysicalGraph(graph);

        System.out.println("TPU Device:");
        System.out.println("  " + tpuDevice.getHardwareProfile());
        System.out.println("  Effective MIPS: " + tpuDevice.getEffectiveMips());
        System.out.println("  Execution time for 10000 MI: " +
                tpuDevice.getEffectiveExecutionTime(10000.0 / 4000) + " ms\n");

        // Comparison
        System.out.println("=== Performance Comparison ===");
        System.out.println("Task: 10000 MI workload\n");

        double baselineTime = cpuDevice.getEffectiveExecutionTime(10000.0 / 1000);
        System.out.printf("CPU:      %8.4f ms (1.00x baseline)%n", baselineTime);
        System.out.printf("FPGA:     %8.4f ms (%.2fx speedup)%n",
                fpgaDevice.getEffectiveExecutionTime(10000.0 / 1000),
                baselineTime / fpgaDevice.getEffectiveExecutionTime(10000.0 / 1000));
        System.out.printf("GPU:      %8.4f ms (%.2fx speedup)%n",
                gpuDevice.getEffectiveExecutionTime(10000.0 / 2000),
                baselineTime / gpuDevice.getEffectiveExecutionTime(10000.0 / 2000));
        System.out.printf("TPU:      %8.4f ms (%.2fx speedup)%n",
                tpuDevice.getEffectiveExecutionTime(10000.0 / 4000),
                baselineTime / tpuDevice.getEffectiveExecutionTime(10000.0 / 4000));

        System.out.println("\n=== Power Consumption ===");
        System.out.println("Estimated power at 50% utilization:\n");

        System.out.printf("CPU:      %.2f W%n", cpuDevice.estimatePowerConsumption(0.5));
        System.out.printf("FPGA:     %.2f W%n", fpgaDevice.estimatePowerConsumption(0.5));
        System.out.printf("GPU:      %.2f W%n", gpuDevice.estimatePowerConsumption(0.5));
        System.out.printf("TPU:      %.2f W%n", tpuDevice.estimatePowerConsumption(0.5));

        System.out.println("\nExample completed successfully!");
    }
}
