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
 * ADS-B decoder example: It reads messages line-by-line from STDIN and prints some information. You can
 * Use it as follows:<br>
 * tail messages.txt | java ExampleDecoder<br>
 * src/test/resources/messages.txt contains 15000 random messages from the OpenSky database.
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
			decoder.decodeMsg(raw);
		}
	}

	/**
	 * This class is mainly used for position decoding
	 */
	private class Aircraft {
		public AirbornePositionMsg last_even_airborne;
		public AirbornePositionMsg last_odd_airborne;
		public SurfacePositionMsg last_even_surface;
		public SurfacePositionMsg last_odd_surface;
		public double[] last_position; // lat lon
		public boolean supplA;
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
	Aircraft tmp;
	
	// we store the positions of aircraft for the CPR
	HashMap<String, Aircraft> aircraft = new HashMap<String, Aircraft>();
	
	public void decodeMsg(String raw) throws Exception {
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
				if (aircraft.containsKey(icao24) && airpos.hasPosition()) {
					tmp = aircraft.get(icao24);
					airpos.setNICSupplementA(tmp.supplA);
					if (tmp.last_position != null) { // use local CPR
						tmp.last_position = airpos.getLocalPosition(tmp.last_position[0], tmp.last_position[1]);
						System.out.println("Now at position ("+tmp.last_position[0]+","+tmp.last_position[1]+") (local)");
					}
					else if (airpos.isOddFormat()) {
						tmp.last_odd_airborne = airpos;
						
						if (tmp.last_even_airborne != null) { // use global CPR
							try {
								tmp.last_position = airpos.getGlobalPosition(tmp.last_even_airborne);
								System.out.println("Now at position ("+tmp.last_position[0]+","+tmp.last_position[1]+") (global)");
							}
							catch (Exception e) {
								System.out.println("Could not decode position (probably incompatible)");
							}
						}
						else {
							System.out.println("Cannot decode position yet.");
						}
					}
					else {
						tmp.last_even_airborne = airpos;
						
						if (tmp.last_odd_airborne != null) { // use global CPR
							tmp.last_position = airpos.getGlobalPosition(tmp.last_odd_airborne);
							System.out.println("Now at position ("+tmp.last_position[0]+","+tmp.last_position[1]+") (global)");
						}
						else {
							System.out.println("Cannot decode position yet.");
						}
					}
				}
				else {
					tmp = new ExampleDecoder().new Aircraft();
					if (airpos.isOddFormat()) tmp.last_odd_airborne = airpos;
					else tmp.last_even_airborne = airpos;
					aircraft.put(icao24, tmp);
					System.out.println("First position.");
				}
				System.out.println("          Horizontal containment radius is "+airpos.getHorizontalContainmentRadiusLimit()+" m");
				System.out.println("          Altitude is "+airpos.getAltitude()+" m");
			}
			
			else if (msg instanceof OperationalStatusMsg) {
				counters[4]++;
				opstat = (OperationalStatusMsg) msg;
				if (aircraft.containsKey(icao24))
					aircraft.get(icao24).supplA = opstat.getNICSupplementA();
				System.out.println("["+icao24+"]: Using ADS-B version "+opstat.getVersion());
				System.out.println("          Has ADS-B IN function: "+opstat.has1090ESIn());
			}
			
			else if (msg instanceof SurfacePositionMsg) {
				counters[5]++;
				surfpos = (SurfacePositionMsg) msg;
				
				System.out.print("["+icao24+"]: ");
				
				// decode the position if possible
				if (aircraft.containsKey(icao24) && surfpos.hasPosition()) {
					tmp = aircraft.get(icao24);
					if (tmp.last_position != null) { // use local CPR
						tmp.last_position = surfpos.getLocalPosition(tmp.last_position[0], tmp.last_position[1]);
						System.out.println("Now at position ("+tmp.last_position[0]+","+tmp.last_position[1]+") (local)");
					}
					else if (surfpos.isOddFormat()) {
						tmp.last_odd_surface = surfpos;
						
						if (tmp.last_even_surface != null) { // use global CPR
							try {
								tmp.last_position = surfpos.getGlobalPosition(tmp.last_even_surface);
								System.out.println("Now at position ("+tmp.last_position[0]+","+tmp.last_position[1]+") (global)");
							}
							catch (Exception e) {
								System.out.println("Could not decode position (probably incompatible)");
							}
						}
						else {
							System.out.println("Cannot decode position yet.");
						}
					}
					else {
						tmp.last_even_surface = surfpos;
						
						if (tmp.last_odd_surface != null) { // use global CPR
							tmp.last_position = surfpos.getGlobalPosition(tmp.last_odd_surface);
							System.out.println("Now at position ("+tmp.last_position[0]+","+tmp.last_position[1]+") (global)");
						}
						else {
							System.out.println("Cannot decode position yet.");
						}
					}
				}
				else {
					tmp = new ExampleDecoder().new Aircraft();
					if (surfpos.isOddFormat()) tmp.last_odd_surface = surfpos;
					else tmp.last_even_surface = surfpos;
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
