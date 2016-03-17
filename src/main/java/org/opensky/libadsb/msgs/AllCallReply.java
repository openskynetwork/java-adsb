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
 * Decoder for Mode S all-call replies
 * @author Matthias Schäfer <schaefer@opensky-network.org>
 */
public class AllCallReply extends ModeSReply implements Serializable {

	private static final long serialVersionUID = -1156158096293306435L;
	
	private byte capabilities;
	private byte[] interrogator; // 3 bytes
	
	/**
	 * @param raw_message raw all-call reply as hex string
	 * @throws BadFormatException if message is not all-call reply or 
	 * contains wrong values.
	 */
	public AllCallReply(String raw_message) throws BadFormatException {
		this(new ModeSReply(raw_message));
	}
	
	/**
	 * @param reply Mode S reply containing this all-call reply
	 * @throws BadFormatException if message is not all-call reply or 
	 * contains wrong values.
	 */
	public AllCallReply(ModeSReply reply) throws BadFormatException {
		super(reply);
		setType(subtype.ALL_CALL_REPLY);
		
		if (getDownlinkFormat() != 11) {
			throw new BadFormatException("Message is not an all-call reply!");
		}
		
		capabilities = getFirstField();
		
		// extract interrogator ID
		interrogator = tools.xor(calcParity(), getParity());
	}

	/**
	 * @return The emitter's capabilities (see ICAO Annex 10 V4)
	 */
	public byte getCapabilities() {
		return capabilities;
	}

	/**
	 * Some receivers already subtract the crc checksum
	 * from the parity field right after reception.
	 * In that case, use {@link #getParity() getParity} to get the interrogator ID.
	 * @return the interrogator ID as a 3-byte array
	 */
	public byte[] getInterrogatorID() {
		return interrogator;
	}
	
	public String toString() {
		return super.toString()+"\n"+
				"All-call Reply:\n"+
				"\tCapabilities:\t\t"+getCapabilities()+"\n"+
				"\tInterrogator:\t\t"+tools.toHexString(getInterrogatorID());
	}

}
