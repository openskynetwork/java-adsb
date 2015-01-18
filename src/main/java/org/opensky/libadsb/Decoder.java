package org.opensky.libadsb;

import org.opensky.libadsb.msgs.AirbornePositionMsg;
import org.opensky.libadsb.msgs.AirspeedHeadingMsg;
import org.opensky.libadsb.msgs.EmergencyOrPriorityStatusMsg;
import org.opensky.libadsb.msgs.ExtendedSquitter;
import org.opensky.libadsb.msgs.IdentificationMsg;
import org.opensky.libadsb.msgs.ModeSReply;
import org.opensky.libadsb.msgs.OperationalStatusMsg;
import org.opensky.libadsb.msgs.SurfacePositionMsg;
import org.opensky.libadsb.msgs.TCASResolutionAdvisoryMsg;
import org.opensky.libadsb.msgs.VelocityOverGroundMsg;

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
 * General decoder for ADS-B messages
 * @author Matthias Schäfer <schaefer@sero-systems.de>
 */
public class Decoder {

	/**
	 * A top-down ADS-B decoder. Use instanceof to check for the message type.
	 * @param raw_message The Mode S message in hex representation
	 */
	public static ModeSReply genericDecoder (String raw_message) throws Exception {
		ModeSReply modes = new ModeSReply(raw_message);

		// check parity; Note: some receivers set parity to 0
		if (tools.isZero(modes.getParity()) || modes.checkParity()) {
			// check if it is an ADS-B message
			if (modes.getDownlinkFormat() == 17 || modes.getDownlinkFormat() == 18) {
				ExtendedSquitter es1090 = new ExtendedSquitter(raw_message);

				// what kind of extended squitter?
				byte ftc = es1090.getFormatTypeCode();

				if (ftc >= 1 && ftc <= 4) // identification message
					return new IdentificationMsg(raw_message);

				if (ftc >= 5 && ftc <= 8) // surface position message
					return new SurfacePositionMsg(raw_message);

				if ((ftc >= 9 && ftc <= 18) || (ftc >= 20 && ftc <= 22)) // airborne position message
					return new AirbornePositionMsg(raw_message);

				if (ftc == 19) { // possible velocity message, check subtype
					int subtype = es1090.getMessage()[0]&0x7;

					if (subtype == 1 || subtype == 2) // velocity over ground
						return new VelocityOverGroundMsg(raw_message);
					else if (subtype == 3 || subtype == 4) // airspeed & heading
						return new AirspeedHeadingMsg(raw_message);
				}

				if (ftc == 28) { // aircraft status message, check subtype
					int subtype = es1090.getMessage()[0]&0x7;

					if (subtype == 1) // emergency/priority status
						return new EmergencyOrPriorityStatusMsg(raw_message);
					if (subtype == 2) // TCAS resolution advisory report
						return new TCASResolutionAdvisoryMsg(raw_message);
				}
				
				if (ftc == 31) { // operational status message
					int subtype = es1090.getMessage()[0]&0x7;

					if (subtype == 0 || subtype == 1) // airborne or surface?
						return new OperationalStatusMsg(raw_message);
				}

				return es1090; // unknown extended squitter
			}
		}

		return modes; // unknown mode s reply
	}

}
