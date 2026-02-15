package org.opensky.libadsb.msgs;

import org.junit.Test;
import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.tools;

import static org.junit.Assert.*;

/**
 * Tests for velocity over ground messages (TC 19, subtype 1-2).
 * Expected values cross-validated with pyModeS 2.21.1 and lib1090.
 *
 * Tests marked "Bug #2" will FAIL against current code due to the vertical rate
 * availability check bug (checks == -1 but raw=0 produces -64 after <<6).
 */
public class VelocityOverGroundMsgTest {

    // --- Message: 8D485020994409940838175B284F ---
    // pyModeS: speed=159, heading=182.88, vrate=-832, GS, alt_diff=550
    // lib1090: speed=159.20, heading=182.88, vrate=-832, GS, alt_diff=550

    @Test
    public void testGroundSpeed_485020() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8D485020994409940838175B284F");
        assertTrue(msg.hasVelocityInfo());
        // pyModeS returns integer 159, lib1090 returns 159.201131
        assertEquals(159.0, msg.getVelocity(), 1.0);
    }

    @Test
    public void testHeading_485020() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8D485020994409940838175B284F");
        assertEquals(182.88, msg.getHeading(), 0.01);
    }

    @Test
    public void testVerticalRate_485020() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8D485020994409940838175B284F");
        assertTrue(msg.hasVerticalRateInfo());
        assertEquals(-832, msg.getVerticalRate().intValue());
    }

    @Test
    public void testGeoMinusBaro_485020() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8D485020994409940838175B284F");
        assertTrue(msg.hasGeoMinusBaroInfo());
        assertEquals(550, msg.getGeoMinusBaro().intValue());
    }

    // --- Message: 8D45AC2D9904D910613F94BA81B5 ---
    // pyModeS: speed=252, heading=301.04, vrate=4992, GS, alt_diff=-475
    @Test
    public void testGroundSpeed_45AC2D() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8d45ac2d9904d910613f94ba81b5");
        assertEquals(252.0, msg.getVelocity(), 1.0);
    }

    @Test
    public void testHeading_45AC2D() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8d45ac2d9904d910613f94ba81b5");
        assertEquals(301.04, msg.getHeading(), 0.01);
    }

    @Test
    public void testVerticalRatePositive_45AC2D() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8d45ac2d9904d910613f94ba81b5");
        assertEquals(4992, msg.getVerticalRate().intValue());
    }

    @Test
    public void testGeoMinusBaroNegative_45AC2D() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8d45ac2d9904d910613f94ba81b5");
        assertEquals(-475, msg.getGeoMinusBaro().intValue());
    }

    // --- Message: 8D451E8B99019699C00B0A81F36E ---
    // pyModeS: speed=453, heading=116.85, vrate=64, alt_diff=225
    @Test
    public void testSmallPositiveVerticalRate_451E8B() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8D451E8B99019699C00B0A81F36E");
        assertTrue(msg.hasVerticalRateInfo());
        assertEquals(64, msg.getVerticalRate().intValue());
    }

    // --- Small vertical rate tests (from jet1090 test vectors) ---
    // 8d3461cf9908388930080f948ea1: vrate=+64
    @Test
    public void testVerticalRate64_3461cf_a() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8d3461cf9908388930080f948ea1");
        assertEquals(64, msg.getVerticalRate().intValue());
        assertEquals(350, msg.getGeoMinusBaro().intValue());
    }

    // 8d3461cf9908558e100c1071eb67: vrate=+128
    @Test
    public void testVerticalRate128_3461cf_b() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8d3461cf9908558e100c1071eb67");
        assertEquals(128, msg.getVerticalRate().intValue());
        assertEquals(375, msg.getGeoMinusBaro().intValue());
    }

    // 8d3461cf99085a8f10400f80e6ac: vrate=+960
    @Test
    public void testVerticalRate960_3461cf_c() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8d3461cf99085a8f10400f80e6ac");
        assertEquals(960, msg.getVerticalRate().intValue());
    }

    // 8d394c0f990c4932780838866883: vrate=-64
    @Test
    public void testVerticalRateNeg64_394c0f() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8d394c0f990c4932780838866883");
        assertEquals(-64, msg.getVerticalRate().intValue());
        assertEquals(1375, msg.getGeoMinusBaro().intValue());
    }

    @Test
    public void testIcaoExtraction() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8D485020994409940838175B284F");
        assertEquals("485020", tools.toHexString(msg.getIcao24()));
    }

    @Test
    public void testSpeedType() throws Exception {
        VelocityOverGroundMsg msg = (VelocityOverGroundMsg) Decoder.genericDecoder("8D485020994409940838175B284F");
        assertFalse("Ground speed messages should not be supersonic", msg.isSupersonic());
    }
}
