package org.fog.hetfs.core;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.entities.Actuator;
import org.fog.entities.Tuple;
import org.fog.hetfs.timing.LatencyTracer;
import org.fog.utils.FogEvents;

/** iFogSim {@link Actuator} that reports deliveries to a {@link LatencyTracer} (module D6). */
public class HetFSActuator extends Actuator {

    private LatencyTracer tracer;

    public HetFSActuator(String name, int userId, String appId, String actuatorType) {
        super(name, userId, appId, actuatorType);
    }

    public void setTracer(LatencyTracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (tracer != null && ev.getTag() == FogEvents.TUPLE_ARRIVAL) {
            tracer.onActuatorArrival((Tuple) ev.getData(), getName(), CloudSim.clock());
        }
        super.processEvent(ev);
    }
}
