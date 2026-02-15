package org.opensky.libadsb.msgs;

import org.junit.Test;
import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.tools;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.UnspecifiedFormatError;

import static org.junit.Assert.*;

/**
 * Tests for CRC validation, ICAO extraction, and downlink format parsing.
 * Expected values cross-validated with pyModeS 2.21.1 and lib1090.
 */
public class ModeSReplyTest {

    // Valid DF17 messages (CRC=0 in pyModeS)
    private static final String[] VALID_MESSAGES = {
        "8D406B902015A678D4D220AA4BDA",
        "8D4840D6202CC371C32CE0576098",
        "8D485020994409940838175B284F",
        "8DA05F219B06B6AF189400CBC33F",
        "8d8960ed58bf053cf11bc5932b7d",
        "8d45cab390c39509496ca9a32912",
        "8d74802958c904e6ef4ba0184d5c",
        "8d4400cd9b0000b4f87000e71a10",
        "8d4065de58a1054a7ef0218e226a",
    };

    @Test
    public void testValidCRC() throws Exception {
        for (String hex : VALID_MESSAGES) {
            ModeSReply msg = Decoder.genericDecoder(hex);
            assertTrue("CRC should be valid for " + hex, msg.checkParity());
        }
    }

    @Test
    public void testDownlinkFormat17() throws Exception {
        ModeSReply msg = Decoder.genericDecoder("8D406B902015A678D4D220AA4BDA");
        assertEquals(17, msg.getDownlinkFormat());
    }

    @Test
    public void testDownlinkFormat17WithCA5() throws Exception {
        // 8D = 10001101, DF = 10001 = 17, CA = 101 = 5
        ModeSReply msg = Decoder.genericDecoder("8D485020994409940838175B284F");
        assertEquals(17, msg.getDownlinkFormat());
    }

    @Test
    public void testDownlinkFormat17WithCA4() throws Exception {
        // 8C = 10001100, DF = 10001 = 17, CA = 100 = 4
        ModeSReply msg = Decoder.genericDecoder("8c3c4dc6381c07331b029eb308de");
        assertEquals(17, msg.getDownlinkFormat());
    }

    @Test
    public void testIcaoExtraction() throws Exception {
        ModeSReply msg = Decoder.genericDecoder("8D406B902015A678D4D220AA4BDA");
        assertEquals("406b90", tools.toHexString(msg.getIcao24()));
    }

    @Test
    public void testIcaoExtractionMultiple() throws Exception {
        String[][] cases = {
            {"8D406B902015A678D4D220AA4BDA", "406b90"},
            {"8D4840D6202CC371C32CE0576098", "4840d6"},
            {"8D485020994409940838175B284F", "485020"},
            {"8DA05F219B06B6AF189400CBC33F", "a05f21"},
            {"8DA2C1B6E112B600000000760759", "a2c1b6"},
            {"8DA05629EA21485CBF3F8CADAEEB", "a05629"},
        };
        for (String[] c : cases) {
            ModeSReply msg = Decoder.genericDecoder(c[0]);
            assertEquals("ICAO for " + c[0], c[1], tools.toHexString(msg.getIcao24()));
        }
    }

    @Test
    public void testFormatTypeCode() throws Exception {
        // TC 4 - identification
        ExtendedSquitter es = (ExtendedSquitter) Decoder.genericDecoder("8D406B902015A678D4D220AA4BDA");
        assertEquals(4, es.getFormatTypeCode());

        // TC 11 - airborne position
        es = (ExtendedSquitter) Decoder.genericDecoder("8D40058B58C901375147EFD09357");
        assertEquals(11, es.getFormatTypeCode());

        // TC 19 - velocity
        es = (ExtendedSquitter) Decoder.genericDecoder("8D485020994409940838175B284F");
        assertEquals(19, es.getFormatTypeCode());

        // TC 28 - emergency
        es = (ExtendedSquitter) Decoder.genericDecoder("8DA2C1B6E112B600000000760759");
        assertEquals(28, es.getFormatTypeCode());

        // TC 29 - target state
        es = (ExtendedSquitter) Decoder.genericDecoder("8DA05629EA21485CBF3F8CADAEEB");
        assertEquals(29, es.getFormatTypeCode());
    }

    @Test
    public void testGenericDecoderReturnsCorrectType() throws Exception {
        assertTrue(Decoder.genericDecoder("8D406B902015A678D4D220AA4BDA") instanceof IdentificationMsg);
        assertTrue(Decoder.genericDecoder("8D40058B58C901375147EFD09357") instanceof AirbornePositionV0Msg);
        assertTrue(Decoder.genericDecoder("8D485020994409940838175B284F") instanceof VelocityOverGroundMsg);
        assertTrue(Decoder.genericDecoder("8DA05F219B06B6AF189400CBC33F") instanceof AirspeedHeadingMsg);
        assertTrue(Decoder.genericDecoder("8DA2C1B6E112B600000000760759") instanceof EmergencyOrPriorityStatusMsg);
    }

    @Test
    public void testSurfaceMessageType() throws Exception {
        // TC 7 surface position
        ModeSReply msg = Decoder.genericDecoder("8c3c4dc6381c07331b029eb308de");
        assertTrue(msg instanceof SurfacePositionV0Msg);
    }
}
