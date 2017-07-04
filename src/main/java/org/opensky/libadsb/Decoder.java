package org.opensky.libadsb;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.UnspecifiedFormatError;
import org.opensky.libadsb.msgs.AirbornePositionMsg;
import org.opensky.libadsb.msgs.AirspeedHeadingMsg;
import org.opensky.libadsb.msgs.AllCallReply;
import org.opensky.libadsb.msgs.AltitudeReply;
import org.opensky.libadsb.msgs.CommBAltitudeReply;
import org.opensky.libadsb.msgs.CommBIdentifyReply;
import org.opensky.libadsb.msgs.CommDExtendedLengthMsg;
import org.opensky.libadsb.msgs.EmergencyOrPriorityStatusMsg;
import org.opensky.libadsb.msgs.ExtendedSquitter;
import org.opensky.libadsb.msgs.IdentificationMsg;
import org.opensky.libadsb.msgs.IdentifyReply;
import org.opensky.libadsb.msgs.LongACAS;
import org.opensky.libadsb.msgs.MilitaryExtendedSquitter;
import org.opensky.libadsb.msgs.ModeSReply;
import org.opensky.libadsb.msgs.OperationalStatusMsg;
import org.opensky.libadsb.msgs.ShortACAS;
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
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class Decoder {

	/**
	 * A easy-to-use top-down ADS-B decoder. Use msg.getType() to
	 * check the message type and then cast to the appropriate class.
	 * @param raw_message the Mode S message in hex representation
	 * @return an instance of the most specialized ModeSReply possible
	 * @throws UnspecifiedFormatError if format is not specified
	 * @throws BadFormatException if format contains error
	 */
	public static ModeSReply genericDecoder (String raw_message) throws BadFormatException, UnspecifiedFormatError {
		return genericDecoder(new ModeSReply(raw_message));
	}
	
	/**
	 * This function decodes a half-decoded Mode S reply to its
	 * deepest possible specialization. Use getType() to check its
	 * actual type afterwards.
	 * @param modes the incompletely decoded Mode S message
	 * @return an instance of the most specialized ModeSReply possible
	 * @throws UnspecifiedFormatError if format is not specified
	 * @throws BadFormatException if format contains error
	 */
	public static ModeSReply genericDecoder (ModeSReply modes) throws BadFormatException, UnspecifiedFormatError {
		switch (modes.getDownlinkFormat()) {
		case 0: return new ShortACAS(modes);
		case 4: return new AltitudeReply(modes);
		case 5: return new IdentifyReply(modes);
		case 11: return new AllCallReply(modes);
		case 16: return new LongACAS(modes);
		case 17: case 18:
			ExtendedSquitter es1090 = new ExtendedSquitter(modes);

			// what kind of extended squitter?
			byte ftc = es1090.getFormatTypeCode();

			if (ftc >= 1 && ftc <= 4) // identification message
				return new IdentificationMsg(es1090);

			if (ftc >= 5 && ftc <= 8) // surface position message
				return new SurfacePositionMsg(es1090);

			if ((ftc >= 9 && ftc <= 18) || (ftc >= 20 && ftc <= 22)) // airborne position message
				return new AirbornePositionMsg(es1090);

			if (ftc == 19) { // possible velocity message, check subtype
				int subtype = es1090.getMessage()[0]&0x7;

				if (subtype == 1 || subtype == 2) // velocity over ground
					return new VelocityOverGroundMsg(es1090);
				else if (subtype == 3 || subtype == 4) // airspeed & heading
					return new AirspeedHeadingMsg(es1090);
			}

			if (ftc == 28) { // aircraft status message, check subtype
				int subtype = es1090.getMessage()[0]&0x7;

				if (subtype == 1) // emergency/priority status
					return new EmergencyOrPriorityStatusMsg(es1090);
				if (subtype == 2) // TCAS resolution advisory report
					return new TCASResolutionAdvisoryMsg(es1090);
			}

			if (ftc == 31) { // operational status message
				int subtype = es1090.getMessage()[0]&0x7;

				if (subtype == 0 || subtype == 1) // airborne or surface?
					return new OperationalStatusMsg(es1090);
			}

			return es1090; // unknown extended squitter
		case 19: return new MilitaryExtendedSquitter(modes);
		case 20: return new CommBAltitudeReply(modes);
		case 21: return new CommBIdentifyReply(modes);
		default:
			if (modes.getDownlinkFormat()>=24)
				return new CommDExtendedLengthMsg(modes);
			else return modes; // unknown mode s reply
		}
	}
}
