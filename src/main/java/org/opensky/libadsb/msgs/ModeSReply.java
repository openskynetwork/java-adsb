package org.opensky.libadsb.msgs;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.tools;

import java.io.Serializable;
import java.util.Arrays;

/*
   This file is part of org.opensky.libadsb.

   org.opensky.libadsb is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   org.opensky.libadsb is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with org.opensky.libadsb.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Decoder for Mode S replies
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class ModeSReply implements Serializable {
	private static final long serialVersionUID = 5369519167589262290L;

	/*
	 * Attributes
	 */
	private byte downlink_format; // 0-31
	private byte first_field; // the 3 bits after downlink format
	private byte[] icao24; // 3 bytes
	private byte[] payload; // 3 or 10 bytes
	private byte[] parity; // 3 bytes
	private boolean noCRC;

	/**
	 * Indicator set by all specializations of this class to tell
	 * users which message format is encapsulated in this Mode S message.
	 */
	public enum subtype {
		MODES_REPLY, // unknown mode s reply
		SHORT_ACAS,
		ALTITUDE_REPLY,
		IDENTIFY_REPLY,
		ALL_CALL_REPLY,
		LONG_ACAS,
		EXTENDED_SQUITTER,
		MILITARY_EXTENDED_SQUITTER,
		COMM_B_ALTITUDE_REPLY,
		COMM_B_IDENTIFY_REPLY,
		COMM_D_ELM,
		// ADS-B subtypes
		ADSB_AIRBORN_POSITION,
		ADSB_SURFACE_POSITION,
		ADSB_AIRSPEED,
		ADSB_EMERGENCY,
		ADSB_TCAS,
		ADSB_VELOCITY,
		ADSB_IDENTIFICATION,
		ADSB_STATUS
	}
	private subtype type;

	/*
	 * Static fields and functions
	 */

	/**
	 * polynomial for the cyclic redundancy check<br>
	 * Note: we assume that the degree of the polynomial
	 * is divisible by 8 (holds for Mode S) and the msb is left out
	 */
	public static final byte[] CRC_polynomial = {
			(byte)0xFF,
			(byte)0xF4,
			(byte)0x09 // according to Annex 10 V4
	};

	/**
	 * @param msg raw message as byte array
	 * @return calculated parity field as 3-byte array. We used the implementation from<br>
	 *         http://www.eurocontrol.int/eec/gallery/content/public/document/eec/report/1994/022_CRC_calculations_for_Mode_S.pdf
	 */
	public static byte[] calcParity(byte[] msg) {
		byte[] pi = Arrays.copyOf(msg, CRC_polynomial.length);

		boolean invert;
		int byteidx, bitshift;
		for (int i = 0; i < msg.length*8; ++i) { // bit by bit
			invert = ((pi[0] & 0x80) != 0);

			// shift left
			pi[0] <<= 1;
			for (int b = 1; b < CRC_polynomial.length; ++b) {
				pi[b-1] |= (pi[b]>>>7) & 0x1;
				pi[b] <<= 1;
			}

			// get next bit from message
			byteidx = ((CRC_polynomial.length*8)+i) / 8;
			bitshift = 7-(i%8);
			if (byteidx < msg.length)
				pi[pi.length-1] |= (msg[byteidx]>>>bitshift) & 0x1;

			// xor
			if (invert)
				for (int b = 0; b < CRC_polynomial.length; ++b)
					pi[b] ^= CRC_polynomial[b];
		}

		return Arrays.copyOf(pi, CRC_polynomial.length);
	}

	public static int getExpectedLength(byte downlink_format) {
		if (downlink_format < 16) return 7;
		else return 14;
	}

	/*
	 * Constructors
	 */

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected ModeSReply() { }

	/**
	 *
	 * @param reply the bytes of the reply
	 * @param noCRC indicates whether the CRC has been subtracted from the parity field
	 * @throws BadFormatException if message has invalid length or payload does
	 * not match specification or parity has invalid length
	 */
	public ModeSReply (byte[] reply, boolean noCRC) throws BadFormatException {
		// check format invariants
		this.noCRC = noCRC;

		if (reply.length != 7 && reply.length != 14) // initial test
			throw new BadFormatException("Raw message has an invalid length of "+reply.length);

		downlink_format = reply[0];
		first_field = (byte) (downlink_format & 0x7);
		downlink_format = (byte) (downlink_format>>>3 & 0x1F);

		if (reply.length != getExpectedLength(downlink_format)) {
			throw new BadFormatException(
					String.format("Downlink format %d has length %d, but only %d bytes provided.",
							downlink_format, getExpectedLength(downlink_format), reply.length));
		}

		// extract payload
		payload = Arrays.copyOfRange(reply, 1, reply.length-3);

		// extract parity field
		parity = Arrays.copyOfRange(reply,reply.length-3, reply.length);

		// extract ICAO24 address
		icao24 = new byte[3];
		switch (downlink_format) {
			case 0: // Short air-air (ACAS)
			case 4: // Short altitude reply
			case 5: // Short identity reply
			case 16: // Long air-air (ACAS)
			case 20: // Long Comm-B, altitude reply
			case 21: // Long Comm-B, identity reply
			case 24: // Long Comm-D (ELM)
				icao24 = noCRC ? parity : tools.xor(calcParity(), parity);
				break;

			case 11: // all call replies
			case 17: case 18: // Extended squitter
				System.arraycopy(payload, 0, icao24, 0, 3);
				break;
			default: // unkown downlink format
				// leave everything 0
		}

		setType(subtype.MODES_REPLY);
	}

    /**
     * We assume the following message format:<br>
     * | DF (5) | FF (3) | Payload (24/80) | PI/AP (24) |
     *
     * @param raw_message Mode S message as byte array
     * @throws BadFormatException if message has invalid length or payload does
     * not match specification or parity has invalid length
     */
    public ModeSReply (byte[] raw_message) throws BadFormatException {
        this(raw_message, false);
    }

	/**
	 * We assume the following message format:<br>
	 * | DF (5) | FF (3) | Payload (24/80) | PI/AP (24) |
	 *
	 * @param raw_message Mode S message in hex representation
	 * @throws BadFormatException if message has invalid length or payload does
	 * not match specification or parity has invalid length
	 */
	public ModeSReply (String raw_message) throws BadFormatException {
		this(tools.hexStringToByteArray(raw_message), false);
	}

	/**
	 * We assume the following message format:<br>
	 * | DF (5) | FF (3) | Payload (24/80) | PI/AP (24) |
	 *
	 * @param raw_message Mode S message in hex representation
	 * @param noCRC indicates whether the CRC has been subtracted from the parity field
	 * @throws BadFormatException if message has invalid length or payload does
	 * not match specification or parity has invalid length
	 */
	public ModeSReply (String raw_message, boolean noCRC) throws BadFormatException {
		this(tools.hexStringToByteArray(raw_message), noCRC);
	}

	/**
	 * Copy constructor for subclasses
	 *
	 * @param reply instance of ModeSReply to copy from
	 */
	public ModeSReply (ModeSReply reply) {
		downlink_format = reply.downlink_format;
		first_field = reply.first_field;
		icao24 = reply.icao24;
		payload = reply.payload;
		parity = reply.parity;
		type = reply.type;
		noCRC = reply.noCRC;
	}

	/**
	 * @return the subtype
	 */
	public subtype getType() {
		return type;
	}

	/**
	 * @param subtype the subtype to set
	 */
	protected void setType(subtype subtype) {
		this.type = subtype;
	}

	/**
	 * @return downlink format of the Mode S reply
	 */
	public byte getDownlinkFormat() {
		return downlink_format;
	}

	/**
	 * Note: the definition of this field depends on the downlink format
	 * @return the first field (three bits after downlink format)
	 */
	public byte getFirstField() {
		return first_field;
	}

	/**
	 * @return the icao24 as an 3-byte array
	 */
	public byte[] getIcao24() {
		return icao24;
	}

	/**
	 * @return payload as 3- or 10-byte array containing the Mode S
	 * reply without the first and the last three bytes. 
	 */
	public byte[] getPayload() {
		return payload;
	}

	/**
	 * @return parity field from message as 3-byte array
	 */
	public byte[] getParity() {
		return parity;
	}

	/**
	 * @return calculates Mode S parity as 3-byte array
	 */
	public byte[] calcParity() {
		byte[] message = new byte[payload.length+1];

		message[0] = (byte) (downlink_format<<3 | first_field);
		System.arraycopy(payload, 0, message, 1, payload.length);

		return calcParity(message);
	}

	/**
	 * Re-builds the message from the fields and returns it as a hex string
	 * @return the reply as a hex string
	 */
	public String getHexMessage() {
		byte[] msg = new byte[4+payload.length];
		msg[0] = (byte) (downlink_format<<3 | first_field);
		System.arraycopy(payload, 0, msg, 1, payload.length);
		byte[] crc = noCRC ? tools.xor(getParity(), calcParity()) : getParity();
		for (int i = 0; i < 3; ++i) msg[1+payload.length+i] = crc[i];
		return tools.toHexString(msg);
	}

	/**
	 * Important note: use this method for extended
	 * squitter/ADS-B messages (DF 17, 18) only! Other messages may have
	 * their parity field XORed with an ICAO24 transponder address
	 * or an interrogator ID.
	 * @return true if parity in message matched calculated parity
	 */
	public boolean checkParity() {
		return tools.areEqual(calcParity(), getParity());
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "Mode S Reply:\n"+
				"\tDownlink format:\t"+getDownlinkFormat()+"\n"+
				"\tICAO 24-bit address:\t"+tools.toHexString(getIcao24())+"\n"+
				"\tPayload:\t\t"+tools.toHexString(getPayload())+"\n"+
				"\tParity:\t\t\t"+tools.toHexString(getParity())+"\n"+
				"\tCalculated Parity:\t"+tools.toHexString(calcParity());
	}

	@Override
	public boolean equals(Object o) {
		if (o == null) return false;
		if (o == this) return true;
		if (o.getClass() != getClass()) return false;

		ModeSReply other = (ModeSReply)o;

		// same type?
		if (this.getDownlinkFormat() != other.getDownlinkFormat())
			return false;

		// most common
		if (this.getDownlinkFormat() == 11 &&
				!tools.areEqual(this.getIcao24(), other.getIcao24()))
			return false;

		// ads-b
		if (this.getDownlinkFormat() == 17 &&
				!tools.areEqual(this.getIcao24(), other.getIcao24()))
			return false;
		if (this.getDownlinkFormat() == 18 &&
				!tools.areEqual(this.getIcao24(), other.getIcao24()))
			return false;

		// check the full payload
		if (!tools.areEqual(this.getPayload(), other.getPayload()) ||
				this.getFirstField() != other.getFirstField())
			return false;

		// and finally the parity
		if (tools.areEqual(this.getParity(), other.getParity()))
			return true;

		// Note: the following checks are necessary since some receivers set
		// the parity field to the remainder of the CRC (0 if correct)
		// while others do not touch it. This combination should be extremely
		// rare so the performance can be more or less neglected.

		if (tools.areEqual(this.getParity(), other.calcParity()))
			return true;

		if (tools.areEqual(this.calcParity(), other.getParity()))
			return true;

		if (this.getDownlinkFormat() == 11) {
			// check interrogator code
			if (tools.areEqual(tools.xor(calcParity(), getParity()),
					other.getParity()))
				return true;

			if (tools.areEqual(tools.xor(other.calcParity(),
					other.getParity()), this.getParity()))
				return true;
		}

		return tools.areEqual(this.getIcao24(), other.getParity()) ||
				tools.areEqual(this.getParity(), other.getIcao24());
	}

	@Override
	public int hashCode() {
		// same method used by String
		int sum = downlink_format<<3|first_field;
		for (int i = 0; i<payload.length; ++i)
			sum += payload[i]*31^(payload.length-i);

		byte[] effective_partiy = parity;
		if (noCRC) effective_partiy = tools.xor(parity, calcParity());

		for (int i = 0; i<effective_partiy.length; ++i)
			sum += effective_partiy[i]*31^(payload.length+effective_partiy.length-i);
		return sum;
	}
}
