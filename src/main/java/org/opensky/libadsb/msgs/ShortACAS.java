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
 * Decoder for Mode S short air-air ACAS replies (DF 0)
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class ShortACAS extends ModeSReply implements Serializable {
	
	private static final long serialVersionUID = 7201021668905726988L;
	
	private boolean airborne;
	private boolean cross_link_capability;
	private byte sensitivity_level;
	private byte reply_information;
	private short altitude_code;

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected ShortACAS() { }

	/**
	 * @param raw_message raw short air-air acas reply as hex string
	 * @throws BadFormatException if message is not altitude reply or 
	 * contains wrong values.
	 */
	public ShortACAS(String raw_message) throws BadFormatException {
		this(new ModeSReply(raw_message));
	}
	
	/**
	 * @param reply Mode S reply containing this short air-air acas reply
	 * @throws BadFormatException if message is not short air-air acas reply or 
	 * contains wrong values.
	 */
	public ShortACAS(ModeSReply reply) throws BadFormatException {
		super(reply);
		setType(subtype.SHORT_ACAS);
		
		if (getDownlinkFormat() != 0) {
			throw new BadFormatException("Message is not a short ACAS (air-air) message!");
		}
		
		byte[] payload = getPayload();
		airborne = (getFirstField()&0x4)==0;
		cross_link_capability = (getFirstField()&0x2)!=0;
		sensitivity_level = (byte) ((payload[0]>>>5)&0x7);
		reply_information = (byte) ((payload[0]&0x7)<<1 | (payload[1]>>>7)&0x1);
		altitude_code = (short) ((payload[1]<<8 | payload[2]&0xFF)&0x1FFF);
	}
	

	/**
	 * @return true if aircraft is airborne, false if it is on the ground
	 */
	public boolean isAirborne() {
		return airborne;
	}
	
	/**
	 * Note: cross-link cabability is the ability to support decoding the contents
	 * of the DS field in an interrogation with UF equals
	 * 0 and respond with the contents of the specified GICB register in the
	 * corresponding reply with DF equals 16.
	 * @return true if aircraft has the cross-link capability
	 * 

	 */
	public boolean hasCrossLinkCapability() {
		return cross_link_capability;
	}

	/**
	 * @return the sensitivity level at which ACAS is currently operating
	 */
	public byte getSensitivityLevel() {
		return sensitivity_level;
	}

	/**
	 * This field is used to report the aircraft's maximum cruising 
	 * true airspeed capability and type of reply to interrogating aircraft
	 * @return the air-to-air reply information according to 3.1.2.8.2.2
	 * @see #getMaximumAirspeed()
	 * @see #hasOperatingACAS()
	 */
	public byte getReplyInformation() {
		return reply_information;
	}
	
	/**
	 * @return whether a/c has operating ACARS (derived from reply information)
	 * @see #getReplyInformation()
	 */
	public boolean hasOperatingACAS() {
		return getReplyInformation() != 0;
	}
	
	/**
	 * @return the maximum airspeed in m/s as specified in ICAO Annex 10V4 3.1.2.8.2.2<br>
	 * null if unknown<br>Double.MAX_VALUE if unbound
	 */
	public Double getMaximumAirspeed() {
		switch (getReplyInformation()) {
		case 9: return 140/3.6;
		case 10: return 280/3.6;
		case 11: return 560/3.6;
		case 12: return 1110/3.6;
		case 13: return 2220/3.6;
		case 14: return Double.MAX_VALUE;
		default: return null;
		}
	}

	/**
	 * @return The 13 bits altitude code (see ICAO Annex 10 V4)
	 */
	public short getAltitudeCode() {
		return altitude_code;
	}

	/**
	 * This method converts a gray code encoded int to a standard decimal int
	 * @param gray gray code encoded int of length bitlength
	 *        bitlength bitlength of gray code
	 * @return radix 2 encoded integer
	 */
	private static int grayToBin(int gray, int bitlength) {
		int result = 0;
		for (int i = bitlength-1; i >= 0; --i)
			result = result|((((0x1<<(i+1))&result)>>>1)^((1<<i)&gray));
		return result;
	}
	
	/**
	 * @return the decoded altitude in meters
	 */
	public Double getAltitude() {
		// altitude unavailable
		if (altitude_code == 0) return null;
		
		boolean Mbit = (altitude_code&0x40)!=0;
		if (!Mbit) {
			boolean Qbit = (altitude_code&0x10)!=0;
			if (Qbit) { // altitude reported in 25ft increments
				int N = (altitude_code&0x0F) | ((altitude_code&0x20)>>>1) | ((altitude_code&0x1F80)>>>2);
				return (25*N-1000)*0.3048;
			}
			else { // altitude is above 50175ft, so we use 100ft increments
				
				// it's decoded using the Gillham code
				int C1 = (0x1000&altitude_code)>>>12;
				int A1 = (0x0800&altitude_code)>>>11;
				int C2 = (0x0400&altitude_code)>>>10;
				int A2 = (0x0200&altitude_code)>>>9;
				int C4 = (0x0100&altitude_code)>>>8;
				int A4 = (0x0080&altitude_code)>>>7;
				int B1 = (0x0020&altitude_code)>>>5;
				int B2 = (0x0008&altitude_code)>>>3;
				int D2 = (0x0004&altitude_code)>>>2;
				int B4 = (0x0002&altitude_code)>>>1;
				int D4 = (0x0001&altitude_code);

				// this is standard gray code
				int N500 = grayToBin(D2<<7|D4<<6|A1<<5|A2<<4|A4<<3|B1<<2|B2<<1|B4, 8);

				// 100-ft steps must be converted
				int N100 = grayToBin(C1<<2|C2<<1|C4, 3)-1;
				if (N100 == 6) N100=4;
				if (N500%2 != 0) N100=4-N100; // invert it

				return (-1200+N500*500+N100*100)*0.3048;
			}
		}
		else return null; // unspecified metric encoding
	}
	
	public String toString() {
		return super.toString()+"\n"+
				"Short air-air ACAS reply:\n"+
				"\tAircraft is airborne:\t\t"+isAirborne()+"\n"+
				"\tHas cross-link capability:\t\t"+hasCrossLinkCapability()+"\n"+
				"\tSensitivity level:\t\t"+getSensitivityLevel()+"\n"+
				"\tHas operating ACAS:\t\t"+hasOperatingACAS()+"\n"+
				"\tMaximum airspeed:\t\t"+getMaximumAirspeed()+"\n"+
				"\tAltitude:\t\t"+getAltitude();
	}

}
