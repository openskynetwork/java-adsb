package org.opensky.libadsb.msgs;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.UnspecifiedFormatError;

import java.io.Serializable;

/*
 *  This file is part of org.opensky.libadsb.
 *
 *  org.opensky.libadsb is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  org.opensky.libadsb is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with org.opensky.libadsb.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Decoder for ADS-B operational status message as specified in DO-260 (ADS-B version 0).
 * @author Markus Fuchs (fuchs@opensky-network.org)
 */
public class OperationalStatusV0Msg extends ExtendedSquitter implements Serializable {

	private byte enroute_capabilities;

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected OperationalStatusV0Msg() { }

	/**
	 * @param raw_message The full Mode S message in hex representation
	 * @throws BadFormatException if message has the wrong typecode or ADS-B version
	 * @throws UnspecifiedFormatError if message has the wrong subtype
	 */
	public OperationalStatusV0Msg(String raw_message) throws BadFormatException, UnspecifiedFormatError {
		this(new ExtendedSquitter(raw_message));
	}

	/**
	 * @param raw_message The full Mode S message as byte array
	 * @throws BadFormatException if message has the wrong typecode or ADS-B version
	 * @throws UnspecifiedFormatError if message has the wrong subtype
	 */
	public OperationalStatusV0Msg(byte[] raw_message) throws BadFormatException, UnspecifiedFormatError {
		this(new ExtendedSquitter(raw_message));
	}

	/**
	 * @param squitter extended squitter which contains this message
	 * @throws BadFormatException  if message has the wrong typecode or ADS-B version
	 * @throws UnspecifiedFormatError if message has the wrong subtype
	 */
	public OperationalStatusV0Msg(ExtendedSquitter squitter) throws BadFormatException, UnspecifiedFormatError {
		super(squitter);
		setType(subtype.ADSB_STATUS_V0);

		if (getFormatTypeCode() != 31) {
			throw new BadFormatException("Operational status messages must have typecode 31.");
		}

		byte[] msg = this.getMessage();

		if ((msg[5]>>>5) != 0)
			throw new BadFormatException("Not a DO-260/version 0 status message.");

		byte subtype_code = (byte)(msg[0] & 0x7);
		if (subtype_code > 0) // all others are reserved
			throw new UnspecifiedFormatError("Operational status message subtype "+subtype_code+" reserved.");

		enroute_capabilities = msg[1];
		// All other capability fields are "TBD" in standard
	}

	/**
	 * DO-260 2.2.3.2.7.3.3.1
	 * @return true if TCAS is operational or unknown
	 */
	public boolean hasOperationalTCAS() {
		// first three bits zero
		return (enroute_capabilities & 0xe0) == 0;
	}

	/**
	 * DO-260 2.2.3.2.7.3.3.1
	 * @return true if CDTI is operational or unknown
	 */
	public boolean hasOperationalCDTI() {
		// status of 4th bit when first two bits zero
		return (enroute_capabilities & 0xd0) == 16;
	}

	/**
	 * the version number of the formats and protocols in use on the aircraft installation.<br>
	 * 	       0: Conformant to DO-260/ED-102 and DO-242<br>
	 * 	       1: Conformant to DO-260A and DO-242A<br>
	 * 	       2: Conformant to DO-260B/ED-102A and DO-242B<br>
	 * 	       3-7: reserved
	 * @return always 0
	 */
	public byte getVersion() {
		return 0;
	}
}
