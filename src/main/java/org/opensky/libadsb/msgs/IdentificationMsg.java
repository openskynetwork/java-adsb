package org.opensky.libadsb.msgs;

import java.io.Serializable;

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
 * Decoder for ADS-B identification messages
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class IdentificationMsg extends ExtendedSquitter implements Serializable {

	private static final long serialVersionUID = -4395826778354619987L;
	private byte emitter_category;
	private byte[] identity;
	
	/**
	 * Maps ADS-B encoded to readable characters
	 * @param digit encoded digit
	 * @return readable character
	 */
	private static char mapChar (byte digit) {
		if (digit>0 && digit<27) return (char) ('A'+digit-1);
		else if (digit>47 && digit<58) return (char) ('0'+digit-48);
		else return ' ';
	}
	
	/**
	 * Maps ADS-B encoded to readable characters
	 * @param digits array of encoded digits
	 * @return array of decoded characters
	 */
	private static char[] mapChar (byte[] digits) {
		char[] result = new char[digits.length];
		
		for (int i=0; i<digits.length; i++)
			result[i] = mapChar(digits[i]);
		
		return result;
	}

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected IdentificationMsg() { }

	/**
	 * @param raw_message the identification message in hex representation
	 * @throws BadFormatException if message has the wrong typecode
	 */
	public IdentificationMsg(String raw_message) throws BadFormatException {
		this(new ExtendedSquitter(raw_message));
	}

	/**
	 * @param squitter extended squitter which contains this identification msg
	 * @throws BadFormatException if message has the wrong typecode
	 */
	public IdentificationMsg(ExtendedSquitter squitter) throws BadFormatException {
		super(squitter);
		setType(subtype.ADSB_IDENTIFICATION);
		
		if (getFormatTypeCode() < 1 || getFormatTypeCode() > 4) {
			throw new BadFormatException("Identification messages must have typecode of 1-4.");
		}
		
		byte[] msg = this.getMessage();
		emitter_category = (byte) (msg[0] & 0x7);
		
		// extract identity
		identity = new byte[8];
		int byte_off, bit_off;
		for (int i=8; i>=1; i--) {
			// calculate offsets
			byte_off = (i*6)/8; bit_off = (i*6)%8;
			
			// char aligned with byte?
			if (bit_off == 0) identity[i-1] = (byte) (msg[byte_off]&0x3F);
			else {
				++byte_off;
				identity[i-1] = (byte) (msg[byte_off]>>>(8-bit_off)&(0x3F>>>(6-bit_off)));
				// should we add bits from the next byte?
				if (bit_off < 6) identity[i-1] |= msg[byte_off-1]<<bit_off&0x3F;
			}
		}
	}

	/**
	 * @return the emitter's category (numerical)
	 */
	public byte getEmitterCategory() {
		return emitter_category;
	}

	/**
	 * @return the call sign as 8 characters array
	 */
	public char[] getIdentity() {
		return mapChar(identity);
	}
	
	/**
	 * @return the decription of the emitter's category according to
	 *         the ADS-B message format specification
	 */
	public String getCategoryDescription () {
		// category descriptions according
		// to the ADS-B specification
		String[][] categories = {{
			"No ADS-B Emitter Category Information",
			"Light (< 15500 lbs)",
			"Small (15500 to 75000 lbs)",
			"Large (75000 to 300000 lbs)",
			"High Vortex Large (aircraft such as B-757)",
			"Heavy (> 300000 lbs)",
			"High Performance (> 5g acceleration and 400 kts)",
			"Rotorcraft"
		},{
			"No ADS-B Emitter Category Information",
			"Glider / sailplane",
			"Lighter-than-air",
			"Parachutist / Skydiver",
			"Ultralight / hang-glider / paraglider",
			"Reserved",
			"Unmanned Aerial Vehicle",
			"Space / Trans-atmospheric vehicle",
		},{
			"No ADS-B Emitter Category Information",
			"Surface Vehicle – Emergency Vehicle",
			"Surface Vehicle – Service Vehicle",
			"Point Obstacle (includes tethered balloons)",
			"Cluster Obstacle",
			"Line Obstacle",
			"Reserved",
			"Reserved"
		},{
			"Reserved",
			"Reserved",
			"Reserved",
			"Reserved",
			"Reserved",
			"Reserved",
			"Reserved",
			"Reserved"
		}};
		
		return categories[4-getFormatTypeCode()][emitter_category];
	}

	/* (non-Javadoc)
	 * @see org.opensky.libadsb.ExtendedSquitter#toString()
	 */
	public String toString() {
		return super.toString()+"\n"+
				"Identification:\n"+
				"\tEmitter category:\t"+getCategoryDescription()+" ("+getEmitterCategory()+")\n"+
				"\tCallsign:\t\t"+new String(getIdentity());
	}
	
}
