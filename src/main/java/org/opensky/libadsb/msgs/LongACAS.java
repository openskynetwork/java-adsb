package org.opensky.libadsb.msgs;

import org.opensky.libadsb.bds.BinaryDataStore;
import org.opensky.libadsb.exceptions.BadFormatException;

import java.io.Serializable;
import java.util.Arrays;

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
 * Decoder for Mode S long air-air ACAS replies (DF 16)
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class LongACAS extends ModeSReply implements Serializable {

	private static final long serialVersionUID = -945389130204217847L;

	private boolean airborne;
	private byte sensitivity_level;
	private byte reply_information;
	private short altitude_code;

	private BinaryDataStore mv = null;

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected LongACAS() { }

	/**
	 * @param raw_message raw long air-to-air ACAS reply as hex string
	 * @throws BadFormatException if message is not long air-to-air ACAS reply or 
	 * contains wrong values.
	 */
	public LongACAS(String raw_message) throws BadFormatException {
		this(new ModeSReply(raw_message));
	}

	/**
	 * @param raw_message raw long air-to-air ACAS reply as byte array
	 * @throws BadFormatException if message is not long air-to-air ACAS reply or
	 * contains wrong values.
	 */
	public LongACAS(byte[] raw_message) throws BadFormatException {
		this(new ModeSReply(raw_message));
	}

	/**
	 * @param reply Mode S reply containing this long air-to-air ACAS reply
	 * @throws BadFormatException if message is not long air-to-air ACAS reply or 
	 * contains wrong values.
	 */
	public LongACAS(ModeSReply reply) throws BadFormatException {
		super(reply);
		setType(subtype.LONG_ACAS);

		if (getDownlinkFormat() != 16) {
			throw new BadFormatException("Message is not a long ACAS (air-air) message!");
		}

		byte[] payload = getPayload();
		airborne = (getFirstField()&0x4)==0;
		sensitivity_level = (byte) ((payload[0]>>>5)&0x7);
		reply_information = (byte) ((payload[0]&0x7)<<1 | (payload[1]>>>7)&0x1);
		altitude_code = (short) ((payload[1]<<8 | payload[2]&0xFF)&0x1FFF);

		mv = BinaryDataStore.parseRegister(Arrays.copyOfRange(payload, 3, payload.length), getAltitude());
	}

	/**
	 * @return true if aircraft is airborne, false if it is on the ground
	 */
	public boolean isAirborne() {
		return airborne;
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
	 * @return the maximum airspeed in kt as specified in ICAO Annex 10V4 3.1.2.8.2.2<br>
	 * null if unknown<br>Integer.MAX_VALUE if unbound
	 */
	public Integer getMaximumAirspeed() {
		return ShortACAS.decodeMaximumAirspeed(getReplyInformation());
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

	public BinaryDataStore getBinaryDataStore() {
		return mv;
	}

	public String toString() {
		return super.toString()+"\n"+
				"\tAircraft is airborne:\t\t\t\t"+isAirborne()+"\n"+
				"\tSensitivity level:\t\t\t\t\t"+getSensitivityLevel()+"\n"+
				"\tHas operating ACAS:\t\t\t\t\t"+hasOperatingACAS()+"\n"+
				"\tMaximum airspeed:\t\t\t\t\t"+getMaximumAirspeed()+"\n"+
				"\tVertical resolution capability:\t\t"+hasVerticalResolutionCapability()+"\n"+
				"\tHorizontal resolution capability:\t"+hasHorizontalResolutionCapability()+"\n"+
				"\tAltitude:\t\t\t\t\t\t\t"+getAltitude()+"\n"+
				mv.toString();

	}

}
