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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;

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
	// we count the message types and biterrors for our summary
	private static int[] counters = new int[10];
	
	public static void main(String[] args) throws Exception {
		// print summary on shutdown
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("\nSummary:");
				System.out.println("\tIdentification Messages: "+counters[0]);
				System.out.println("\tEmergency/Priority Status Messages: "+counters[1]);
				System.out.println("\tAirspeed/Heading Messages: "+counters[2]);
				System.out.println("\tAirborne Position Messages: "+counters[3]);
				System.out.println("\tOperational Status Messages: "+counters[4]);
				System.out.println("\tSurface Position Messages: "+counters[5]);
				System.out.println("\tTCAS Resolution Advisory Messages: "+counters[6]);
				System.out.println("\tVelocity Messages: "+counters[7]);
				System.out.println("\n\tUnkown Messages: "+counters[8]);
				System.out.println("\tErroneous Messages: "+counters[9]);
			}
		});

		ExampleDecoder decoder = new ExampleDecoder();
		
		// the main loop
		String raw;
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			raw = in.readLine();
			if (raw == null) {
				System.out.println("No more input. Exiting...");
				System.exit(0);
			}
			String[] input = raw.split(",");
			decoder.decodeMsg(Double.parseDouble(input[0]), input[1]);
		}
	}

	// tmp variables for the different message types
	ModeSReply msg;
	IdentificationMsg ident;
	EmergencyOrPriorityStatusMsg status;
	AirspeedHeadingMsg airspeed;
	AirbornePositionMsg airpos;
	OperationalStatusMsg opstat;
	SurfacePositionMsg surfpos;
	TCASResolutionAdvisoryMsg tcas;
	VelocityOverGroundMsg veloc;
	String icao24;
	
	// we store the position decoder for each aircraft
	HashMap<String, PositionDecoder> aircraft = new HashMap<String, PositionDecoder>();
	
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
			
			// now check the message type
			if (msg instanceof IdentificationMsg) {
				counters[0]++;
				ident = (IdentificationMsg) msg;
				System.out.println("["+icao24+"]: Callsign is "+new String(ident.getIdentity()));
				System.out.println("          Category: "+ident.getCategoryDescription());
			}
			
			else if (msg instanceof EmergencyOrPriorityStatusMsg) {
				counters[1]++;
				status = (EmergencyOrPriorityStatusMsg) msg;
				System.out.println("["+icao24+"]: "+status.getEmergencyStateText());
				System.out.println("          Mode A code is "+status.getModeACode()[0]+
						status.getModeACode()[1]+status.getModeACode()[2]+status.getModeACode()[3]);
			}
			
			else if (msg instanceof AirspeedHeadingMsg) {
				counters[2]++;
				airspeed = (AirspeedHeadingMsg) msg;
				System.out.println("["+icao24+"]: Airspeed is "+
						(airspeed.hasAirspeedInfo() ? airspeed.getAirspeed()+" m/s" : "unkown"));
				if (airspeed.hasHeadingInfo())
					System.out.println("          Heading is "+
						(airspeed.hasHeadingInfo() ? airspeed.getHeading()+"°" : "unkown"));
				if (airspeed.hasVerticalRateInfo())
					System.out.println("          Vertical rate is "+
							(airspeed.hasVerticalRateInfo() ? airspeed.getVerticalRate()+" m/s" : "unkown"));
				
			}
			
			else if (msg instanceof AirbornePositionMsg) {
				counters[3]++;
				airpos = (AirbornePositionMsg) msg;
				
				System.out.print("["+icao24+"]: ");
				
				// decode the position if possible
				if (aircraft.containsKey(icao24)) {
					PositionDecoder tmp = aircraft.get(icao24);
					airpos.setNICSupplementA(tmp.getNICSupplementA());
					Position current = tmp.decodePosition(timestamp, airpos);
					if (current == null)
						System.out.println("Cannot decode position yet.");
					else
						System.out.println("Now at position ("+current.getLatitude()+","+current.getLongitude()+") (global)");
				}
				else {
					PositionDecoder tmp = new PositionDecoder();
					tmp.decodePosition(timestamp, airpos);
					aircraft.put(icao24, tmp);
					System.out.println("First position.");
				}
				System.out.println("          Horizontal containment radius is "+airpos.getHorizontalContainmentRadiusLimit()+" m");
				System.out.println("          Altitude is "+ (airpos.hasAltitude() ? airpos.getAltitude() : "unknown") +" m");
			}
			
			else if (msg instanceof OperationalStatusMsg) {
				counters[4]++;
				opstat = (OperationalStatusMsg) msg;
				if (aircraft.containsKey(icao24))
					aircraft.get(icao24).setNICSupplementA(opstat.getNICSupplementA());
				System.out.println("["+icao24+"]: Using ADS-B version "+opstat.getVersion());
				System.out.println("          Has ADS-B IN function: "+opstat.has1090ESIn());
			}
			
			else if (msg instanceof SurfacePositionMsg) {
				counters[5]++;
				surfpos = (SurfacePositionMsg) msg;
				
				System.out.print("["+icao24+"]: ");
				
				// decode the position if possible
				if (aircraft.containsKey(icao24)) {
					PositionDecoder tmp = aircraft.get(icao24);
					airpos.setNICSupplementA(tmp.getNICSupplementA());
					Position current = tmp.decodePosition(timestamp, airpos);
					if (current == null)
						System.out.println("Cannot decode position yet.");
					else
						System.out.println("Now at position ("+current.getLatitude()+","+current.getLongitude()+") (global)");
				}
				else {
					PositionDecoder tmp = new PositionDecoder();
					tmp.decodePosition(timestamp, airpos);
					aircraft.put(icao24, tmp);
					System.out.println("First position.");
				}
				System.out.println("          Horizontal containment radius is "+surfpos.getHorizontalContainmentRadiusLimit()+" m");
				if (surfpos.hasValidHeading())
					System.out.println("          Heading is "+surfpos.getHeading()+" m");
				System.out.println("          Airplane is on the ground.");
			}
			
			else if (msg instanceof TCASResolutionAdvisoryMsg) {
				counters[6]++;
				tcas = (TCASResolutionAdvisoryMsg) msg;
				System.out.println("["+icao24+"]: TCAS Resolution Advisory completed: "+tcas.hasRATerminated());
				System.out.println("          Threat type is "+tcas.getThreatType());
				if (tcas.getThreatType() == 1) // it's a icao24 address
					System.out.println("          Threat identity is 0x"+String.format("%06x", tcas.getThreatIdentity()));
			}
			
			else if (msg instanceof VelocityOverGroundMsg) {
				counters[7]++;
				veloc = (VelocityOverGroundMsg) msg;
				System.out.println("["+icao24+"]: Velocity is "+(veloc.hasVelocityInfo() ? veloc.getVelocity() : "unknown")+" m/s");
				System.out.println("          Heading is "+(veloc.hasVelocityInfo() ? veloc.getHeading() : "unknown")+" degrees");
				System.out.println("          Vertical rate is "+(veloc.hasVerticalRateInfo() ? veloc.getVerticalRate() : "unknown")+" m/s");
			}
			
			else { // unknown format
				counters[8]++;
				System.out.println("["+icao24+"]: Unknown message with downlink format "+msg.getDownlinkFormat());
			}
		}
		else { // CRC failed
			counters[9]++;
		}
	}

}
