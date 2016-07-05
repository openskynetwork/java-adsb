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
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class AllCallReply extends ModeSReply implements Serializable {

	private static final long serialVersionUID = -1156158096293306435L;
	
	private byte capabilities;
	private byte[] interrogator; // 3 bytes

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected AllCallReply() { }

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
	 * @return The emitter's capabilities (see ICAO Annex 10 V4, 3.1.2.5.2.2.1)
	 */
	public byte getCapabilities() {
		return capabilities;
	}

	/**
	 * Some receivers already subtract the crc checksum
	 * from the parity field right after reception.
	 * In that case, use {@link #getParity()} to get the interrogator ID.<br><br>
	 * Note: Use {@link #hasValidInterrogatorID()} to check the validity of this field.
	 * @return the interrogator ID as a 3-byte array
	 */
	public byte[] getInterrogatorID() {
		return interrogator;
	}
	
	/**
	 * Note: this can be used as an accurate check whether the all call reply
	 * has been received correctly without knowing the interrogator in advance.
	 * @return true if the interrogator ID is conformant with Annex 10 V4
	 */
	public boolean hasValidInterrogatorID() {
		assert(interrogator.length == 3);
		
		// 3.1.2.3.3.2
		// the first 17 bits have to be zero
		if (interrogator[0] != 0 ||
				interrogator[1] != 0 ||
				(interrogator[2]&0x80) != 0)
			return false;
		
		int cl = (interrogator[2]>>4)&0x7;
		
		// 3.1.2.5.2.1.3
		// code label is only defined for 0-4
		if (cl>4) return false;
		
		// Note: seems to be used by ACAS
//		int ii = interrogator[2]&0xF;
//		// 3.1.2.5.2.1.2.4
//		// surveillance identifier of 0 shall never be used
//		if (cl>0 && ii==0) return false;
		
		return true;
	}
	
	public String toString() {
		return super.toString()+"\n"+
				"All-call Reply:\n"+
				"\tCapabilities:\t\t"+getCapabilities()+"\n"+
				"\tValid Interrogator ID:\t\t"+hasValidInterrogatorID()+"\n"+
				"\tInterrogator:\t\t"+tools.toHexString(getInterrogatorID());
	}

}
