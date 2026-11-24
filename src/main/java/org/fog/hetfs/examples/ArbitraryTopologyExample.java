package org.fog.hetfs.examples;

import org.fog.hetfs.topology.GraphAwareController;
import org.fog.hetfs.topology.GraphFogDevice;
import org.fog.hetfs.topology.PhysicalGraph;

import java.util.*;

/**
 * Example demonstrating arbitrary graph-based topology construction using
 * PhysicalGraph and GraphFogDevice. Shows how to build complex network topologies
 * beyond simple parent-child hierarchies.
 */
public class ArbitraryTopologyExample {

    public static void main(String[] args) {
        System.out.println("=== Arbitrary Topology Example ===\n");

        // Create a physical graph
        PhysicalGraph graph = new PhysicalGraph();

        // Add devices (fog computing nodes)
        for (int i = 0; i < 6; i++) {
            graph.addDevice(i);
        }

        // Add bidirectional links with varying latencies and bandwidths
        graph.addLink(0, 1, 5.0, 1000.0);
        graph.addLink(1, 0, 5.0, 1000.0);

        graph.addLink(1, 2, 10.0, 500.0);
        graph.addLink(2, 1, 10.0, 500.0);

        graph.addLink(1, 3, 8.0, 800.0);
        graph.addLink(3, 1, 8.0, 800.0);

        graph.addLink(2, 4, 12.0, 400.0);
        graph.addLink(4, 2, 12.0, 400.0);

        graph.addLink(3, 5, 15.0, 600.0);
        graph.addLink(5, 3, 15.0, 600.0);

        graph.addLink(4, 5, 20.0, 300.0);
        graph.addLink(5, 4, 20.0, 300.0);

        // Print topology information
        System.out.println("Topology Information:");
        System.out.println("  Devices: " + graph.getDeviceCount());
        System.out.println("  Links: " + graph.getLinkCount());
        System.out.println("  All devices: " + graph.getAllDeviceIds() + "\n");

        // Compute and print shortest paths
        System.out.println("Shortest Paths (by latency):");
        for (int src = 0; src < 6; src++) {
            for (int dst = src + 1; dst < 6; dst++) {
                List<Integer> path = graph.getShortestPath(src, dst);
                System.out.println("  " + src + " -> " + dst + ": " + path);
            }
        }
        System.out.println();

        // Create GraphFogDevice instances
        List<GraphFogDevice> devices = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            GraphFogDevice device = new GraphFogDevice(
                    "Node_" + i,
                    1000,       // MIPS
                    2048,       // RAM MB
                    10000,      // Bandwidth Mbps
                    0.0         // Rate per MIPS
            );
            device.setPhysicalGraph(graph);
            device.buildRoutingTable(graph);
            devices.add(device);
        }

        // Print routing tables
        System.out.println("Routing Tables:");
        for (GraphFogDevice device : devices) {
            System.out.println("  Device " + device.getId() + ": " + device.getRoutingTable());
        }
        System.out.println();

        // Create and initialize controller
        GraphAwareController controller = new GraphAwareController("TopologyController", graph);
        controller.registerDevices(devices);
        controller.initializeTopology();

        System.out.println("Controller initialized with " + controller.getGraphDevices().size() + " devices");
        System.out.println("Example completed successfully!");
    }
}
