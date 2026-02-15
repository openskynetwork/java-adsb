package org.opensky.libadsb.msgs;

import org.junit.Test;
import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.tools;

import static org.junit.Assert.*;

/**
 * Tests for airspeed and heading messages (TC 19, subtype 3-4).
 * Expected values cross-validated with pyModeS 2.21.1 and lib1090.
 *
 * Bug #1: Heading uses integer division (360/1024 = 0) instead of floating-point.
 *         Current code truncates heading to integer degrees.
 *         Correct: heading = raw * 360.0 / 1024.0
 *
 * Bug #2: Vertical rate availability check compares against -1 but
 *         raw=0 produces -64 after <<6 shift.
 *
 * Bug #10: Geo-minus-baro availability check compares against -1 but
 *          raw=0 produces -25 after (0-1)*25.
 */
public class AirspeedHeadingMsgTest {

    // --- Message: 8DA05F219B06B6AF189400CBC33F ---
    // pyModeS: speed=375 (TAS), heading=243.984375, vrate=-2304, alt_diff=null (unavailable)
    // lib1090: speed=375, heading=243.984375, vrate=-2304, alt_diff=-25 (bug)

    @Test
    public void testHeadingPrecision() throws Exception {
        // Bug #1: java-adsb returns 243.0 due to integer division (360/1024)
        // Correct value: 694 * 360.0 / 1024.0 = 243.984375
        AirspeedHeadingMsg msg = (AirspeedHeadingMsg) Decoder.genericDecoder("8DA05F219B06B6AF189400CBC33F");
        assertTrue(msg.hasHeadingStatusFlag());
        assertEquals(243.984375, msg.getHeading(), 0.001);
    }

    @Test
    public void testAirspeed() throws Exception {
        AirspeedHeadingMsg msg = (AirspeedHeadingMsg) Decoder.genericDecoder("8DA05F219B06B6AF189400CBC33F");
        assertTrue(msg.hasAirspeedInfo());
        assertEquals(375, msg.getAirspeed().intValue());
    }

    @Test
    public void testTrueAirspeed() throws Exception {
        AirspeedHeadingMsg msg = (AirspeedHeadingMsg) Decoder.genericDecoder("8DA05F219B06B6AF189400CBC33F");
        assertTrue(msg.isTrueAirspeed());
    }

    @Test
    public void testVerticalRate() throws Exception {
        AirspeedHeadingMsg msg = (AirspeedHeadingMsg) Decoder.genericDecoder("8DA05F219B06B6AF189400CBC33F");
        assertTrue(msg.hasVerticalRateInfo());
        assertEquals(-2304, msg.getVerticalRate().intValue());
    }

    @Test
    public void testGeoMinusBaroUnavailable() throws Exception {
        // Bug #10: msg[6]=0x00, raw 7-bit field=0 means "not available".
        // Current code computes (0-1)*25 = -25 and checks ==-1, which fails.
        // Correct: hasGeoMinusBaroInfo() should return false.
        AirspeedHeadingMsg msg = (AirspeedHeadingMsg) Decoder.genericDecoder("8DA05F219B06B6AF189400CBC33F");
        assertFalse("geo-minus-baro should not be available when raw field is 0",
                msg.hasGeoMinusBaroInfo());
    }

    @Test
    public void testGeoMinusBaroReturnsNullWhenUnavailable() throws Exception {
        // Bug #10: getGeoMinusBaro() should return null when not available
        AirspeedHeadingMsg msg = (AirspeedHeadingMsg) Decoder.genericDecoder("8DA05F219B06B6AF189400CBC33F");
        assertNull("getGeoMinusBaro() should return null when unavailable",
                msg.getGeoMinusBaro());
    }

    @Test
    public void testIcaoExtraction() throws Exception {
        AirspeedHeadingMsg msg = (AirspeedHeadingMsg) Decoder.genericDecoder("8DA05F219B06B6AF189400CBC33F");
        assertEquals("a05f21", tools.toHexString(msg.getIcao24()));
    }

    @Test
    public void testSubtype3NotSupersonic() throws Exception {
        AirspeedHeadingMsg msg = (AirspeedHeadingMsg) Decoder.genericDecoder("8DA05F219B06B6AF189400CBC33F");
        assertFalse(msg.isSupersonic());
    }

    // --- Message: 8D4400CD9B0000B4F87000E71A10 ---
    // TC19 subtype 3 with heading_status_bit=0 (no heading available)
    // pyModeS returns no velocity (whole tuple is None because heading unavailable)
    // java-adsb/lib1090: speed=422, heading=null, vrate=-1728

    @Test
    public void testNoHeadingAvailable() throws Exception {
        AirspeedHeadingMsg msg = (AirspeedHeadingMsg) Decoder.genericDecoder("8d4400cd9b0000b4f87000e71a10");
        assertFalse(msg.hasHeadingStatusFlag());
        assertNull(msg.getHeading());
    }
}
