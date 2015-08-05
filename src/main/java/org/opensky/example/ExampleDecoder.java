package org.opensky.example;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.opensky.libadsb.Decoder;
import org.opensky.libadsb.Position;
import org.opensky.libadsb.PositionDecoder;
import org.opensky.libadsb.tools;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.msgs.AirbornePositionMsg;
import org.opensky.libadsb.msgs.AirspeedHeadingMsg;
import org.opensky.libadsb.msgs.EmergencyOrPriorityStatusMsg;
import org.opensky.libadsb.msgs.IdentificationMsg;
import org.opensky.libadsb.msgs.ModeSReply;
import org.opensky.libadsb.msgs.OperationalStatusMsg;
import org.opensky.libadsb.msgs.SurfacePositionMsg;
import org.opensky.libadsb.msgs.TCASResolutionAdvisoryMsg;
import org.opensky.libadsb.msgs.VelocityOverGroundMsg;

/**
 * ADS-B decoder example: It reads STDIN line-by-line. It should be fed with
 * comma-separated timestamp and message.
 * @author Matthias Schäfer <schaefer@sero-systems.de>
 */
public class ExampleDecoder {
	// tmp variables for the different message types
	private ModeSReply msg;
	private IdentificationMsg ident;
	private EmergencyOrPriorityStatusMsg status;
	private AirspeedHeadingMsg airspeed;
	private AirbornePositionMsg airpos;
	private OperationalStatusMsg opstat;
	private SurfacePositionMsg surfpos;
	private TCASResolutionAdvisoryMsg tcas;
	private VelocityOverGroundMsg veloc;
	private String icao24;

	// we store the position decoder for each aircraft
	HashMap<String, PositionDecoder> decs;
	private PositionDecoder dec;
	
	public ExampleDecoder() {
		decs = new HashMap<String, PositionDecoder>();
	}

	public void decodeMsg(double timestamp, String raw) throws Exception {
		try {
			msg = Decoder.genericDecoder(raw);
		} catch (BadFormatException e) {
			System.out.println("Malformed message! Skipping it...");
			return;
		}

		// check for erroneous messages; some receivers set
		// parity field to the result of the CRC polynomial division
		if (tools.isZero(msg.getParity()) || msg.checkParity()) { // CRC is ok
			icao24 = tools.toHexString(msg.getIcao24());
			
			// cleanup decoders every 100.000 messages to avoid excessive memory usage
			// therefore, remove decoders which have not been used for more than one hour.
			List<String> to_remove = new ArrayList<String>();
			for (String key : decs.keySet())
				if (decs.get(key).getLastUsedTime()<timestamp-3600)
					to_remove.add(key);
			
			for (String key : to_remove)
				decs.remove(key);

			// now check the message type

			switch (msg.getType()) {
			case ADSB_AIRBORN_POSITION:
				airpos = (AirbornePositionMsg) msg;
				System.out.print("["+icao24+"]: ");

				// decode the position if possible
				if (decs.containsKey(icao24)) {
					dec = decs.get(icao24);
					airpos.setNICSupplementA(dec.getNICSupplementA());
					Position current = dec.decodePosition(timestamp, airpos);
					if (current == null)
						System.out.println("Cannot decode position yet.");
					else
						System.out.println("Now at position ("+current.getLatitude()+","+current.getLongitude()+")");
				}
				else {
					dec = new PositionDecoder();
					dec.decodePosition(timestamp, airpos);
					decs.put(icao24, dec);
					System.out.println("First position.");
				}
				System.out.println("          Horizontal containment radius is "+airpos.getHorizontalContainmentRadiusLimit()+" m");
				System.out.println("          Altitude is "+ (airpos.hasAltitude() ? airpos.getAltitude() : "unknown") +" m");
				break;
			case ADSB_SURFACE_POSITION:
				surfpos = (SurfacePositionMsg) msg;

				System.out.print("["+icao24+"]: ");

				// decode the position if possible
				if (decs.containsKey(icao24)) {
					dec = decs.get(icao24);
					Position current = dec.decodePosition(timestamp, surfpos);
					if (current == null)
						System.out.println("Cannot decode position yet.");
					else
						System.out.println("Now at position ("+current.getLatitude()+","+current.getLongitude()+")");
				}
				else {
					dec = new PositionDecoder();
					dec.decodePosition(timestamp, surfpos);
					decs.put(icao24, dec);
					System.out.println("First position.");
				}
				System.out.println("          Horizontal containment radius is "+surfpos.getHorizontalContainmentRadiusLimit()+" m");
				if (surfpos.hasValidHeading())
					System.out.println("          Heading is "+surfpos.getHeading()+" m");
				System.out.println("          Airplane is on the ground.");
				break;
			case ADSB_EMERGENCY:
				status = (EmergencyOrPriorityStatusMsg) msg;
				System.out.println("["+icao24+"]: "+status.getEmergencyStateText());
				System.out.println("          Mode A code is "+status.getModeACode()[0]+
						status.getModeACode()[1]+status.getModeACode()[2]+status.getModeACode()[3]);
				break;
			case ADSB_AIRSPEED:
				airspeed = (AirspeedHeadingMsg) msg;
				System.out.println("["+icao24+"]: Airspeed is "+
						(airspeed.hasAirspeedInfo() ? airspeed.getAirspeed()+" m/s" : "unkown"));
				if (airspeed.hasHeadingInfo())
					System.out.println("          Heading is "+
							(airspeed.hasHeadingInfo() ? airspeed.getHeading()+"°" : "unkown"));
				if (airspeed.hasVerticalRateInfo())
					System.out.println("          Vertical rate is "+
							(airspeed.hasVerticalRateInfo() ? airspeed.getVerticalRate()+" m/s" : "unkown"));
				break;
			case ADSB_IDENTIFICATION:
				ident = (IdentificationMsg) msg;
				System.out.println("["+icao24+"]: Callsign is "+new String(ident.getIdentity()));
				System.out.println("          Category: "+ident.getCategoryDescription());
				break;
			case ADSB_STATUS:
				opstat = (OperationalStatusMsg) msg;
				PositionDecoder dec;
				if (decs.containsKey(icao24))
					dec = decs.get(icao24);
				else {
					dec = new PositionDecoder();
					decs.put(icao24, dec);
				}
				dec.setNICSupplementA(opstat.getNICSupplementA());
				if (opstat.getSubtypeCode() == 1)
					dec.setNICSupplementC(opstat.getNICSupplementC());
				System.out.println("["+icao24+"]: Using ADS-B version "+opstat.getVersion());
				System.out.println("          Has ADS-B IN function: "+opstat.has1090ESIn());
				break;
			case ADSB_TCAS:
				tcas = (TCASResolutionAdvisoryMsg) msg;
				System.out.println("["+icao24+"]: TCAS Resolution Advisory completed: "+tcas.hasRATerminated());
				System.out.println("          Threat type is "+tcas.getThreatType());
				if (tcas.getThreatType() == 1) // it's a icao24 address
					System.out.println("          Threat identity is 0x"+String.format("%06x", tcas.getThreatIdentity()));
				break;
			case ADSB_VELOCITY:
				veloc = (VelocityOverGroundMsg) msg;
				System.out.println("["+icao24+"]: Velocity is "+(veloc.hasVelocityInfo() ? veloc.getVelocity() : "unknown")+" m/s");
				System.out.println("          Heading is "+(veloc.hasVelocityInfo() ? veloc.getHeading() : "unknown")+" degrees");
				System.out.println("          Vertical rate is "+(veloc.hasVerticalRateInfo() ? veloc.getVerticalRate() : "unknown")+" m/s");
				break;
			default:
				System.out.println("["+icao24+"]: Unknown message with downlink format "+msg.getDownlinkFormat());
			}
		}
		else { // CRC failed
			System.out.println("Message seems to contain biterrors.");
		}
	}

}
