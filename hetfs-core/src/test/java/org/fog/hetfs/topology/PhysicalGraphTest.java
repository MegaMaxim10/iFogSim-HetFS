package org.fog.hetfs.topology;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.fog.entities.Tuple;
import org.junit.Before;
import org.junit.Test;

public class PhysicalGraphTest {

    // 1 --5-- 2 --5-- 3
    //  \             /
    //   ----20------
    private PhysicalGraph g;

    @Before
    public void setUp() {
        g = new PhysicalGraph();
        g.addBidirectionalLink(1, 2, 5, 100);
        g.addBidirectionalLink(2, 3, 5, 100);
        g.addBidirectionalLink(1, 3, 20, 100);
    }

    @Test
    public void shortestPathFollowsLowestLatency() {
        PhysicalGraph.ShortestPathTree t = g.shortestPathTree(1);
        assertEquals(10.0, t.distanceTo(3), 1e-12);
        assertEquals(Arrays.asList(1, 2, 3), t.pathTo(3));
        assertEquals(2, t.nextHop(3));
        assertEquals(1, t.nextHop(1));
    }

    @Test
    public void failedRelayForcesDetour() {
        g.setDeviceUp(2, false);
        PhysicalGraph.ShortestPathTree t = g.shortestPathTree(1);
        assertEquals(20.0, t.distanceTo(3), 1e-12);
        assertEquals(Arrays.asList(1, 3), t.pathTo(3));
        assertFalse(t.reaches(2));
    }

    @Test
    public void failedLinkIsDirectional() {
        g.setLinkUp(1, 2, false);
        assertEquals(20.0, g.shortestPathTree(1).distanceTo(3), 1e-12);
        assertEquals(10.0, g.shortestPathTree(3).distanceTo(1), 1e-12);
    }

    @Test
    public void unreachableTargetHasNoNextHop() {
        g.setLinkUp(1, 2, false);
        g.setLinkUp(1, 3, false);
        PhysicalGraph.ShortestPathTree t = g.shortestPathTree(1);
        assertEquals(-1, t.nextHop(3));
        assertTrue(t.pathTo(3).isEmpty());
        assertEquals(Double.POSITIVE_INFINITY, t.distanceTo(3), 0);
    }

    @Test
    public void everyChangeBumpsTheVersion() {
        long v0 = g.getVersion();
        g.setLinkLatency(1, 2, 7);
        long v1 = g.getVersion();
        g.setLinkBandwidth(1, 2, 50);
        long v2 = g.getVersion();
        g.setDeviceUp(3, false);
        long v3 = g.getVersion();
        assertNotEquals(v0, v1);
        assertNotEquals(v1, v2);
        assertNotEquals(v2, v3);
        g.setDeviceUp(3, false); // no change, same version
        assertEquals(v3, g.getVersion());
    }

    @Test
    public void tiesAreBrokenDeterministically() {
        PhysicalGraph d = new PhysicalGraph();
        d.addBidirectionalLink(1, 3, 1, 1);
        d.addBidirectionalLink(1, 2, 1, 1);
        d.addBidirectionalLink(3, 4, 1, 1);
        d.addBidirectionalLink(2, 4, 1, 1);
        assertEquals(Arrays.asList(1, 2, 4), d.shortestPathTree(1).pathTo(4));
    }

    @Test
    public void downedLinkDiscardsItsQueue() {
        PhysicalLink l = g.getLink(1, 2);
        for (int i = 0; i < 2; i++) {
            l.getQueue().add(new Tuple("app", i, Tuple.UP, 1, 1, 1, 1, new UtilizationModelFull(),
                    new UtilizationModelFull(), new UtilizationModelFull()));
        }
        g.setLinkUp(1, 2, false);
        assertEquals(0, l.getQueueLength());
        assertEquals(2, l.getDroppedTuples());
    }

    @Test(expected = IllegalArgumentException.class)
    public void selfLoopsAreRejected() {
        g.addLink(1, 1, 1, 1);
    }

    @Test
    public void serialisationDelayUsesBandwidth() {
        assertEquals(2.56, new PhysicalLink(1, 2, 0, 100).serialisationDelay(256), 1e-12);
    }
}
