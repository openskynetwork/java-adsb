package org.opensky.libadsb;

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
public class ModeSReply {
	/*
	 * Attributes
	 */

	private byte downlink_format; // 0-31
	private byte[] icao24; // 3 bytes
	private byte[] payload; // 3 or 10 bytes
	private byte[] parity; // 3 bytes


	/*
	 * Constructors
	 */

	/**
	 * @param raw_message Mode S message in hex representation
	 */
	public ModeSReply (String raw_message) {
		// check format invariants
		int length = raw_message.length();
		assert length == 14 || length == 28: "Raw message has invalid length";

		byte downlink_format = (byte) (Short.parseShort(raw_message.substring(0, 2), 16) >>> 3);

		byte[] payload = new byte[(length-6)/2];
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
			payload[0] = (byte) (Short.parseShort(raw_message.substring(0, 2), 16) & 0x7);
			for (int i=2; i<length-6; i+=2)
				payload[1+(i-2)/2] = (byte) Short.parseShort(raw_message.substring(i, i+2), 16);
			break;
		case 11: // Short all-call reply
		case 17: case 18: // Extended squitter
			// extract payload (little endian)
			payload[0] = (byte) (Short.parseShort(raw_message.substring(0, 2), 16) & 0x7);
			for (int i=2; i<length-6; i+=2)
				payload[1+(i-2)/2] = (byte) Short.parseShort(raw_message.substring(i, i+2), 16);

			for (int i=1; i<4; i++)
				icao24[i-1] = payload[i];

			for (int i=length-6; i<length; i+=2)
				parity[(i-length+6)/2] = (byte) Short.parseShort(raw_message.substring(i, i+2), 16);
			break;
		default: // unkown downlink format
			// leave everything 0
		}
		
		// check format invariants
		assert downlink_format <= 31: "Invalid downlink format";
		assert icao24.length == 3: "ICAO address too short/long";
		assert payload.length == 4 || payload.length == 11: "Payload length does not match specification";
		assert parity.length == 3: "Parity too short/long";

		this.downlink_format = downlink_format;
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
	 * @return parity field as 3-byte array
	 */
	public byte[] getParity() {
		return parity;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return super.toString()+"\n"+
				"Mode S Reply:\n"+
				"\tDownlink format:\t"+getDownlinkFormat()+"\n"+
				"\tICAO 24-bit address:\t"+tools.toHexString(getIcao24())+"\n"+
				"\tPayload:\t\t"+tools.toHexString(getPayload())+"\n"+
				"\tParity:\t\t\t"+tools.toHexString(getParity());
	}
}
