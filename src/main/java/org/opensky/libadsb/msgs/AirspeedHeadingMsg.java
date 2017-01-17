package org.opensky.libadsb.msgs;

import java.io.Serializable;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.MissingInformationException;

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
 * Decoder for ADS-B airspeed and heading messages
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class AirspeedHeadingMsg extends ExtendedSquitter implements Serializable {
	
	private static final long serialVersionUID = -7072061713588878404L;
	private byte msg_subtype;
	private boolean intent_change;
	private boolean ifr_capability;
	private byte navigation_accuracy_category;
	private boolean heading_available;
	private double heading; // in degrees
	private boolean true_airspeed; // 0 = indicated AS, 1 = true AS
	private short airspeed; // in knots
	private boolean airspeed_available;
	private boolean vertical_source; // 0 = geometric, 1 = barometric
	private boolean vertical_rate_down; // 0 = up, 1 = down
	private short vertical_rate; // in ft/s
	private boolean vertical_rate_info_available;
	private short geo_minus_baro; // in ft
	private boolean geo_minus_baro_available;

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected AirspeedHeadingMsg() { }

	/**
	 * @param raw_message raw ADS-B airspeed and heading message as hex string
	 * @throws BadFormatException if message has wrong format
	 */
	public AirspeedHeadingMsg(String raw_message) throws BadFormatException {
		this(new ExtendedSquitter(raw_message));
	}
	
	/**
	 * @param squitter extended squitter containing the airspeed and heading msg
	 * @throws BadFormatException if message has wrong format
	 */
	public AirspeedHeadingMsg(ExtendedSquitter squitter) throws BadFormatException {
		super(squitter);
		setType(subtype.ADSB_AIRSPEED);
		
		if (this.getFormatTypeCode() != 19) {
			throw new BadFormatException("Airspeed and heading messages must have typecode 19.");
		}
		
		byte[] msg = this.getMessage();
		
		msg_subtype = (byte) (msg[0]&0x7);
		if (msg_subtype != 3 && msg_subtype != 4) {
			throw new BadFormatException("Airspeed and heading messages have subtype 3 or 4.");
		}
		
		intent_change = (msg[1]&0x80)>0;
		ifr_capability = (msg[1]&0x40)>0;
		navigation_accuracy_category = (byte) ((msg[1]>>>3)&0x7);
		
		// check this later
		vertical_rate_info_available = true;
		geo_minus_baro_available = true;
		
		heading_available = (msg[1]&0x4)>0;
		heading = ((msg[1]&0x3)<<8 | msg[2]&0xFF) * 360/1024;
		
		true_airspeed = (msg[3]&0x80)>0;
		airspeed = (short) (((msg[3]&0x7F)<<3 | msg[4]>>>5&0x07)-1);
		if (airspeed != -1) {
			airspeed_available = true;
			if (msg_subtype == 4) airspeed<<=2;
		}

		vertical_source = (msg[4]&0x10)>0;
		vertical_rate_down = (msg[4]&0x08)>0;
		vertical_rate = (short) ((((msg[4]&0x07)<<6 | msg[5]>>>2&0x3F)-1)<<6);
		if (vertical_rate == -1) vertical_rate_info_available = false;
		
		geo_minus_baro = (short) (((msg[6]&0x7F)-1)*25);
		if (geo_minus_baro == -1) geo_minus_baro_available = false;
		if ((msg[6]&0x80)>0) geo_minus_baro *= -1;
	}

	/**
	 * Must be checked before accessing heading!
	 * 
	 * @return whether heading info is available
	 */
	public boolean hasHeadingInfo() {
		return heading_available;
	}

	/**
	 * Must be checked before accessing airspeed!
	 * 
	 * @return whether airspeed info is available
	 */
	public boolean hasAirspeedInfo() {
		return airspeed_available;
	}
	
	/**
	 * Must be checked before accessing vertical rate!
	 * 
	 * @return whether vertical rate info is available
	 */
	public boolean hasVerticalRateInfo() {
		return vertical_rate_info_available;
	}

	/**
	 * Must be checked before accessing geo minus baro!
	 * 
	 * @return whether geo-baro difference info is available
	 */
	public boolean hasGeoMinusBaroInfo() {
		return geo_minus_baro_available;
	}

	/**
	 * @return If supersonic, velocity has only 4 kts accuracy, otherwise 1 kt
	 */
	public boolean isSupersonic() {
		return msg_subtype == 4;
	}
	
	/**
	 * @return true, if aircraft wants to change altitude for instance
	 */
	public boolean hasChangeIntent() {
		return intent_change;
	}

	/**
	 * Note: only in ADS-B version 1 transponders!!
	 * @return true, iff aircraft has equipage class A1 or higher
	 */
	public boolean hasIFRCapability() {
		return ifr_capability;
	}


	/**
	 * @return NAC according to RTCA DO-260A
	 */
	public byte getNavigationAccuracyCategory() {
		return navigation_accuracy_category;
	}


	/**
	 * @return airspeed in m/s
	 * @throws MissingInformationException if no velocity information available
	 */
	public double getAirspeed() throws MissingInformationException {
		if (!airspeed_available) throw new MissingInformationException("No airspeed info available!");
		return airspeed * 0.514444;
	}


	/**
	 * @return whether altitude is derived by barometric sensor or GNSS
	 */
	public boolean isBarometricVerticalSpeed() {
		return vertical_source;
	}


	/**
	 * @return vertical rate in m/s (negative value means descending)
	 * @throws MissingInformationException if no vertical rate info is available
	 */
	public double getVerticalRate() throws MissingInformationException {
		if (!vertical_rate_info_available) throw new MissingInformationException("No vertical rate info available!");
		return (vertical_rate_down ? -vertical_rate : vertical_rate) * 0.00508;
	}


	/**
	 * @return difference between barometric and geometric altitude in m
	 * @throws MissingInformationException  if no geo/baro difference info is available
	 */
	public double getGeoMinusBaro() throws MissingInformationException {
		if (!geo_minus_baro_available) throw new MissingInformationException("No geo/baro difference info available!");
		return geo_minus_baro * 0.3048;
	}
	
	/**
	 * @return heading in decimal degrees ([0, 360]). 0° = geographic north
	 * @throws MissingInformationException if no velocity info is available
	 */
	public double getHeading() throws MissingInformationException {
		if (!heading_available) throw new MissingInformationException("No heading info available!");
		return heading;
	}
	
	/**
	 * @return true if airspeed is true airspeed, false if airspeed is indicated airspeed
	 */
	public boolean isTrueAirspeed() {
		return true_airspeed;
	}
	
	public String toString() {
		String ret = super.toString()+"\n"+
				"Airspeed and heading:\n";
		try { ret += "\tAirspeed:\t"+getAirspeed()+" m/s\n"; }
		catch (Exception e) { ret += "\tAirspeed:\t\tnot available\n"; }
		ret += "\tAirspeed Type:\t\t"+(isTrueAirspeed() ? "true" : "indicated")+"\n";
		try { ret += "\tHeading\t\t\t\t"+getHeading()+"\n"; }
		catch (Exception e) { ret += "\tHeading\t\t\t\tnot available\n"; }
		try { ret += "\tVertical rate:\t\t\t"+getVerticalRate(); }
		catch (Exception e) { ret += "\tVertical rate:\t\t\tnot available"; }
		
		return ret;
	}
}
