package org.opensky.libadsb.msgs;

import org.junit.Test;
import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.tools;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.UnspecifiedFormatError;

import static org.junit.Assert.*;

/**
 * Tests for ADS-B airborne operational status version 1 messages (TC 31, subtype 0).
 *
 * Bug #3: Byte sign extension in capability_class_code and operational_mode_code.
 *         msg[1]<<8|msg[2] fails when msg[2] >= 0x80 (sign-extends to 0xFFFFFFxx).
 *         Fix: ((msg[1]&0xFF)<<8)|(msg[2]&0xFF)
 *
 * Bug #11: capability_class_code is assigned before subtype check, so for
 *          subtype=1 (surface), airborne-format parsing runs before exception.
 */
public class AirborneOperationalStatusV1MsgTest {

    @Test
    public void testCapabilityCodeWithHighByteBits() throws Exception {
        // Bug #3: When ME byte[2] (capability low byte) has high bit set,
        // byte sign extension corrupts the capability_class_code.
        //
        // Construct a TC31 sub0 message where msg[2] = 0x80:
        // ME = F8 00 80 00 00 29 00
        //   msg[0] = F8: TC=31, subtype=0
        //   msg[1] = 00: capability high byte (bits 15-8)
        //   msg[2] = 80: capability low byte (bit 7 set)
        //   msg[3] = 00: operational_mode high byte
        //   msg[4] = 00: operational_mode low byte
        //   msg[5] = 29: version=1 (001), NIC_suppl=0, NACp=9 (1001)
        //   msg[6] = 00: GVA=0, SIL=0, etc.
        //
        // Expected capability_class_code = 0x0080 = 128
        // Buggy result: 0xFFFFFF80 (due to sign extension), triggers (& 0xC000) != 0 check

        // Build full 14-byte message: DF=17 (8D), ICAO=000000, ME, CRC
        // We use noCRC=true to bypass CRC check
        // 14 bytes: DF(1) + ICAO(3) + ME(7) + CRC(3)
        byte[] msg = tools.hexStringToByteArray("8D000000F8008000002900000000");
        try {
            AirborneOperationalStatusV1Msg status = new AirborneOperationalStatusV1Msg(msg);
            // If we get here without exception, the capability was parsed correctly
            assertEquals(1, status.getVersion());
        } catch (BadFormatException e) {
            // Bug #3: The sign extension causes capability_class_code & 0xC000 != 0
            // which incorrectly throws "Unknown capability class code!"
            if (e.getMessage().contains("capability class code")) {
                fail("Bug #3: Byte sign extension in capability_class_code. " +
                     "msg[2]=0x80 caused sign extension to 0xFFFFFF80. " + e.getMessage());
            }
            throw e;
        }
    }

    @Test
    public void testOperationalModeCodeWithHighByte() throws Exception {
        // Same sign extension issue for operational_mode_code (msg[3]<<8|msg[4])
        // When msg[4] >= 0x80, sign extension corrupts the value.
        // ME = F8 00 00 00 80 29 00
        byte[] msg = tools.hexStringToByteArray("8D000000F8000000802900000000");
        try {
            AirborneOperationalStatusV1Msg status = new AirborneOperationalStatusV1Msg(msg);
            assertEquals(1, status.getVersion());
        } catch (BadFormatException e) {
            if (e.getMessage().contains("capability class code")) {
                fail("Bug #3 triggered by operational_mode_code sign extension: " + e.getMessage());
            }
            throw e;
        }
    }

    @Test
    public void testValidVersion1Message() throws Exception {
        // This test uses a message where all bytes are < 0x80, so no sign extension issue
        // ME = F8 00 02 00 49 29 00
        byte[] msg = tools.hexStringToByteArray("8D000000F8000200492900000000");
        try {
            AirborneOperationalStatusV1Msg status = new AirborneOperationalStatusV1Msg(msg);
            assertEquals(1, status.getVersion());
            assertEquals(9, status.getNACp());
        } catch (Exception e) {
            // This message should parse without error
            fail("Valid v1 message should parse: " + e.getMessage());
        }
    }
}
