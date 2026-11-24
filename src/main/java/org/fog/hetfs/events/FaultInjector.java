package org.fog.hetfs.events;

import org.fog.hetfs.core.HetFSFogDevice;

/**
 * Fluent builder for failure scenarios (module D5).
 *
 * <pre>
 * FaultInjector.on(scheduler)
 *     .failDevice(esn, 500).recoverDevice(esn, 800)
 *     .failLink(cadZ1, esn, 300, 900)          // both directions, down between 300 and 900 ms
 *     .setLinkLatency(cadZ1, esn, 250, 1200)
 *     .pulse(cadZ2, 0, 400, 50, 3);            // 3 outages of 50 ms every 400 ms
 * </pre>
 */
public final class FaultInjector {

    private final TopologyEventScheduler scheduler;

    private FaultInjector(TopologyEventScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public static FaultInjector on(TopologyEventScheduler scheduler) {
        return new FaultInjector(scheduler);
    }

    public FaultInjector failDevice(HetFSFogDevice d, double atMs) {
        scheduler.registerDevice(d);
        scheduler.schedule(TopologyEvent.device(TopologyEventType.DEVICE_FAIL, atMs, d.getId()));
        return this;
    }

    public FaultInjector recoverDevice(HetFSFogDevice d, double atMs) {
        scheduler.registerDevice(d);
        scheduler.schedule(TopologyEvent.device(TopologyEventType.DEVICE_RECOVER, atMs, d.getId()));
        return this;
    }

    /** Takes the link between {@code a} and {@code b} down in both directions during [from, to). */
    public FaultInjector failLink(HetFSFogDevice a, HetFSFogDevice b, double fromMs, double toMs) {
        scheduler.schedule(TopologyEvent.link(TopologyEventType.LINK_FAIL, fromMs, a.getId(), b.getId(), Double.NaN, true));
        if (toMs > fromMs && !Double.isInfinite(toMs)) {
            scheduler.schedule(TopologyEvent.link(TopologyEventType.LINK_RECOVER, toMs, a.getId(), b.getId(), Double.NaN, true));
        }
        return this;
    }

    /** Sets the latency of the link between {@code a} and {@code b} (both directions) at {@code atMs}. */
    public FaultInjector setLinkLatency(HetFSFogDevice a, HetFSFogDevice b, double latencyMs, double atMs) {
        scheduler.schedule(TopologyEvent.link(TopologyEventType.LINK_LATENCY_CHANGE, atMs, a.getId(), b.getId(), latencyMs, true));
        return this;
    }

    /** Sets the bandwidth of the link between {@code a} and {@code b} (both directions) at {@code atMs}. */
    public FaultInjector setLinkBandwidth(HetFSFogDevice a, HetFSFogDevice b, double bandwidth, double atMs) {
        scheduler.schedule(TopologyEvent.link(TopologyEventType.LINK_BANDWIDTH_CHANGE, atMs, a.getId(), b.getId(), bandwidth, true));
        return this;
    }

    /** {@code count} outages of {@code durationMs} every {@code periodMs}, the first at {@code startMs}. */
    public FaultInjector pulse(HetFSFogDevice d, double startMs, double periodMs, double durationMs, int count) {
        if (durationMs >= periodMs) {
            throw new IllegalArgumentException("outage duration must be shorter than the period");
        }
        for (int i = 0; i < count; i++) {
            double t = startMs + i * periodMs;
            failDevice(d, t);
            recoverDevice(d, t + durationMs);
        }
        return this;
    }
}
