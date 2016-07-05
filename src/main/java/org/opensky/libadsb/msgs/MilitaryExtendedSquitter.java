package org.opensky.libadsb.msgs;

import java.io.Serializable;

import org.apache.commons.lang.ArrayUtils;
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
 * Decoder for Mode S military extended squitters (DF19)<br>
 * Note: this format is practically unspecified
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class MilitaryExtendedSquitter extends ModeSReply implements Serializable {

	private static final long serialVersionUID = -7877955448285410779L;
	
	private byte[] message;
	private byte application_code;

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected MilitaryExtendedSquitter() { }

	/**
	 * @param raw_message raw military extended squitter as hex string
	 * @throws BadFormatException if message is not military extended squitter or 
	 * contains wrong values.
	 */
	public MilitaryExtendedSquitter(String raw_message) throws BadFormatException {
		this(new ModeSReply(raw_message));
	}
	
	/**
	 * @param reply Mode S reply containing this military extended squitter
	 * @throws BadFormatException if message is not a military extended squitter or 
	 * contains wrong values.
	 */
	public MilitaryExtendedSquitter(ModeSReply reply) throws BadFormatException {
		super(reply);
		setType(subtype.MILITARY_EXTENDED_SQUITTER);
		
		if (getDownlinkFormat() != 19) {
			throw new BadFormatException("Message is not a military extended squitter!");
		}
		
		message = ArrayUtils.addAll(getPayload(), getParity());
		application_code = getFirstField();
	}
	
	/**
	 * Copy constructor for subclasses
	 * 
	 * @param squitter instance of MilitaryExtendedSquitter to copy from
	 */
	public MilitaryExtendedSquitter(MilitaryExtendedSquitter squitter) {
		super(squitter);
		
		message = squitter.getMessage();
		application_code = squitter.getApplicationCode();
	}

	/**
	 * @return The message as 13-byte array
	 */
	public byte[] getMessage() {
		return message;
	}
	
	/**
	 * @return the application code from the AF field
	 */
	public byte getApplicationCode() {
		return application_code;
	}
	
	public String toString() {
		return super.toString()+"\n"+
				"Extended Squitter:\n"+
				"\tApplication Code: "+getApplicationCode()+"\n"+
				"\tMessage field:\t\t"+tools.toHexString(getMessage());
	}

}
