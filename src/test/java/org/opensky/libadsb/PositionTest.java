package org.opensky.libadsb;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for the Position class: coordinates, haversine, ECEF conversion.
 */
public class PositionTest {

    @Test
    public void testConstructorAndGetters() {
        Position pos = new Position(6.08, 49.82, 39000.0);
        assertEquals(6.08, pos.getLongitude(), 0.001);
        assertEquals(49.82, pos.getLatitude(), 0.001);
        assertEquals(39000.0, pos.getAltitude(), 0.1);
    }

    @Test
    public void testNullComponents() {
        Position pos = new Position(null, null, null);
        assertNull(pos.getLatitude());
        assertNull(pos.getLongitude());
        assertNull(pos.getAltitude());
    }

    @Test
    public void testHaversineDistance() {
        // Two known positions from CPR decode:
        // pos1: 49.824097, 6.067850
        // pos2: 49.817551, 6.084422
        Position pos1 = new Position(6.067850, 49.824097, null);
        Position pos2 = new Position(6.084422, 49.817551, null);
        Double dist = pos1.haversine(pos2);
        assertNotNull(dist);
        // Distance should be roughly 1.3 km
        assertTrue("Haversine distance should be > 500m", dist > 500);
        assertTrue("Haversine distance should be < 3000m", dist < 3000);
    }

    @Test
    public void testHaversineZeroDistance() {
        Position pos = new Position(0.0, 0.0, null);
        assertEquals(0.0, pos.haversine(pos), 0.001);
    }

    @Test
    public void testHaversineAntipodal() {
        // North pole to south pole: ~20,000 km
        Position north = new Position(0.0, 90.0, null);
        Position south = new Position(0.0, -90.0, null);
        Double dist = north.haversine(south);
        assertNotNull(dist);
        assertEquals(20015000, dist, 100000); // ~20,015 km ± 100 km
    }

    @Test
    public void testReasonableFlag() {
        Position pos = new Position(6.0, 49.0, 39000.0);
        // Default should be true
        assertTrue(pos.isReasonable());
        pos.setReasonable(false);
        assertFalse(pos.isReasonable());
    }
}
