package org.fog.hetfs.topology;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.fog.entities.Tuple;
import org.junit.Before;
import org.junit.Test;

public class GraphRouterTest {

    /** Minimal device: hosts a fixed set of modules and actuator types. */
    static final class FakeDevice implements RoutableDevice {
        final int id;
        final Set<String> modules = new HashSet<>();
        final Set<String> actuators = new HashSet<>();
        final Set<Integer> instances = new HashSet<>();

        FakeDevice(int id) {
            this.id = id;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public String getName() {
            return "d" + id;
        }

        @Override
        public boolean hostsModule(String appId, String moduleName) {
            return modules.contains(moduleName);
        }

        @Override
        public boolean hostsModuleInstance(int vmId) {
            return instances.contains(vmId);
        }

        @Override
        public boolean hasActuatorOfType(String type) {
            return actuators.contains(type);
        }
    }

    private PhysicalGraph g;
    private GraphRouter r;
    private FakeDevice d1, d2, d3, d4;

    // 1 -1- 2 -1- 3 -1- 4 , plus 1 -10- 4
    @Before
    public void setUp() {
        g = new PhysicalGraph();
        g.addBidirectionalLink(1, 2, 1, 100);
        g.addBidirectionalLink(2, 3, 1, 100);
        g.addBidirectionalLink(3, 4, 1, 100);
        g.addBidirectionalLink(1, 4, 10, 100);
        r = new GraphRouter(g);
        d1 = new FakeDevice(1);
        d2 = new FakeDevice(2);
        d3 = new FakeDevice(3);
        d4 = new FakeDevice(4);
        for (FakeDevice d : new FakeDevice[] {d1, d2, d3, d4}) {
            r.register(d);
        }
    }

    private static Tuple tuple(String dest, int direction) {
        Tuple t = new Tuple("app", 1, direction, 10, 1, 10, 10, new UtilizationModelFull(),
                new UtilizationModelFull(), new UtilizationModelFull());
        t.setAppId("app");
        t.setDestModuleName(dest);
        t.setModuleCopyMap(new HashMap<String, Integer>());
        return t;
    }

    @Test
    public void nearestReplicaIsSelected() {
        d2.modules.add("m");
        d4.modules.add("m");
        assertEquals(2, r.resolveTarget(d1, tuple("m", Tuple.UP)));
        // equidistant replicas: the smaller device id wins
        assertEquals(2, r.resolveTarget(d3, tuple("m", Tuple.UP)));
        d2.modules.clear();
        d1.modules.add("m");
        assertEquals(4, r.resolveTarget(d3, tuple("m", Tuple.UP)));
    }

    @Test
    public void localModuleWins() {
        d1.modules.add("m");
        d2.modules.add("m");
        assertEquals(1, r.resolveTarget(d1, tuple("m", Tuple.DOWN)));
    }

    @Test
    public void pinnedInstanceOverridesProximity() {
        d2.modules.add("m");
        d4.modules.add("m");
        d4.instances.add(42);
        Tuple t = tuple("m", Tuple.UP);
        t.getModuleCopyMap().put("m", 42);
        assertEquals(4, r.resolveTarget(d1, t));
    }

    @Test
    public void actuatorTuplesGoToTheGateway() {
        d3.actuators.add("SCREEN");
        assertEquals(3, r.resolveTarget(d1, tuple("SCREEN", Tuple.ACTUATOR)));
        assertEquals(3, r.resolveTarget(d3, tuple("SCREEN", Tuple.ACTUATOR)));
    }

    @Test
    public void unplacedModuleIsReported() {
        assertEquals(GraphRouter.UNPLACED, r.resolveTarget(d1, tuple("ghost", Tuple.UP)));
    }

    @Test
    public void unreachableHostIsReported() {
        d4.modules.add("m");
        g.setDeviceUp(4, false);
        assertEquals(GraphRouter.UNREACHABLE, r.resolveTarget(d1, tuple("m", Tuple.UP)));
    }

    @Test
    public void routesAreRecomputedOnlyAfterATopologyChange() {
        assertEquals(2, r.nextHop(1, 4));
        assertEquals(2, r.nextHop(1, 3));
        assertEquals(1, r.getTreeComputations());
        g.setDeviceUp(2, false);
        assertEquals(4, r.nextHop(1, 4));
        assertEquals(2, r.getTreeComputations());
        assertEquals(10.0, r.latency(1, 4), 1e-12);
    }

    @Test(expected = IllegalArgumentException.class)
    public void devicesOutsideTheGraphAreRejected() {
        r.register(new FakeDevice(99));
    }
}
