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
 * Decoder for Mode S surveillance identify replies (DF 5)
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class IdentifyReply extends ModeSReply implements Serializable {

	private static final long serialVersionUID = -1156158096293306435L;
	
	private byte flight_status;
	private byte downlink_request;
	private byte utility_msg;
	private short identity;

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected IdentifyReply() { }

	/**
	 * @param raw_message raw identify reply as hex string
	 * @throws BadFormatException if message is not identify reply or 
	 * contains wrong values.
	 */
	public IdentifyReply(String raw_message) throws BadFormatException {
		this(new ModeSReply(raw_message));
	}
	
	/**
	 * @param reply Mode S reply which contains this identify reply
	 * @throws BadFormatException if message is not identify reply or 
	 * contains wrong values.
	 */
	public IdentifyReply(ModeSReply reply) throws BadFormatException {
		super(reply);
		setType(subtype.IDENTIFY_REPLY);
		
		if (getDownlinkFormat() != 5) {
			throw new BadFormatException("Message is not an identify reply!");
		}
		
		byte[] payload = getPayload();
		flight_status = getFirstField();
		downlink_request = (byte) ((payload[0]>>>3) & 0x1F);
		utility_msg = (byte) ((payload[0]&0x7)<<3 | (payload[1]>>>5)&0x7);
		identity = (short) ((payload[1]<<8 | (payload[2]&0xFF))&0x1FFF);
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
	 * Note: this is not the same identifier as the one contained in all-call replies.
	 * 
	 * @return the 4-bit interrogator identifier subfield of the
	 * utility message which reports the identifier of the
	 * interrogator that is reserved for multisite communications.
	 */
	public byte getInterrogatorIdentifier() {
		return (byte) ((utility_msg>>>2)&0xF);
	}
	
	/**
	 * @return the 2-bit identifier designator subfield of the
	 * utility message which reports the type of reservation made
	 * by the interrogator identified in
	 * {@link #getInterrogatorIdentifier() getInterrogatorIdentifier}.
	 * Assigned coding is:<br>
	 * <ul>
	 * <li>0 signifies no information</li>
	 * <li>1 signifies IIS contains Comm-B II code</li>
	 * <li>2 signifies IIS contains Comm-C II code</li>
	 * <li>3 signifies IIS contains Comm-D II code</li>
	 * </ul>
	 */
	public byte getIdentifierDesignator() {
		return (byte) (utility_msg&0x3);
	}

	/**
	 * @return The 13 bits identity code (Mode A code; see ICAO Annex 10 V4)
	 */
	public short getIdentityCode() {
		return identity;
	}

	/**
	 * @return The identity/Mode A code (see ICAO Annex 10 V4).
	 * Special codes are<br>
	 * <ul>
	 * <li> 7700 indicates emergency<br>
	 * <li> 7600 indicates radiocommunication failure</li>
	 * <li> 7500 indicates unlawful interference</li>
	 * <li> 2000 indicates that transponder is not yet operated</li>
	 * </ul>
	 */
	public String getIdentity() {
		int C1 = (0x1000&identity)>>>12;
		int A1 = (0x800&identity)>>>11;
		int C2 = (0x400&identity)>>>10;
		int A2 = (0x200&identity)>>>9;
		int C4 = (0x100&identity)>>>8;
		int A4 = (0x080&identity)>>>7;
		int B1 = (0x020&identity)>>>5;
		int D1 = (0x010&identity)>>>4;
		int B2 = (0x008&identity)>>>3;
		int D2 = (0x004&identity)>>>2;
		int B4 = (0x002&identity)>>>1;
		int D4 = (0x001&identity);

		String A = Integer.toString((A4<<2)+(A2<<1)+A1);
		String B = Integer.toString((B4<<2)+(B2<<1)+B1);
		String C = Integer.toString((C4<<2)+(C2<<1)+C1);
		String D = Integer.toString((D4<<2)+(D2<<1)+D1);
		
		return A+B+C+D;
	}
	
	public String toString() {
		return super.toString()+"\n"+
				"Identify Reply:\n"+
				"\tFlight status:\t\t"+getFlightStatus()+"\n"+
				"\tDownlink request:\t\t"+getDownlinkRequest()+"\n"+
				"\tUtility Message:\t\t"+getUtilityMsg()+"\n"+
				"\tIdentity:\t\t"+getIdentity();
	}

}
