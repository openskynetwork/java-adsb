package org.opensky.libadsb.msgs;

import org.opensky.libadsb.exceptions.BadFormatException;

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
	 * @param raw_message raw short air-air acas reply as byte array
	 * @throws BadFormatException if message is not altitude reply or
	 * contains wrong values.
	 */
	public ShortACAS(byte[] raw_message) throws BadFormatException {
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
	 * true airspeed capability and TCAS capabilities. Capabilities are:<br>
	 *     <ul>
	 *         <li>code 2: On-board TCAS with resolution capability inhibited</li>
	 *         <li>code 3: On-board TCAS with vertical-only resolution capability</li>
	 *         <li>code 4: On-board TCAS with vertical and horizontal resolution capability</li>
	 *     </ul>
	 * @return the air-to-air reply information according to 3.1.2.8.2.2
	 * @see #getMaximumAirspeed()
	 * @see #hasOperatingACAS()
	 * @see #hasHorizontalResolutionCapability()
	 * @see #hasVerticalResolutionCapability()
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
	 * @return the maximum airspeed in kn as specified in ICAO Annex 10V4 3.1.2.8.2.2<br>
	 * null if unknown<br>Integer.MAX_VALUE if unbound
	 */
	public Integer getMaximumAirspeed() {
		return decodeMaximumAirspeed(getReplyInformation());
	}

	static Integer decodeMaximumAirspeed(byte reply_information) {
		switch (reply_information) {
		case 9: return 75;
		case 10: return 150;
		case 11: return 300;
		case 12: return 600;
		case 13: return 1200;
		case 14: return Integer.MAX_VALUE;
		default: return null;
		}
	}

	/**
	 * @return true if vertical resolution capability announced; false if explicitly not available; null if information
	 * not provided in this reply
	 *
	 */
	public Boolean hasVerticalResolutionCapability () {
		switch (reply_information) {
			case 0:
			case 1:
				return false;
			case 3:
			case 4:
				return true;
			default:
				return null;
		}
	}

	/**
	 * @return true if horizontal resolution capability announced; false if explicitly not available; null if
	 * information not provided in this reply
	 *
	 */
	public Boolean hasHorizontalResolutionCapability () {
		switch (reply_information) {
			case 0:
			case 1:
			case 3:
				return false;
			case 4:
				return true;
			default:
				return null;
		}
	}

	/**
	 * @return The 13 bits altitude code (see ICAO Annex 10 V4)
	 */
	public short getAltitudeCode() {
		return altitude_code;
	}

	/**
	 * @return the decoded altitude in feet
	 */
	public Integer getAltitude() {
		return AltitudeReply.decodeAltitude(altitude_code);
	}

	public String toString() {
		return super.toString()+"\n"+
				"Short air-air ACAS reply:\n"+
				"\tAircraft is airborne:\t\t\t\t"+isAirborne()+"\n"+
				"\tHas cross-link capability:\t\t\t"+hasCrossLinkCapability()+"\n"+
				"\tSensitivity level:\t\t\t\t\t"+getSensitivityLevel()+"\n"+
				"\tHas operating ACAS:\t\t\t\t\t"+hasOperatingACAS()+"\n"+
				"\tMaximum airspeed:\t\t\t\t\t"+getMaximumAirspeed()+"\n"+
				"\tVertical resolution capability:\t\t"+hasVerticalResolutionCapability()+"\n"+
				"\tHorizontal resolution capability:\t"+hasHorizontalResolutionCapability()+"\n"+
				"\tAltitude:\t\t\t\t\t\t\t"+getAltitude();
	}

}
