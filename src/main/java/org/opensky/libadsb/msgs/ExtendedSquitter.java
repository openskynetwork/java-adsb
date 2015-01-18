package org.opensky.libadsb.msgs;

import java.io.Serializable;

import org.opensky.libadsb.tools;
import org.opensky.libadsb.exceptions.BadFormatException;

/**
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
 * Decoder for Mode S extended squitters
 * @author Matthias Schäfer <schaefer@sero-systems.de>
 */
public class ExtendedSquitter extends ModeSReply implements Serializable {

	private static final long serialVersionUID = -7877955448285410779L;
	private byte capabilities;
	private byte[] message;
	private byte format_type_code;
	
	/**
	 * @param raw_message raw extended squitter as hex string
	 * @throws BadFormatException if message is not extended squitter or 
	 * contains wrong values.
	 */
	public ExtendedSquitter(String raw_message) throws BadFormatException {
		super(raw_message);
		
		if (getDownlinkFormat() != 17 && getDownlinkFormat() != 18) {
			throw new BadFormatException("Message is not an extended squitter!", raw_message);
		}
		
		byte[] payload = getPayload();
		capabilities = (byte) (payload[0] & 0x7);
		
		// extract ADS-B message
		message = new byte[7];
		for (int i=0; i<7; i++)
			message[i] = payload[i+3];
		
		format_type_code = (byte) ((message[0] >>> 3) & 0x1F);
	}

	/**
	 * @return The emitter's capabilities (see ICAO Annex 10 V4)
	 */
	public byte getCapabilities() {
		return capabilities;
	}

	/**
	 * @return The message's format type code (see ICAO Annex 10 V4)
	 */
	public byte getFormatTypeCode() {
		return format_type_code;
	}

	/**
	 * @return The message as 7-byte array
	 */
	public byte[] getMessage() {
		return message;
	}
	
	public String toString() {
		return super.toString()+"\n"+
				"Extended Squitter:\n"+
				"\tFormat type code:\t"+getFormatTypeCode()+"\n"+
				"\tCapabilities:\t\t"+getCapabilities()+"\n"+
				"\tMessage field:\t\t"+tools.toHexString(getMessage());
	}

}
