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
 * Decoder for ADS-B velocity messages
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class VelocityOverGroundMsg extends ExtendedSquitter implements Serializable {
	
	private static final long serialVersionUID = -7397309420290359454L;
	private byte msg_subtype;
	private boolean intent_change;
	private boolean ifr_capability;
	private byte navigation_accuracy_category;
	private boolean direction_west; // 0 = east, 1 = west
	private short east_west_velocity; // in kn
	private boolean velocity_info_available;
	private boolean direction_south; // 0 = north, 1 = south
	private short north_south_velocity; // in kn
	private boolean vertical_source; // 0 = geometric, 1 = barometric
	private boolean vertical_rate_down; // 0 = up, 1 = down
	private short vertical_rate; // in ft/min
	private boolean vertical_rate_info_available;
	private short geo_minus_baro; // in ft
	private boolean geo_minus_baro_available;

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected VelocityOverGroundMsg() { }

	/**
	 * @param raw_message raw ADS-B velocity-over-ground message as hex string
	 * @throws BadFormatException if message has wrong format
	 */
	public VelocityOverGroundMsg(String raw_message) throws BadFormatException {
		this(new ExtendedSquitter(raw_message));
	}
	
	/**
	 * @param squitter extended squitter which contains this velocity over ground msg
	 * @throws BadFormatException if message has wrong format
	 */
	public VelocityOverGroundMsg(ExtendedSquitter squitter) throws BadFormatException {
		super(squitter);
		setType(subtype.ADSB_VELOCITY);
		
		if (this.getFormatTypeCode() != 19) {
			throw new BadFormatException("Velocity messages must have typecode 19.");
		}
		
		byte[] msg = this.getMessage();
		
		msg_subtype = (byte) (msg[0]&0x7);
		if (msg_subtype != 1 && msg_subtype != 2) {
			throw new BadFormatException("Ground speed messages have subtype 1 or 2.");
		}
		
		intent_change = (msg[1]&0x80)>0;
		ifr_capability = (msg[1]&0x40)>0;
		navigation_accuracy_category = (byte) ((msg[1]>>>3)&0x7);
		
		// check this later
		velocity_info_available = true;
		vertical_rate_info_available = true;
		geo_minus_baro_available = true;
		
		direction_west = (msg[1]&0x4)>0;
		east_west_velocity = (short) (((msg[1]&0x3)<<8 | msg[2]&0xFF)-1);
		if (east_west_velocity == -1) velocity_info_available = false;
		if (msg_subtype == 2) east_west_velocity<<=2;
		
		direction_south = (msg[3]&0x80)>0;
		north_south_velocity = (short) (((msg[3]&0x7F)<<3 | msg[4]>>>5&0x07)-1);
		if (north_south_velocity == -1) velocity_info_available = false;
		if (msg_subtype == 2) north_south_velocity<<=2;

		vertical_source = (msg[4]&0x10)>0;
		vertical_rate_down = (msg[4]&0x08)>0;
		vertical_rate = (short) ((((msg[4]&0x07)<<6 | msg[5]>>>2&0x3F)-1)<<6);
		if (vertical_rate == -1) vertical_rate_info_available = false;
		
		geo_minus_baro = (short) (((msg[6]&0x7F)-1)*25);
		if (geo_minus_baro == -1) geo_minus_baro_available = false;
		if ((msg[6]&0x80)>0) geo_minus_baro *= -1;
	}

	/**
	 * Must be checked before accessing velocity!
	 * 
	 * @return whether velocity info is available
	 */
	public boolean hasVelocityInfo() {
		return velocity_info_available;
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
		return msg_subtype == 2;
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
	 * @return velocity from east to south in m/s
	 * @throws MissingInformationException if no velocity information available
	 */
	public double getEastToWestVelocity() throws MissingInformationException {
		if (!velocity_info_available) throw new MissingInformationException("No velocity info available!");
		return (direction_west ? east_west_velocity : -east_west_velocity) * 0.514444;
	}


	/**
	 * @return velocity from north to south in m/s
	 * @throws MissingInformationException if no velocity information available
	 */
	public double getNorthToSouthVelocity() throws MissingInformationException {
		if (!velocity_info_available) throw new MissingInformationException("No velocity info available!");
		return (direction_south ? north_south_velocity : -north_south_velocity) * 0.514444;
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
		if (!velocity_info_available) throw new MissingInformationException("No velocity info available!");
		double angle = Math.toDegrees(Math.atan2(
				-this.getEastToWestVelocity(),
				-this.getNorthToSouthVelocity()));
		
		// if negative => clockwise
		if (angle < 0) return 360+angle;
		else return angle;
	}
	
	/**
	 * @return speed over ground in m/s
	 * @throws MissingInformationException if no velocity info is available
	 */
	public double getVelocity() throws MissingInformationException {
		if (!velocity_info_available) throw new MissingInformationException("No velocity info available!");
		return Math.hypot(north_south_velocity, east_west_velocity) * 0.514444;
	}
	
	public String toString() {
		String ret = super.toString()+"\n"+
				"Velocity over ground:\n";
		try { ret += "\tNorth to south velocity:\t"+getNorthToSouthVelocity()+"\n"; }
		catch (Exception e) { ret += "\tNorth to south velocity:\t\tnot available\n"; }
		try { ret += "\tEast to west velocity:\t\t"+getEastToWestVelocity()+"\n"; }
		catch (Exception e) { ret += "\tEast to west velocity:\t\tnot available\n"; }
		try { ret += "\tVelocity:\t\t\t"+getVelocity()+"\n"; }
		catch (Exception e) { ret += "\tVelocity:\t\t\tnot available\n"; }
		try { ret += "\tHeading\t\t\t\t"+getHeading()+"\n"; }
		catch (Exception e) { ret += "\tHeading\t\t\t\tnot available\n"; }
		try { ret += "\tVertical rate:\t\t\t"+getVerticalRate(); }
		catch (Exception e) { ret += "\tVertical rate:\t\t\tnot available"; }
		
		return ret;
	}
}
