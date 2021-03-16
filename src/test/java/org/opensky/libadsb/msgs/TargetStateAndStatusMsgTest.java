package org.opensky.libadsb.msgs;

import org.junit.Test;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.UnspecifiedFormatError;
import org.opensky.libadsb.tools;

import static org.junit.Assert.*;

/**
 * @author Markus Fuchs (fuchs@opensky-network.org)
 */
public class TargetStateAndStatusMsgTest {

	// A TSS report observed in reality, decomposed by single bits
	public static final String TSS_WITHOUT_HEADING =
				// 10001 => DF 17
				//   101 => FF (firs field, node needed here)
				"8d" +

				// ICAO 24 bit address
				"89653e" +

				//       11101  => type code 29
				//          01  => Subtype 1
				//           0  => SIL supplement
				//           0  => Selected altitude type
				// 10011000100 => Selected altitude
				"ea4c4" +

				// 100001011 => barometric pressure setting
				//         0 => selected heading status
				//         0 => selected heading sign
				//  00000000 => selected heading
				"85801" +

				// 1001 => NACp
				//    1 => NICbaro
				//   11 => SIL
				//    1 => Status mode bits
				//    1 => Autopilot
				//    1 => VNAV mode
				//    0 => Altitude hold
				//    0 => Reserved ADS-R
				//    0 => Approach mode
				//    1 => TCAS operational
				//    1 => LNAV mode
				//   00 => Reserved
				"3f8c" +

				// parity bits (valid, but not tested here)
				"6472e1";

	// Modified message from above with heading bits set, angle < 180°
	public static final String TSS_HEADING_LT_180_DEG =
			// see above
			"8d" +
			"89653e" +
			"ea4c4" +
			// 100001011 => barometric pressure setting
			//         1 => selected heading status
			//         0 => selected heading sign
			//  00100000 => selected heading
			"85c41" +
			// see above
			"3f8c" +
			// parity bits (invalid, not tested here)
			"6472e1";

	// Modified message from above with heading bits set, angle > 180°
	public static final String TSS_HEADING_GT_180_DEG =
			// see above
			"8d" +
			"89653e" +
			"ea4c4" +
			// 100001011 => barometric pressure setting
			//         1 => selected heading status
			//         0 => selected heading sign
			//  00100000 => selected heading
			"85e41" +
			// see above
			"3f8c" +
			// parity bits (invalid, not tested here)
			"6472e1";

	// Modified message from above with reserved bits set
	public static final String INVALID_TSS =
			// see above
			"8d" +
			"89653e" +
			"ea4c4" +
			"85801" +
			// 1001 => NACp
			//    1 => NICbaro
			//   11 => SIL
			//    1 => Status mode bits
			//    1 => Autopilot
			//    1 => VNAV mode
			//    0 => Altitude hold
			//    0 => Reserved ADS-R
			//    0 => Approach mode
			//    1 => TCAS operational
			//    1 => LNAV mode
			//   11 => Reserved
			"3f8f" +
			// parity bits (invalid, not tested here)
			"6472e1";

	public static final String TSS_WITH_ME11_BIT_SET = "8d" +
			// ICAO 24 bit address
			"4d0131" +
			//
			//       11101  => type code 29
			//          01  => Subtype 1
			//           0  => SIL supplement
			//           0  => Selected altitude type
			// 01110001011 => Selected altitude
			"ea38b" +
			"866c33c085693ec";

	@Test
	public void testTssWithoutHeading() throws UnspecifiedFormatError, BadFormatException {
		final TargetStateAndStatusMsg tss = new TargetStateAndStatusMsg(tools.hexStringToByteArray(TSS_WITHOUT_HEADING));

		assertEquals("89653e", tools.toHexString(tss.getIcao24()));
		assertEquals(17, tss.getDownlinkFormat());

		assertEquals(29, tss.getFormatTypeCode());
		assertFalse(tss.hasSILSupplement());
		assertFalse(tss.isFMSSelectedAltitude());
		assertTrue(tss.hasSelectedAltitudeInfo());

		assertEquals(39008, tss.getSelectedAltitude().intValue());
		assertEquals(212.8, tss.getBarometricPressureSetting(), 0.0001);
		assertFalse(tss.hasSelectedHeadingInfo());
		assertNull(tss.getSelectedHeading());

		assertEquals(9, tss.getNACp());
		assertTrue(tss.getBarometricAltitudeIntegrityCode());
		assertEquals(3, tss.getSIL());
		assertTrue(tss.hasModeInfo());
		assertTrue(tss.hasAutopilotEngaged());
		assertTrue(tss.hasVNAVModeEngaged());
		assertFalse(tss.hasActiveAltitudeHoldMode());
		assertFalse(tss.hasActiveApproachMode());
		assertTrue(tss.hasOperationalTCAS());
	}

	@Test
	public void testTssWithHeadingLt180Degrees() throws UnspecifiedFormatError, BadFormatException {
		final TargetStateAndStatusMsg tss = new TargetStateAndStatusMsg(tools.hexStringToByteArray(TSS_HEADING_LT_180_DEG));

		assertEquals("89653e", tools.toHexString(tss.getIcao24()));
		assertEquals(17, tss.getDownlinkFormat());

		assertEquals(29, tss.getFormatTypeCode());
		assertFalse(tss.hasSILSupplement());
		assertFalse(tss.isFMSSelectedAltitude());
		assertTrue(tss.hasSelectedAltitudeInfo());

		assertEquals(39008, tss.getSelectedAltitude().intValue());
		assertEquals(212.8, tss.getBarometricPressureSetting(), 0.0001);
		assertTrue(tss.hasSelectedHeadingInfo());
		assertEquals(32 * 0.703125, tss.getSelectedHeading(), 0.0001);

		assertEquals(9, tss.getNACp());
		assertTrue(tss.getBarometricAltitudeIntegrityCode());
		assertEquals(3, tss.getSIL());
		assertTrue(tss.hasModeInfo());
		assertTrue(tss.hasAutopilotEngaged());
		assertTrue(tss.hasVNAVModeEngaged());
		assertFalse(tss.hasActiveAltitudeHoldMode());
		assertFalse(tss.hasActiveApproachMode());
		assertTrue(tss.hasOperationalTCAS());
	}

	@Test
	public void testTssWithHeadingGt180Degrees() throws UnspecifiedFormatError, BadFormatException {
		final TargetStateAndStatusMsg tss = new TargetStateAndStatusMsg(tools.hexStringToByteArray(TSS_HEADING_GT_180_DEG));

		assertEquals("89653e", tools.toHexString(tss.getIcao24()));
		assertEquals(17, tss.getDownlinkFormat());

		assertEquals(29, tss.getFormatTypeCode());
		assertFalse(tss.hasSILSupplement());
		assertFalse(tss.isFMSSelectedAltitude());
		assertTrue(tss.hasSelectedAltitudeInfo());

		assertEquals(39008, tss.getSelectedAltitude().intValue());
		assertEquals(212.8, tss.getBarometricPressureSetting(), 0.0001);
		assertTrue(tss.hasSelectedHeadingInfo());
		assertEquals(180 + 32 * 0.703125, tss.getSelectedHeading(), 0.0001);

		assertEquals(9, tss.getNACp());
		assertTrue(tss.getBarometricAltitudeIntegrityCode());
		assertEquals(3, tss.getSIL());
		assertTrue(tss.hasModeInfo());
		assertTrue(tss.hasAutopilotEngaged());
		assertTrue(tss.hasVNAVModeEngaged());
		assertFalse(tss.hasActiveAltitudeHoldMode());
		assertFalse(tss.hasActiveApproachMode());
		assertTrue(tss.hasOperationalTCAS());
	}

	@Test
	public void testInvalidReservedBits_shouldThrowBadFormatException() {
		try {
			final TargetStateAndStatusMsg tss = new TargetStateAndStatusMsg(tools.hexStringToByteArray(INVALID_TSS));
			fail();
		} catch (BadFormatException e) {
			// NOP
		} catch (UnspecifiedFormatError unspecifiedFormatError) {
			fail();
		}

	}

}