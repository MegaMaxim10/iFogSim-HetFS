package org.fog.hetfs.core;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.hetfs.timing.LatencyTracer;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;
import org.fog.utils.distribution.Distribution;

/**
 * iFogSim {@link Sensor} with deterministic edge resolution and lineage tracing.
 *
 * <p>{@code Sensor#transmit()} looks for the application edge to use by scanning
 * every edge whose source equals the sensor's tuple type and keeping the
 * <em>last</em> one. When several sensors share a tuple type but feed different
 * modules, all of them silently send to the same module (challenge C8 of the
 * companion paper). A {@code HetFSSensor} selects the edge whose source is its tuple
 * type and whose destination is its destination module when one is given with
 * {@link #setDestModuleName}, and fails with an explicit error when the choice is
 * ambiguous. Otherwise it builds exactly the tuple {@code Sensor} builds, registers
 * its emission with an optional {@link LatencyTracer} (module D6), and draws on an
 * optional battery (module D7).
 */
public class HetFSSensor extends Sensor {

    private LatencyTracer tracer;
    private AppEdge edge;

    public HetFSSensor(String name, String tupleType, int userId, String appId, Distribution transmitDistribution) {
        super(name, tupleType, userId, appId, transmitDistribution);
    }

    public void setTracer(LatencyTracer tracer) {
        this.tracer = tracer;
    }

    public LatencyTracer getTracer() {
        return tracer;
    }

    /** The application edge this sensor feeds (resolved on first use). */
    public AppEdge resolveEdge() {
        if (edge != null) {
            return edge;
        }
        List<AppEdge> candidates = new ArrayList<>();
        for (AppEdge e : getApp().getEdges()) {
            if (e.getSource().equals(getTupleType())
                    && (getDestModuleName() == null || e.getDestination().equals(getDestModuleName()))) {
                candidates.add(e);
            }
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException(getName() + ": no application edge from tuple type " + getTupleType()
                    + (getDestModuleName() == null ? "" : " to " + getDestModuleName()));
        }
        if (candidates.size() > 1) {
            StringBuilder dests = new StringBuilder();
            for (AppEdge e : candidates) {
                dests.append(' ').append(e.getDestination());
            }
            throw new IllegalStateException(getName() + ": tuple type " + getTupleType()
                    + " feeds several modules (" + dests.toString().trim()
                    + "); call setDestModuleName to choose one");
        }
        edge = candidates.get(0);
        return edge;
    }

    @Override
    public void transmit() {
        AppEdge e = resolveEdge();
        Tuple tuple = new Tuple(getAppId(), FogUtils.generateTupleId(), Tuple.UP, (long) e.getTupleCpuLength(), 1,
                (long) e.getTupleNwLength(), getOutputSize(), new UtilizationModelFull(), new UtilizationModelFull(),
                new UtilizationModelFull());
        tuple.setUserId(getUserId());
        tuple.setTupleType(getTupleType());
        tuple.setDestModuleName(e.getDestination());
        tuple.setSrcModuleName(getTupleType());
        tuple.setDestinationDeviceId(getGatewayDeviceId());
        Logger.debug(getName(), "Sending tuple with tupleId = " + tuple.getCloudletId());
        tuple.setActualTupleId(updateTimings(getTupleType(), tuple.getDestModuleName()));
        if (tracer != null) {
            tracer.onEmit(tuple, getName(), CloudSim.clock());
        }
        send(getGatewayDeviceId(), getLatency(), FogEvents.TUPLE_ARRIVAL, tuple);
    }
}
