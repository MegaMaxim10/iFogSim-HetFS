package org.fog.hetfs.examples;

import org.fog.hetfs.events.FaultInjector;
import org.fog.hetfs.events.TopologyEvent;
import org.fog.hetfs.events.TopologyEventType;

import java.util.*;

/**
 * Example demonstrating fault injection patterns for resilience testing.
 * Shows how to schedule device failures, link degradations, and failure-recovery cycles.
 */
public class FaultInjectionExample {

    public static void main(String[] args) {
        System.out.println("=== Fault Injection Example ===\n");

        // Create a fault injector
        FaultInjector injector = new FaultInjector();

        // Schedule single device failure
        injector.failDeviceAt(5, 1000.0);
        System.out.println("Scheduled device 5 to fail at 1000ms");

        // Schedule device recovery
        injector.recoverDeviceAt(5, 3000.0);
        System.out.println("Scheduled device 5 to recover at 3000ms");

        // Schedule link latency degradation
        injector.degradeLinkAt(10, 15, 2000.0, 50.0);
        System.out.println("Scheduled link 10->15 latency to degrade to 50ms at 2000ms");

        // Schedule link bandwidth reduction
        injector.reduceBandwidthAt(8, 9, 2500.0, 100.0);
        System.out.println("Scheduled link 8->9 bandwidth to reduce to 100Mbps at 2500ms");

        // Schedule a failure-recovery pulse
        injector.schedulePulse(20, 5000.0, 7000.0);
        System.out.println("Scheduled device 20 to pulse (fail at 5000ms, recover at 7000ms)");

        // Schedule a link pulse
        injector.scheduleLinkPulse(12, 13, 4000.0, 6000.0);
        System.out.println("Scheduled link 12->13 to pulse at 4000-6000ms");

        System.out.println("\n=== Scheduled Events ===\n");

        // Retrieve and display all scheduled events
        List<TopologyEvent> events = injector.getScheduledEvents();
        System.out.println("Total scheduled events: " + events.size() + "\n");

        for (TopologyEvent event : events) {
            System.out.println("  " + event);
        }

        System.out.println("\n=== Event Type Summary ===\n");

        // Count events by type
        Map<TopologyEventType, Integer> eventCounts = new HashMap<>();
        for (TopologyEvent event : events) {
            eventCounts.put(event.getType(), eventCounts.getOrDefault(event.getType(), 0) + 1);
        }

        for (Map.Entry<TopologyEventType, Integer> entry : eventCounts.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " events");
        }

        System.out.println("\n=== Timeline ===\n");

        for (TopologyEvent event : events) {
            String timeStr = String.format("%.0fms", event.getScheduledAt());
            String description;

            if (event.isDeviceEvent()) {
                description = String.format("%s: Device %d", event.getType(), event.getTargetDeviceId());
            } else {
                description = String.format("%s: Link %d->%d", event.getType(), event.getSrcId(), event.getDstId());
                if (event.getType() == TopologyEventType.LINK_LATENCY_CHANGE) {
                    description += String.format(" to %.2fms", event.getNewValue());
                } else if (event.getType() == TopologyEventType.LINK_BANDWIDTH_CHANGE) {
                    description += String.format(" to %.2fMbps", event.getNewValue());
                }
            }

            System.out.println("  [" + timeStr + "] " + description);
        }

        // Test chaining and clearing
        System.out.println("\n=== Testing Builder Pattern ===\n");

        FaultInjector chainInjector = new FaultInjector()
                .failDeviceAt(1, 100.0)
                .recoverDeviceAt(1, 200.0)
                .failDeviceAt(2, 150.0)
                .recoverDeviceAt(2, 250.0);

        System.out.println("Chained injector events: " + chainInjector.getEventCount());

        chainInjector.clear();
        System.out.println("After clearing: " + chainInjector.getEventCount());

        System.out.println("\nExample completed successfully!");
    }
}
