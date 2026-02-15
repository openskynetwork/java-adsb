package org.opensky.libadsb.msgs;

import org.junit.Test;
import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.Position;
import org.opensky.libadsb.tools;

import static org.junit.Assert.*;

/**
 * Tests for surface position messages (TC 5-8).
 * Expected values cross-validated with pyModeS 2.21.1 and lib1090.
 *
 * Bug #6: Southern hemisphere detection subtracts latitude from 90
 *         instead of negating it (e.g. 90-43.49 = 46.51 instead of -43.49).
 *
 * Bug #7: Longitude wrap doesn't handle values < -180 after hemisphere adjustment.
 */
public class SurfacePositionV0MsgTest {

    // === Surface message basic field tests ===

    // 8c3c4dc6381c07331b029eb308de: TC=7, heading=180, GS=0
    @Test
    public void testSurfaceHeading_3C4DC6() throws Exception {
        SurfacePositionV0Msg msg = (SurfacePositionV0Msg) Decoder.genericDecoder("8c3c4dc6381c07331b029eb308de");
        assertTrue(msg.hasValidHeading());
        assertEquals(180.0, msg.getHeading(), 0.001);
    }

    @Test
    public void testSurfaceGroundSpeedZero_3C4DC6() throws Exception {
        SurfacePositionV0Msg msg = (SurfacePositionV0Msg) Decoder.genericDecoder("8c3c4dc6381c07331b029eb308de");
        assertTrue(msg.hasGroundSpeed());
        assertEquals(0.0, msg.getGroundSpeed(), 0.1);
    }

    // 8c4841753aab238733c8cd4020b1: TC=7, GS=18, track=140.625
    @Test
    public void testSurfaceGroundSpeed_484175() throws Exception {
        SurfacePositionV0Msg msg = (SurfacePositionV0Msg) Decoder.genericDecoder("8c4841753aab238733c8cd4020b1");
        assertTrue(msg.hasGroundSpeed());
        assertEquals(18.0, msg.getGroundSpeed(), 1.0);
    }

    @Test
    public void testSurfaceTrack_484175() throws Exception {
        SurfacePositionV0Msg msg = (SurfacePositionV0Msg) Decoder.genericDecoder("8c4841753aab238733c8cd4020b1");
        assertEquals(140.625, msg.getHeading(), 0.001);
    }

    // 8FC8200A3AB8F5F893096B000000: TC=7, GS=19, track=42.1875
    @Test
    public void testSurfaceGroundSpeed_C8200A() throws Exception {
        SurfacePositionV0Msg msg = (SurfacePositionV0Msg) Decoder.genericDecoder("8FC8200A3AB8F5F893096B000000");
        assertEquals(19.0, msg.getGroundSpeed(), 1.0);
    }

    @Test
    public void testSurfaceTrack_C8200A() throws Exception {
        SurfacePositionV0Msg msg = (SurfacePositionV0Msg) Decoder.genericDecoder("8FC8200A3AB8F5F893096B000000");
        assertEquals(42.1875, msg.getHeading(), 0.001);
    }

    // === Surface CPR flag tests ===

    @Test
    public void testOddFlag_C8200A() throws Exception {
        SurfacePositionV0Msg msg = (SurfacePositionV0Msg) Decoder.genericDecoder("8FC8200A3AB8F5F893096B000000");
        assertTrue(msg.isOddFormat());
    }

    @Test
    public void testEvenFlag_484175() throws Exception {
        SurfacePositionV0Msg msg = (SurfacePositionV0Msg) Decoder.genericDecoder("8c4841753aab238733c8cd4020b1");
        assertFalse(msg.isOddFormat());
    }

    // === Local CPR decode with reference ===

    @Test
    public void testSurfaceLocalPosition_SouthernHemisphere() throws Exception {
        // Bug #6: reference lat=-43.5, lon=172.5
        // pyModeS: ref_lat=-43.485644, ref_lon=172.539417
        // Correct: latitude should be negative (southern hemisphere)
        SurfacePositionV0Msg msg = (SurfacePositionV0Msg) Decoder.genericDecoder("8FC8200A3AB8F5F893096B000000");
        Position ref = new Position(172.5, -43.5, null);
        Position pos = msg.getLocalPosition(ref);
        assertNotNull(pos);
        // Latitude should be in the southern hemisphere
        assertTrue("Latitude should be negative for southern hemisphere",
                pos.getLatitude() < 0);
        assertEquals(-43.486, pos.getLatitude(), 0.01);
        assertEquals(172.539, pos.getLongitude(), 0.01);
    }

    @Test
    public void testSurfaceLocalPosition_NorthernHemisphere() throws Exception {
        // reference lat=51.99, lon=4.375
        // pyModeS: ref_lat=52.32304, ref_lon=4.730473
        SurfacePositionV0Msg msg = (SurfacePositionV0Msg) Decoder.genericDecoder("8c4841753aab238733c8cd4020b1");
        Position ref = new Position(4.375, 51.99, null);
        Position pos = msg.getLocalPosition(ref);
        assertNotNull(pos);
        assertEquals(52.323, pos.getLatitude(), 0.01);
        assertEquals(4.730, pos.getLongitude(), 0.01);
    }

    // === Type code ===

    @Test
    public void testTypeCode7() throws Exception {
        SurfacePositionV0Msg msg = (SurfacePositionV0Msg) Decoder.genericDecoder("8c3c4dc6381c07331b029eb308de");
        assertEquals(7, msg.getFormatTypeCode());
    }

    // === ICAO extraction ===

    @Test
    public void testIcao_3C4DC6() throws Exception {
        SurfacePositionV0Msg msg = (SurfacePositionV0Msg) Decoder.genericDecoder("8c3c4dc6381c07331b029eb308de");
        assertEquals("3c4dc6", tools.toHexString(msg.getIcao24()));
    }

    @Test
    public void testIcao_484175() throws Exception {
        SurfacePositionV0Msg msg = (SurfacePositionV0Msg) Decoder.genericDecoder("8c4841753aab238733c8cd4020b1");
        assertEquals("484175", tools.toHexString(msg.getIcao24()));
    }
}
