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
 * Decoder for ADS-B emergency and priority status messages
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class EmergencyOrPriorityStatusMsg extends ExtendedSquitter implements Serializable {
	
	private static final long serialVersionUID = 7197836328522321081L;
	private byte msgsubtype;
	private byte emergency_state;
	private short mode_a_code;

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected EmergencyOrPriorityStatusMsg() { }

	/**
	 * @param raw_message raw ADS-B aircraft status message as hex string
	 * @throws BadFormatException if message has wrong format
	 */
	public EmergencyOrPriorityStatusMsg(String raw_message) throws BadFormatException {
		this(new ExtendedSquitter(raw_message));
	}
	
	/**
	 * @param squitter extended squitter which contains this emergency or priority status msg
	 * @throws BadFormatException if message has wrong format
	 */
	public EmergencyOrPriorityStatusMsg(ExtendedSquitter squitter) throws BadFormatException {
		super(squitter);
		setType(subtype.ADSB_EMERGENCY);
		
		if (this.getFormatTypeCode() != 28) {
			throw new BadFormatException("Emergency and Priority Status messages must have typecode 28.");
		}
		
		byte[] msg = this.getMessage();
		
		msgsubtype = (byte) (msg[0]&0x7);
		if (msgsubtype != 1) {
			throw new BadFormatException("Emergency and priority status reports have subtype 1.");
		}
		
		emergency_state = (byte) ((msg[1]&0xFF)>>>5);
		mode_a_code = (short) (msg[2]|((msg[1]&0x1F)<<8));
	}
	
	/**
	 * @return the subtype code of the aircraft status report (should always be 1)
	 */
	public byte getSubtype() {
		return msgsubtype;
	}

	/**
	 * @return the emergency state code (see DO-260B, Appendix A, Page A-83)
	 */
	public byte getEmergencyStateCode() {
		return emergency_state;
	}

	/**
	 * @return the human readable emergency state (see DO-260B, Appendix A, Page A-83)
	 */
	public String getEmergencyStateText() {
		switch (emergency_state) {
		case 0: return "no emergency";
		case 1: return "general emergency";
		case 2: return "lifeguard/medical";
		case 3: return "minimum fuel";
		case 4: return "no communications";
		case 5: return "unlawful interference";
		case 6: return "downed aircraft";
		default: return "unknown";
		}
	}

	/**
	 * @return the four-digit Mode A (4096) code (only ADS-B version 2)
	 */
	public byte[] getModeACode() {
		// the sequence is C1, A1, C2, A2, C4, A4, ZERO, B1, D1, B2, D2, B4, D4
		int C1 = (mode_a_code>>>12)&0x1;
		int A1 = (mode_a_code>>>11)&0x1;
		int C2 = (mode_a_code>>>10)&0x1;
		int A2 = (mode_a_code>>>9)&0x1;
		int C4 = (mode_a_code>>>8)&0x1;
		int A4 = (mode_a_code>>>7)&0x1;
		int B1 = (mode_a_code>>>5)&0x1;
		int D1 = (mode_a_code>>>4)&0x1;
		int B2 = (mode_a_code>>>3)&0x1;
		int D2 = (mode_a_code>>>2)&0x1;
		int B4 = (mode_a_code>>>1)&0x1;
		int D4 = mode_a_code&0x1;
		return new byte[] {
				(byte) (A1+(A2<<1)+(A4<<2)),
				(byte) (B1+(B2<<1)+(B4<<2)),
				(byte) (C1+(C2<<1)+(C4<<2)),
				(byte) (D1+(D2<<1)+(D4<<2))};
	}

	public String toString() {
		byte[] modeA = getModeACode();
		String ret = super.toString()+"\n"+
				"Emergency & Priority Status:\n";
		ret += "\tEmergency:\t"+getEmergencyStateText()+"\n";
		ret += "\tMode A code:\t"+modeA[0]+"|"+modeA[1]+"|"+modeA[2]+"|"+modeA[3];
		
		return ret;
	}
}
