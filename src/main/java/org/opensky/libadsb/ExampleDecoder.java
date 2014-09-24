package org.opensky.libadsb;

import org.opensky.libadsb.AirbornePositionMsg;
import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.IdentificationMsg;
import org.opensky.libadsb.ModeSReply;
import org.opensky.libadsb.VelocityOverGroundMsg;
import org.opensky.libadsb.tools;

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
 * ADS-B decoding example with random messages from the OpenSky Network
 * @author Matthias Schäfer <schaefer@sero-systems.de>
 */
public class ExampleDecoder {
	public static void main(String[] args) throws Exception {
		// decode identification message
		ModeSReply msg = Decoder.genericDecoder("8f3c64882010c234c8b820000000");

		// what is it?
		if (msg instanceof IdentificationMsg) {
			IdentificationMsg tmp = (IdentificationMsg)msg;
			System.out.println("Aircraft 0x"+tools.toHexString(msg.getIcao24())+
					" with call sign '"+new String(tmp.getIdentity())+"'.");
		}

		// decode velocity message
		msg = Decoder.genericDecoder("8d507c0b99c5089ad88800000000");
		if (msg instanceof VelocityOverGroundMsg) {
			VelocityOverGroundMsg tmp = (VelocityOverGroundMsg)msg;
			System.out.println("Aircraft 0x"+tools.toHexString(msg.getIcao24())+
					" was travelling at "+
					(tmp.hasVelocityInfo()?tmp.getVelocity():"unkown")+" m/s"+
					" towards "+(tmp.hasVelocityInfo()?tmp.getHeading():"unkown")+
					"° from geographic north. Its vertical rate was "+
					(tmp.hasVerticalRateInfo()?tmp.getVerticalRate():"unkown")+"m/s.");
		}
		
		// decode position message
//		msg = Decoder.genericDecoder("8d47875c58b986d0b3bd25000000"); // odd position msg
//		ModeSReply msg2 = Decoder.genericDecoder("8d47875c58b9835693c897000000"); // even position msg
		msg = Decoder.genericDecoder("8d40064678000740000000000000"); // odd position msg
		ModeSReply msg2 = Decoder.genericDecoder("8d40064678000000000000000000"); // even position msg
		if (msg instanceof AirbornePositionMsg && msg2 instanceof AirbornePositionMsg) {
			// Note that I ensured that msg2 was received shortly after msg
			AirbornePositionMsg p1 = (AirbornePositionMsg)msg;
			AirbornePositionMsg p2 = (AirbornePositionMsg)msg2;
			double[] pos = p1.getGlobalPosition(p2);
			System.out.println("Aircraft 0x"+tools.toHexString(msg.getIcao24())+
					" was at latitude "+pos[0]+" and longitude "+pos[1]);//+
//					" at an altitude of "+p1.getAltitude()+"m.");
		}
	}

}
