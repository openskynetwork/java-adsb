package org.opensky.libadsb.msgs;

import java.io.Serializable;
import java.util.Arrays;

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
 * Decoder for Mode S replies
 * @author Matthias Schäfer <schaefer@sero-systems.de>
 */
public class ModeSReply implements Serializable {
	private static final long serialVersionUID = 5369519167589262290L;

	/*
	 * Attributes
	 */
	private byte downlink_format; // 0-31
	private byte capabilities; // three bits after the downlink format
	private byte[] icao24; // 3 bytes
	private byte[] payload; // 3 or 10 bytes
	private byte[] parity; // 3 bytes

	/*
	 * Static fields and functions
	 */

	/**
	 * polynomial for the cyclic redundancy check<br />
	 * Note: we assume that the degree of the polynomial
	 * is divisible by 8 (holds for Mode S) and the msb is left out
	 */
	public static final byte[] CRC_polynomial = {
		(byte)0xFF,
		(byte)0xF4,
		(byte)0x09 // according to Annex 10 V4
	};

	/**
	 * @return calculated parity field as 3-byte array. We used the implementation from<br />
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

	/*
	 * Constructors
	 */

	/**
	 * We assume the following message format:
	 * | DF | CA | Payload | PI/AP |
	 *   5    3    3/10      3
	 * 
	 * @param raw_message Mode S message in hex representation
	 * @throws BadFormatException if message has invalid length or payload does
	 * not match specification or parity has invalid length
	 */
	public ModeSReply (String raw_message) throws BadFormatException {
		// check format invariants
		int length = raw_message.length();
		if (length != 14 && length != 28)
			throw new BadFormatException("Raw message has invalid length", raw_message);

		downlink_format = (byte) (Short.parseShort(raw_message.substring(0, 2), 16));
		capabilities = (byte) (downlink_format & 0x7);
		downlink_format = (byte) (downlink_format>>>3 & 0x1F);

		byte[] payload = new byte[(length-8)/2];
		byte[] icao24 = new byte[3];
		byte[] parity = new byte[3];

		// decode based on format
		// TODO
		switch (downlink_format) {
		case 0: // Short air-air (ACAS)
		case 4: // Short altitude reply
		case 5: // Short identity reply
		case 16: // Long air-air (ACAS)
		case 20: // Long Comm-B, altitude reply
		case 21: // Long Comm-B, identity reply
		case 24: // Long Comm-D (ELM)
			// here we assume that AP is already the icao24
			// i.e. parity is extracted. Therefore we leave
			// parity 0
			for (int i=length-6; i<length; i+=2)
				icao24[(i-length+6)/2] = (byte) Short.parseShort(raw_message.substring(i, i+2), 16);

			// extract payload (little endian)
			for (int i=2; i<length-6; i+=2)
				payload[(i-2)/2] = (byte) Short.parseShort(raw_message.substring(i, i+2), 16);
			break;
		case 11: // Short all-call reply
		case 17: case 18: // Extended squitter
			// extract payload (little endian)
			for (int i=2; i<length-6; i+=2)
				payload[(i-2)/2] = (byte) Short.parseShort(raw_message.substring(i, i+2), 16);

			for (int i=0; i<3; i++)
				icao24[i] = payload[i];

			for (int i=length-6; i<length; i+=2)
				parity[(i-length+6)/2] = (byte) Short.parseShort(raw_message.substring(i, i+2), 16);
			break;
		default: // unkown downlink format
			// leave everything 0
		}

		// check format invariants
		if (icao24.length != 3)
			throw new BadFormatException("ICAO address too short/long", raw_message);
		if (payload.length != 3 && payload.length != 10)
			throw new BadFormatException("Payload length does not match specification", raw_message);
		if (parity.length != 3)
			throw new BadFormatException("Parity too short/long", raw_message);

		this.icao24 = icao24;
		this.payload = payload;
		this.parity = parity;
	}

	/**
	 * @return downlink format of the Mode S reply
	 */
	public byte getDownlinkFormat() {
		return downlink_format;
	}

	/**
	 * @return byte containing the three bytes after the DF
	 */
	public byte getCapabilities() {
		return capabilities;
	}

	/**
	 * @return the icao24 as an 3-byte array
	 */
	public byte[] getIcao24() {
		return icao24;
	}

	/**
	 * @return payload as 4- or 11-byte array
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
	 * @return recalculated parity as 3-byte array
	 */
	public byte[] calcParity() {
		byte[] message = new byte[payload.length+1];

		message[0] = (byte) (downlink_format<<3 | capabilities);
		for (byte b = 0; b < payload.length; ++b)
			message[b+1] = payload[b];

		return calcParity(message);
	}

	/**
	 * @return true if parity in message matched recalculated parity
	 */
	public boolean checkParity() {
		return tools.areEqual(calcParity(), getParity());
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return super.toString()+"\n"+
				"Mode S Reply:\n"+
				"\tDownlink format:\t"+getDownlinkFormat()+"\n"+
				"\tCapabilities:\t\t"+getCapabilities()+"\n"+
				"\tICAO 24-bit address:\t"+tools.toHexString(getIcao24())+"\n"+
				"\tPayload:\t\t"+tools.toHexString(getPayload())+"\n"+
				"\tParity:\t\t\t"+tools.toHexString(getParity())+"\n"+
				"\tCalculated Parity:\t"+tools.toHexString(calcParity());
	}
}
