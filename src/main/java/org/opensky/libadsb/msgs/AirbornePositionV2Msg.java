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
 * Decoder for ADS-B airborne position version 2 (DO-260B).
 * @author Markus Fuchs (fuchs@opensky-network.org)
 */
public class AirbornePositionV2Msg extends AirbornePositionV1Msg implements Serializable {

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected AirbornePositionV2Msg() { }

	/**
	 * @param raw_message raw ADS-B airborne position message as hex string
	 * @throws BadFormatException if message has wrong format
	 */
	public AirbornePositionV2Msg(String raw_message) throws BadFormatException {
		this(new ExtendedSquitter(raw_message));
	}

	/**
	 * @param raw_message raw ADS-B airborne position message as byte array
	 * @throws BadFormatException if message has wrong format
	 */
	public AirbornePositionV2Msg(byte[] raw_message) throws BadFormatException {
		this(new ExtendedSquitter(raw_message));
	}

	/**
	 * @param squitter extended squitter containing the airborne position msg
	 * @throws BadFormatException if message has wrong format
	 */
	public AirbornePositionV2Msg(ExtendedSquitter squitter) throws BadFormatException {
		super(squitter);
		setType(subtype.ADSB_AIRBORN_POSITION_V2);
	}

	/**
	 * NIC supplement B as introduced in ADS-B version 2. The flag indicated single antenna in previous versions.
	 * @return NIC supplement B to refine horizontal containment radius limit and navigation integrity category
	 */
	public boolean hasNICSupplementB() {
		return super.hasSingleAntenna();
	}

	/**
	 * The position error, i.e., 95% accuracy for the horizontal position. For the navigation accuracy category
	 * (NACp) see {@link AirborneOperationalStatusV2Msg}. According to DO-260B Table 2-14.
	 *
	 * The horizontal containment radius is also known as "horizontal protection level".
	 *
	 * @return horizontal containment radius limit in meters. A return value of -1 means "unkown".
	 *         If aircraft uses ADS-B version 2, set NIC supplement A from Operational Status Message
	 *         for better precision. Otherwise, we'll be pessimistic.
	 */
	public double getHorizontalContainmentRadiusLimit() {
		switch (getFormatTypeCode()) {
			case 0: case 18: case 22: return -1;
			case 9: case 20: return 7.5;
			case 10: case 21: return 25;
			case 11:
				return hasNICSupplementB() && hasNICSupplementA() ? 75 : 185.2;
			case 12: return 370.4;
			case 13:
				if (!hasNICSupplementB()) return 926;
				else return hasNICSupplementA() ? 1111.2 : 555.6;
			case 14: return 1852;
			case 15: return 3704;
			case 16:
				return hasNICSupplementB() && hasNICSupplementA() ? 7408 : 14816;
			case 17: return 37040;
			default: return -1;
		}
	}

	/**
	 * According to DO-260B Table 2-14.
	 * @return Navigation integrity category. A NIC of 0 means "unkown".
	 */
	public byte getNIC() {
		switch (getFormatTypeCode()) {
			case 0: case 18: case 22: return 0;
			case 9: case 20: return 11;
			case 10: case 21: return 10;
			case 11:
				return (byte) (hasNICSupplementB() && hasNICSupplementA() ? 9 : 8);
			case 12: return 7;
			case 13: return 6;
			case 14: return 5;
			case 15: return 4;
			case 16:
				return (byte) (hasNICSupplementB() && hasNICSupplementA() ? 3 : 2);
			case 17: return 1;
			default: return 0;
		}
	}
}
