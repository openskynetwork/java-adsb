package org.opensky.libadsb.msgs;

import org.junit.Test;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.UnspecifiedFormatError;
import org.opensky.libadsb.tools;

import static org.junit.Assert.*;


/**
 * @author Markus Fuchs (fuchs@opensky-network.org)
 */
public class OperationalStatusMsgTest {

	// Airborne operational status message observed in the wild
	public static final String A_OPSTAT_V2 = "8d" +
			// ICAO 24 bit address
			"4d0131" +
			// 11111 => typecode (31)
			//   000 => subtype (1)
			"f8" +
			// 10000100000000 => capability class codes
			"2100" +
			// 00001000000000 => operational mode codes
			"0200" +
			//  010 => MOPS version
			//    0 => NIC suppl A
			// 1001 => NACp
			"49" +
			//        10 => GVA
			//        11 => SIL
			//         1 => NICbaro
			//         0 => HRD
			//         0 => SILsuppl
			//         0 => Reserved
			"b8" +
			// parity (correct, not test here)
			"209514";

	@Test
	public void testDecodeAirborneOpstat() throws UnspecifiedFormatError, BadFormatException {
		final AirborneOperationalStatusV1Msg opstat = new AirborneOperationalStatusV2Msg(A_OPSTAT_V2);

		assertEquals("4d0131", tools.toHexString(opstat.getIcao24()));
		assertEquals(31, opstat.getFormatTypeCode());

		assertEquals(2, opstat.getVersion());
		assertFalse(opstat.hasNICSupplementA());
		assertEquals(9, opstat.getNACp());

		assertEquals(2, opstat.getGVA());
		assertEquals(3, opstat.getSIL());
		assertTrue(opstat.getBarometricAltitudeIntegrityCode());
		assertFalse(opstat.getHorizontalReferenceDirection());
	}
}
