package org.opensky.example;

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

import org.opensky.libadsb.*;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.UnspecifiedFormatError;
import org.opensky.libadsb.msgs.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

/**
 * ADS-B decoder example: It reads STDIN line-by-line. It should be fed with
 * comma-separated timestamp, receiver latitude, receiver longitude and the
 * raw Mode S/ADS-B message. Receiver coordinates can be omitted. In that
 * case, surface position messages cannot be decoded properly and plausibility
 * checks for positions are limited. 
 * 
 * Example input:
 * 
 * 1,8d4b19f39911088090641010b9b0
 * 2,8d4ca513587153a8184a2fb5adeb
 * 3,8d3413c399014e23c80f947ce87c
 * 4,5d4ca88c079afe
 * 5,a0001838ca3e51f0a8000047a36a
 * 6,8d47a36a58c38668ffb55f000000
 * 7,5d506c28000000
 * 8,a8000102fe81c1000000004401e3
 * 9,a0001839000000000000004401e3
 * 
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 * @author Markus Fuchs (fuchs@opensky-network.org)
 */
public class ExampleDecoder {
	// The ModeSDecoder does all the magic for us
	private ModeSDecoder decoder = new ModeSDecoder();

	/**
	 *
	 * @param timestamp in milliseconds since epoch
	 * @param raw Mode S messages as hex string
	 * @param receiver the location of the receiver for sanity checks on decoded positions (optional)
	 */
	public void decodeMsg(long timestamp, String raw, Position receiver) {
		ModeSReply msg;
		try {
			msg = decoder.decode(raw);
		} catch (BadFormatException e) {
			System.out.println("Malformed message! Skipping it. Message: "+e.getMessage());
			return;
		} catch (UnspecifiedFormatError e) {
			System.out.println("Unspecified message! Skipping it...");
			return;
		}

		String icao24 = tools.toHexString(msg.getIcao24());

		// check for erroneous messages; some receivers set
		// parity field to the result of the CRC polynomial division
		if (tools.isZero(msg.getParity()) || msg.checkParity()) { // CRC is ok

			// now check the message type
			switch (msg.getType()) {
			case ADSB_AIRBORN_POSITION_V0:
			case ADSB_AIRBORN_POSITION_V1:
			case ADSB_AIRBORN_POSITION_V2:
				AirbornePositionV0Msg ap0 = (AirbornePositionV0Msg) msg;
				System.out.print("["+icao24+"]: ");

				Position c0 = decoder.decodePosition(timestamp, ap0, receiver);
				if (c0 == null)
					System.out.println("Cannot decode position yet.");
				else
					System.out.println("Now at position ("+c0.getLatitude()+","+c0.getLongitude()+")");
				System.out.println("          Horizontal containment radius is "+ap0.getHorizontalContainmentRadiusLimit()+" m");
				System.out.println("          Altitude is "+ (ap0.hasAltitude() ? ap0.getAltitude() : "unknown") +" m");
				// TODO uncertainty etc
				break;
			case ADSB_SURFACE_POSITION_V0:
			case ADSB_SURFACE_POSITION_V1:
			case ADSB_SURFACE_POSITION_V2:
				SurfacePositionV0Msg sp0 = (SurfacePositionV0Msg) msg;
				System.out.print("["+icao24+"]: ");

				Position sPos0 = decoder.decodePosition(timestamp, sp0, receiver);
				// decode the position if possible; prior position needed
				if (sPos0 == null)
					System.out.println("Cannot decode position yet or no reference available (yet).");
				else
					System.out.println("Now at position ("+sPos0.getLatitude()+","+sPos0.getLongitude()+")");

				System.out.println("          Horizontal containment radius is "+sp0.getHorizontalContainmentRadiusLimit()+" m");
				if (sp0.hasValidHeading())
					System.out.println("          Heading is "+sp0.getHeading()+" m");
				System.out.println("          Airplane is on the ground.");
				break;
				// TODO nic suppl etc
			case ADSB_EMERGENCY:
				EmergencyOrPriorityStatusMsg status = (EmergencyOrPriorityStatusMsg) msg;
				System.out.println("["+icao24+"]: "+status.getEmergencyStateText());
				System.out.println("          Mode A code is "+status.getModeACode()[0]+
						status.getModeACode()[1]+status.getModeACode()[2]+status.getModeACode()[3]);
				break;
			case ADSB_AIRSPEED:
				AirspeedHeadingMsg airspeed = (AirspeedHeadingMsg) msg;
				System.out.println("["+icao24+"]: Airspeed is "+
						(airspeed.hasAirspeedInfo() ? airspeed.getAirspeed()+" m/s" : "unkown"));
				if (airspeed.headingStatusFlag())
					System.out.println("          Heading is "+
							(airspeed.headingStatusFlag() ? airspeed.getHeading()+"°" : "unkown"));
				if (airspeed.hasVerticalRateInfo())
					System.out.println("          Vertical rate is "+
							(airspeed.hasVerticalRateInfo() ? airspeed.getVerticalRate()+" m/s" : "unkown"));
				break;
			case ADSB_IDENTIFICATION:
				IdentificationMsg ident = (IdentificationMsg) msg;
				System.out.println("["+icao24+"]: Callsign is "+new String(ident.getIdentity()));
				System.out.println("          Category: "+ident.getCategoryDescription());
				break;
			case ADSB_STATUS_V0:
				break;
			case ADSB_AIRBORN_STATUS_V1:
				break;
			case ADSB_AIRBORN_STATUS_V2:
				break;
			case ADSB_SURFACE_STATUS_V1:
				break;
			case ADSB_SURFACE_STATUS_V2:
				/* TODO
				OperationalStatusV2Msg opstat = (OperationalStatusV2Msg) msg;
				PositionDecoder dec;
				if (decs.containsKey(icao24))
					dec = decs.get(icao24);
				else {
					dec = new PositionDecoder();
					decs.put(icao24, dec);
				}
				dec.setNICSupplementA(opstat.hasNICSupplementA());
				if (opstat.getSubtypeCode() == 1)
					dec.setNICSupplementC(opstat.getNICSupplementC());
				System.out.println("["+icao24+"]: Using ADS-B version "+opstat.getVersion());
				System.out.println("          Has ADS-B IN function: "+opstat.has1090ESIn());
				*/
				break;
			case ADSB_TCAS:
				TCASResolutionAdvisoryMsg tcas = (TCASResolutionAdvisoryMsg) msg;
				System.out.println("["+icao24+"]: TCAS Resolution Advisory completed: "+tcas.hasRATerminated());
				System.out.println("          Threat type is "+tcas.getThreatType());
				if (tcas.getThreatType() == 1) // it's a icao24 address
					System.out.println("          Threat identity is 0x"+String.format("%06x", tcas.getThreatIdentity()));
				break;
			case ADSB_VELOCITY:
				VelocityOverGroundMsg veloc = (VelocityOverGroundMsg) msg;
				System.out.println("["+icao24+"]: Velocity is "+(veloc.hasVelocityInfo() ? veloc.getVelocity() : "unknown")+" m/s");
				System.out.println("          Heading is "+(veloc.hasVelocityInfo() ? veloc.getHeading() : "unknown")+" degrees");
				System.out.println("          Vertical rate is "+(veloc.hasVerticalRateInfo() ? veloc.getVerticalRate() : "unknown")+" m/s");
				break;
			case EXTENDED_SQUITTER:
				System.out.println("["+icao24+"]: Unknown extended squitter with type code "+((ExtendedSquitter)msg).getFormatTypeCode()+"!");
				break;
			}
		}
		else if (msg.getDownlinkFormat() != 17) { // CRC failed
			switch (msg.getType()) {
			case MODES_REPLY:
				System.out.println("["+icao24+"]: Unknown message with DF "+msg.getDownlinkFormat());
				break;
			case SHORT_ACAS:
				ShortACAS acas = (ShortACAS)msg;
				System.out.println("["+icao24+"]: Altitude is "+acas.getAltitude()+" and ACAS is "+
						(acas.hasOperatingACAS() ? "operating." : "not operating."));
				System.out.println("          A/C is "+(acas.isAirborne() ? "airborne" : "on the ground")+
						" and sensitivity level is "+acas.getSensitivityLevel());
				break;
			case ALTITUDE_REPLY:
				AltitudeReply alti = (AltitudeReply)msg;
				System.out.println("["+icao24+"]: Short altitude reply: "+alti.getAltitude()+"m");
				break;
			case IDENTIFY_REPLY:
				IdentifyReply identify = (IdentifyReply)msg;
				System.out.println("["+icao24+"]: Short identify reply: "+identify.getIdentity());
				break;
			case ALL_CALL_REPLY:
				AllCallReply allcall = (AllCallReply)msg;
				System.out.println("["+icao24+"]: All-call reply for "+tools.toHexString(allcall.getInterrogatorID())+
						" ("+(allcall.hasValidInterrogatorID()?"valid":"invalid")+")");
				break;
			case LONG_ACAS:
				LongACAS long_acas = (LongACAS)msg;
				System.out.println("["+icao24+"]: Altitude is "+long_acas.getAltitude()+" and ACAS is "+
						(long_acas.hasOperatingACAS() ? "operating." : "not operating."));
				System.out.println("          A/C is "+(long_acas.isAirborne() ? "airborne" : "on the ground")+
						" and sensitivity level is "+long_acas.getSensitivityLevel());
				System.out.println("          RAC is "+(long_acas.hasValidRAC() ? "valid" : "not valid")+
						" and is "+long_acas.getResolutionAdvisoryComplement()+" (MTE="+long_acas.hasMultipleThreats()+")");
				System.out.println("          Maximum airspeed is "+long_acas.getMaximumAirspeed()+"m/s.");
				break;
			case MILITARY_EXTENDED_SQUITTER:
				MilitaryExtendedSquitter mil = (MilitaryExtendedSquitter)msg;
				System.out.println("["+icao24+"]: Military ES of application "+mil.getApplicationCode());
				System.out.println("          Message is 0x"+tools.toHexString(mil.getMessage()));
				break;
			case COMM_B_ALTITUDE_REPLY:
				CommBAltitudeReply commBaltitude = (CommBAltitudeReply)msg;
				System.out.println("["+icao24+"]: Long altitude reply: "+commBaltitude.getAltitude()+"m");
				break;
			case COMM_B_IDENTIFY_REPLY:
				CommBIdentifyReply commBidentify = (CommBIdentifyReply)msg;
				System.out.println("["+icao24+"]: Long identify reply: "+commBidentify.getIdentity());
				break;
			case COMM_D_ELM:
				CommDExtendedLengthMsg commDELM = (CommDExtendedLengthMsg)msg;
				System.out.println("["+icao24+"]: ELM message w/ sequence no "+commDELM.getSequenceNumber()+
						" (ACK: "+commDELM.isAck()+")");
				System.out.println("          Message is 0x"+tools.toHexString(commDELM.getMessage()));
				break;
			default:
			}
		}
		else {
			System.out.println("Message contains biterrors.");
		}
	}

	public static void main(String[] args) throws Exception {
		// iterate over STDIN
		Scanner sc = new Scanner(System.in, "UTF-8");
		ExampleDecoder dec = new ExampleDecoder();
		Position rec = new Position(0., 0., 0.);
		while(sc.hasNext()) {
		  String[] values = sc.nextLine().split(",");

		  if (values.length == 4) {
			  // time,lat,lon,msg
			  rec.setLongitude(Double.parseDouble(values[2]));
			  rec.setLatitude(Double.parseDouble(values[1]));
			  dec.decodeMsg((long) Double.parseDouble(values[0]), values[3], rec);
		  }
		  else if (values.length == 2) {
			  // time,msg
			  dec.decodeMsg((long) Double.parseDouble(values[0]), values[1], null);
		  }
		}
		sc.close();
	}
}
