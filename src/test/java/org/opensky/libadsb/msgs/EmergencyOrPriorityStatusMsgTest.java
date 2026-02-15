package org.opensky.libadsb.msgs;

import org.junit.Test;
import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.tools;

import static org.junit.Assert.*;

/**
 * Tests for emergency and priority status messages (TC 28).
 * Expected values cross-validated with pyModeS 2.21.1 and lib1090.
 *
 * Discovered bug: getModeACode() has byte sign extension issue.
 * mode_a_code = (short) (msg[2]|((msg[1]&0x1F)<<8))
 * msg[2] is not masked with &0xFF, causing sign extension when msg[2] >= 0x80.
 * lib1090 fix: (short) (((msg[1]&0x1F)<<8) | (msg[2] & 0xFF))
 */
public class EmergencyOrPriorityStatusMsgTest {

    // 8DA2C1B6E112B600000000760759
    // pyModeS: is_emergency=false, emergency_state=0, squawk="6513"
    // lib1090: emergency_state=0, squawk="6513"
    // java-adsb BUGGY: squawk="7573" due to byte sign extension

    @Test
    public void testEmergencyState() throws Exception {
        EmergencyOrPriorityStatusMsg msg = (EmergencyOrPriorityStatusMsg) Decoder.genericDecoder("8DA2C1B6E112B600000000760759");
        assertEquals(0, msg.getEmergencyStateCode());
        assertEquals("no emergency", msg.getEmergencyStateText());
    }

    @Test
    public void testSquawkCode() throws Exception {
        // Consensus: squawk = 6513 (pyModeS and lib1090 agree)
        // java-adsb currently returns 7573 due to byte sign extension bug
        EmergencyOrPriorityStatusMsg msg = (EmergencyOrPriorityStatusMsg) Decoder.genericDecoder("8DA2C1B6E112B600000000760759");
        byte[] modeA = msg.getModeACode();
        String squawk = "" + modeA[0] + modeA[1] + modeA[2] + modeA[3];
        assertEquals("6513", squawk);
    }

    @Test
    public void testIcao() throws Exception {
        EmergencyOrPriorityStatusMsg msg = (EmergencyOrPriorityStatusMsg) Decoder.genericDecoder("8DA2C1B6E112B600000000760759");
        assertEquals("a2c1b6", tools.toHexString(msg.getIcao24()));
    }

    @Test
    public void testTypeCode28() throws Exception {
        EmergencyOrPriorityStatusMsg msg = (EmergencyOrPriorityStatusMsg) Decoder.genericDecoder("8DA2C1B6E112B600000000760759");
        assertEquals(28, msg.getFormatTypeCode());
    }
}
