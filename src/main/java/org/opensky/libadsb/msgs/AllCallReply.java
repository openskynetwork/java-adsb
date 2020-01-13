package org.opensky.libadsb.msgs;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.tools;

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
 * Decoder for Mode S all-call replies
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class AllCallReply extends ModeSReply implements Serializable {

	private static final long serialVersionUID = -1156158096293306435L;

	private byte capabilities;
	private byte[] parity_interrogator; // 3 bytes
	private byte code_label;

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
     * @param raw_message raw all-call reply as byte array
     * @throws BadFormatException if message is not all-call reply or
     * contains wrong values.
     */
    public AllCallReply(byte[] raw_message) throws BadFormatException {
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
		this.parity_interrogator = tools.xor(calcParity(), getParity());

		code_label = (byte) ((parity_interrogator[2]>>4)&0x7);
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
	 * Note: Use {@link #hasValidInterrogatorCode()} to check the validity of this field.
	 * @return the interrogator code which can either be the interrogator id or the surveillance id.
	 *         Check {@link #isSurveillanceID()} for interpretation of the result.
	 */
	public byte getInterrogatorCode() {

		switch (code_label) {
			case 0:
			case 1:
				return (byte) (parity_interrogator[2]&0xF);
			case 2:
				return (byte) (parity_interrogator[2]&0xF + 16);
			case 3:
				return (byte) (parity_interrogator[2]&0xF + 32);
			default: // 4 and >= 4 (illegal)
				return (byte) (parity_interrogator[2]&0xF + 48);
		}

	}

	/**
	 * If true, {@link #getInterrogatorCode()} returns the SI-Code, otherwise it returns the II-Code.
	 * @return true if the interrogator has a surveillance identifier, false if it has an interrogator identifier.
	 */
	public boolean isSurveillanceID() {
		return code_label > 0;
	}

	/**
	 * Coding of the 3 bit code label (DL) is:<br>
	 * <ul>
	 * <li>000 - signifies that the IC field contains the II code</li>
	 * <li>001 - signifies that the IC field contains SI codes 1 to 15</li>
	 * <li>010 - signifies that the IC field contains SI codes 16 to 31</li>
	 * <li>011 - signifies that the IC field contains SI codes 32 to 47</li>
	 * <li>100 - signifies that the IC field contains SI codes 48 to 63</li>
	 * </ul>
	 * @return code label
	 */
	public byte getCodeLabel () {
		return code_label;
	}

	/**
	 * Note: this can be used as an accurate check whether the all call reply
	 * has been received correctly without knowing the interrogator in advance.
	 * @return true if the interrogator ID is conformant with Annex 10 V4
	 */
	public boolean hasValidInterrogatorCode() {
		assert(parity_interrogator.length == 3);

		// 3.1.2.3.3.2
		// the first 17 bits have to be zero
		if (parity_interrogator[0] != 0 || parity_interrogator[1] != 0 || (parity_interrogator[2]&0x80) != 0)
			return false;

		// 3.1.2.5.2.1.3
		// code label is only defined for 0-4
		if (code_label > 4) return false;

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
				"\tValid Interrogator ID:\t\t"+hasValidInterrogatorCode()+"\n"+
				(isSurveillanceID() ? "\tSI-Code:\t\t" : "\tII-Code:\t\t")+
				getInterrogatorCode();
	}

}
