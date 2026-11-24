package org.fog.hetfs.core;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.placement.Controller;
import org.fog.utils.FogEvents;

/**
 * iFogSim {@link Controller} whose end of simulation can be observed.
 *
 * <p>{@code Controller} handles {@code STOP_SIMULATION} by printing its reports and
 * calling {@code System.exit(0)}, so no code placed after
 * {@code CloudSim.startSimulation()} ever runs. This controller first notifies the
 * registered end-of-simulation listeners, then either delegates to {@code Controller}
 * (default, identical console output and exit) or, after
 * {@link #setExitOnStop(boolean) setExitOnStop(false)}, terminates the CloudSim event
 * loop so that {@code CloudSim.startSimulation()} returns normally and several
 * results can be written by the caller.
 */
public class HetFSController extends Controller {

    private final List<Runnable> endListeners = new ArrayList<>();
    private boolean exitOnStop = true;
    private boolean stopped;

    public HetFSController(String name, List<FogDevice> fogDevices, List<Sensor> sensors,
                           List<Actuator> actuators) {
        super(name, fogDevices, sensors, actuators);
    }

    public void addEndListener(Runnable listener) {
        endListeners.add(listener);
    }

    public void setExitOnStop(boolean exitOnStop) {
        this.exitOnStop = exitOnStop;
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() == FogEvents.STOP_SIMULATION) {
            if (!stopped) {
                stopped = true;
                for (Runnable r : endListeners) {
                    r.run();
                }
            }
            if (!exitOnStop) {
                // CloudSim.stopSimulation() only prints a message; the event loop keeps
                // running as long as periodic sensors schedule new events.
                CloudSim.abruptallyTerminate();
                return;
            }
        }
        super.processEvent(ev);
    }
}
