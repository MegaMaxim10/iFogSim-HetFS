package org.fog.hetfs.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.hetfs.core.HetFSSensor;
import org.fog.placement.ModuleMapping;
import org.fog.utils.distribution.DeterministicDistribution;
import org.junit.Before;
import org.junit.Test;

public class ApplicationValidatorTest {

    private Application app;

    @Before
    public void setUp() {
        CloudSim.init(1, Calendar.getInstance(), false);
        app = Application.createApplication("a", 1);
        app.addAppModule("zone1", 10);
        app.addAppModule("zone2", 10);
        app.addAppModule("coord", 10);
        app.addAppModule("unit1", 10);
        app.addAppModule("unit2", 10);
        app.addAppEdge("INCIDENT", "zone1", 10, 10, "INCIDENT", Tuple.UP, AppEdge.SENSOR);
        app.addAppEdge("INCIDENT", "zone2", 10, 10, "INCIDENT", Tuple.UP, AppEdge.SENSOR);
        app.addAppEdge("coord", "unit1", 10, 10, "D1", Tuple.DOWN, AppEdge.MODULE);
        app.addAppEdge("coord", "unit2", 10, 10, "D2", Tuple.DOWN, AppEdge.MODULE);
    }

    private static List<String> codes(List<Finding> f) {
        List<String> c = new ArrayList<>();
        for (Finding x : f) {
            c.add(x.getCode());
        }
        return c;
    }

    @Test
    public void unmappedModuleIsAnError() {
        ModuleMapping m = ModuleMapping.createModuleMapping();
        for (String mod : new String[] {"zone1", "zone2", "coord", "unit1"}) {
            m.addModuleToDevice(mod, "d");
        }
        List<Finding> f = ApplicationValidator.validate(app, new ArrayList<Sensor>(),
                ApplicationValidator.Placement.MAPPING, m);
        assertEquals(Arrays.asList("UNMAPPED_MODULE"), codes(f));
        assertTrue(f.get(0).getMessage().contains("unit2"));
    }

    @Test
    public void downwardFanOutUnderEdgewardsIsReported() {
        List<Finding> f = ApplicationValidator.validate(app, new ArrayList<Sensor>(),
                ApplicationValidator.Placement.EDGEWARDS, null);
        assertEquals(Arrays.asList("EDGEWARDS_DOWNWARD_FANOUT"), codes(f));
    }

    @Test
    public void sharedSensorTupleTypeIsAnError() {
        Sensor s1 = new Sensor("s1", "INCIDENT", 1, "a", new DeterministicDistribution(10));
        List<Finding> f = ApplicationValidator.validate(app, Arrays.asList(s1),
                ApplicationValidator.Placement.OTHER, null);
        assertTrue(codes(f).contains("SHARED_SENSOR_TUPLE_TYPE"));
    }

    @Test
    public void hetfsSensorWithDestinationIsAccepted() {
        HetFSSensor s1 = new HetFSSensor("s1", "INCIDENT", 1, "a", new DeterministicDistribution(10));
        s1.setDestModuleName("zone1");
        List<Finding> f = ApplicationValidator.validate(app, Arrays.asList(s1),
                ApplicationValidator.Placement.OTHER, null);
        assertTrue(!codes(f).contains("SHARED_SENSOR_TUPLE_TYPE"));
    }

    @Test
    public void overlappingLoopsAreReported() {
        AppLoop outer = new AppLoop(Arrays.asList("INCIDENT", "zone1", "coord", "unit1"));
        AppLoop inner = new AppLoop(Arrays.asList("coord", "unit1"));
        app.setLoops(Arrays.asList(outer, inner));
        List<Finding> f = ApplicationValidator.validate(app, new ArrayList<Sensor>(),
                ApplicationValidator.Placement.OTHER, null);
        assertEquals(Arrays.asList("LOOP_SHARES_START_MODULE"), codes(f));
    }

    @Test
    public void unnamedSensorIsReported() {
        Sensor s = new Sensor("s", 1, "a", 0, 1.0, null, new DeterministicDistribution(10), "OTHER");
        List<Finding> f = ApplicationValidator.validate(app, Arrays.asList(s),
                ApplicationValidator.Placement.OTHER, null);
        assertTrue(codes(f).contains("UNNAMED_SENSOR"));
    }
}
