package org.opensky.libadsb.msgs;

import org.junit.Test;
import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.tools;
import org.opensky.libadsb.exceptions.BadFormatException;

import static org.junit.Assert.*;

/**
 * Tests for ADS-B identification messages (TC 1-4).
 * Expected values cross-validated with pyModeS 2.21.1 and lib1090.
 * Note: trailing '_' in pyModeS output represents space ' ' in actual callsign.
 */
public class IdentificationMsgTest {

    @Test
    public void testCallsignEZY85MH() throws Exception {
        IdentificationMsg msg = (IdentificationMsg) Decoder.genericDecoder("8D406B902015A678D4D220AA4BDA");
        // pyModeS: "EZY85MH_" (_ = space), lib1090: "EZY85MH "
        assertEquals("EZY85MH ", new String(msg.getIdentity()));
    }

    @Test
    public void testCallsignKLM1023() throws Exception {
        IdentificationMsg msg = (IdentificationMsg) Decoder.genericDecoder("8D4840D6202CC371C32CE0576098");
        // pyModeS: "KLM1023_" (_ = space), lib1090: "KLM1023 "
        assertEquals("KLM1023 ", new String(msg.getIdentity()));
    }

    @Test
    public void testCategoryEZY85MH() throws Exception {
        IdentificationMsg msg = (IdentificationMsg) Decoder.genericDecoder("8D406B902015A678D4D220AA4BDA");
        // All decoders agree: category=0
        assertEquals(0, msg.getEmitterCategory());
    }

    @Test
    public void testCategoryKLM1023() throws Exception {
        IdentificationMsg msg = (IdentificationMsg) Decoder.genericDecoder("8D4840D6202CC371C32CE0576098");
        assertEquals(0, msg.getEmitterCategory());
    }

    @Test
    public void testTypeCodeIdentification() throws Exception {
        IdentificationMsg msg = (IdentificationMsg) Decoder.genericDecoder("8D406B902015A678D4D220AA4BDA");
        assertEquals(4, msg.getFormatTypeCode());
    }

    @Test
    public void testIcaoIdentification() throws Exception {
        IdentificationMsg msg = (IdentificationMsg) Decoder.genericDecoder("8D406B902015A678D4D220AA4BDA");
        assertEquals("406b90", tools.toHexString(msg.getIcao24()));
    }
}
