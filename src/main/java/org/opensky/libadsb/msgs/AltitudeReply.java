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
 * Decoder for Mode S surveillance altitude replies (DF 4)
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class AltitudeReply extends ModeSReply implements Serializable {

	private static final long serialVersionUID = -1156158096293306435L;
	
	private byte flight_status;
	private byte downlink_request;
	private byte utility_msg;
	private short altitude_code;

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected AltitudeReply() { }

	/**
	 * @param raw_message raw altitude reply as hex string
	 * @throws BadFormatException if message is not altitude reply or 
	 * contains wrong values.
	 */
	public AltitudeReply(String raw_message) throws BadFormatException {
		this(new ModeSReply(raw_message));
	}
	
	/**
	 * @param reply Mode S reply containing this altitude reply
	 * @throws BadFormatException if message is not altitude reply or 
	 * contains wrong values.
	 */
	public AltitudeReply(ModeSReply reply) throws BadFormatException {
		super(reply);
		setType(subtype.ALTITUDE_REPLY);
		
		if (getDownlinkFormat() != 4) {
			throw new BadFormatException("Message is not an altitude reply!");
		}
		
		byte[] payload = getPayload();
		flight_status = getFirstField();
		downlink_request = (byte) ((payload[0]>>>3) & 0x1F);
		utility_msg = (byte) ((payload[0]&0x7)<<3 | (payload[1]>>>5)&0x7);
		altitude_code = (short) ((payload[1]<<8 | payload[2]&0xFF)&0x1FFF);
	}

	/**
	 * Indicates alerts, whether SPI is enabled, and if the plane is on ground.
	 * @return The 3 bits flight status. The coding is:<br>
	 * <ul>
	 * <li>0 signifies no alert and no SPI, aircraft is airborne</li>
	 * <li>1 signifies no alert and no SPI, aircraft is on the ground</li>
	 * <li>2 signifies alert, no SPI, aircraft is airborne</li>
	 * <li>3 signifies alert, no SPI, aircraft is on the ground</li>
	 * <li>4 signifies alert and SPI, aircraft is airborne or on the ground</li>
	 * <li>5 signifies no alert and SPI, aircraft is airborne or on the ground</li>
	 * <li>6 reserved</li>
	 * <li>7 not assigned</li>
	 * </ul>
	 * @see #hasAlert()
	 * @see #hasSPI()
	 * @see #isOnGround()
	 */
	public byte getFlightStatus() {
		return flight_status;
	}

	/**
	 * @return whether flight status indicates alert
	 */
	public boolean hasAlert() {
		return flight_status>=2 && flight_status<=4;
	}

	/**
	 * @return whether flight status indicates special purpose indicator
	 */
	public boolean hasSPI() {
		return flight_status==4 || flight_status==5;
	}

	/**
	 * @return whether flight status indicates that aircraft is
	 * airborne or on the ground; For flight status &gt;= 4, this flag is unknown
	 */
	public boolean isOnGround() {
		return flight_status==1 || flight_status==3;
	}

	/**
	 * indicator for downlink requests
	 * @return the 5 bits downlink request. The coding is:<br>
     * <ul>
     * <li>0 signifies no downlink request</li>
	 * <li>1 signifies request to send Comm-B message</li>
	 * <li>2 reserved for ACAS</li>
	 * <li>3 reserved for ACAS</li>
	 * <li>4 signifies Comm-B broadcast message 1 available</li>
	 * <li>5 signifies Comm-B broadcast message 2 available</li>
	 * <li>6 reserved for ACAS</li>
	 * <li>7 reserved for ACAS</li>
	 * <li>8-15 not assigned</li>
	 * <li>16-31 see downlink ELM protocol (3.1.2.7.7.1)</li>
     * </ul>
	 */
	public byte getDownlinkRequest() {
		return downlink_request;
	}

	/**
	 * @return The 6 bits utility message (see ICAO Annex 10 V4)
	 */
	public byte getUtilityMsg() {
		return utility_msg;
	}
	
	/**
	 * Note that this is not the same identifier as the one contained in all-call replies.
	 * 
	 * @return the 4-bit interrogator identifier subfield of the
	 * utility message which reports the identifier of the
	 * interrogator that is reserved for multisite communications.
	 */
	public byte getInterrogatorIdentifier() {
		return (byte) ((utility_msg>>>2)&0xF);
	}
	
	/**
	 * Assigned coding is:<br>
	 * 0 signifies no information<br>
	 * 1 signifies IIS contains Comm-B II code<br>
	 * 2 signifies IIS contains Comm-C II code<br>
	 * 3 signifies IIS contains Comm-D II code<br>
	 * @return the 2-bit identifier designator subfield of the
	 * utility message which reports the type of reservation made
	 * by the interrogator identified in
	 * {@link #getInterrogatorIdentifier() getInterrogatorIdentifier}.
	 */
	public byte getIdentifierDesignator() {
		return (byte) (utility_msg&0x3);
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
				"Altitude Reply:\n"+
				"\tFlight status:\t\t"+getFlightStatus()+"\n"+
				"\tDownlink request:\t\t"+getDownlinkRequest()+"\n"+
				"\tUtility Message:\t\t"+getUtilityMsg()+"\n"+
				"\tAltitude:\t\t"+getAltitude();
	}

}
