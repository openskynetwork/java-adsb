package org.opensky.libadsb.msgs;

import java.io.Serializable;

import org.opensky.libadsb.Position;
import org.opensky.libadsb.tools;
import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.MissingInformationException;
import org.opensky.libadsb.exceptions.PositionStraddleError;

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
 * Decoder for ADS-B surface position messages
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class SurfacePositionMsg extends ExtendedSquitter implements Serializable {

	private static final long serialVersionUID = 8854492255470317616L;
	private boolean horizontal_position_available;
	private byte movement;
	private boolean heading_status; // is heading valid?
	private byte ground_track;
	private boolean time_flag;
	private boolean cpr_format;
	private int cpr_encoded_lat;
	private int cpr_encoded_lon;
	private boolean nic_supplA;
	private boolean nic_supplC;
	private static final int[] lon_offs = new int[] {90, 180, 270};

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected SurfacePositionMsg() { }

	/**
	 * @param raw_message raw ADS-B surface position message as hex string
	 * @throws BadFormatException if message has wrong format
	 */
	public SurfacePositionMsg(String raw_message) throws BadFormatException {
		this(new ExtendedSquitter(raw_message));
	}
	
	/**
	 * @param squitter extended squitter which contains this surface position msg
	 * @throws BadFormatException if message has wrong format
	 */
	public SurfacePositionMsg(ExtendedSquitter squitter) throws BadFormatException {
		super(squitter);
		setType(subtype.ADSB_SURFACE_POSITION);

		if (!(getFormatTypeCode() == 0 ||
				(getFormatTypeCode() >= 5 && getFormatTypeCode() <= 8)))
			throw new BadFormatException("This is not a position message! Wrong format type code ("+getFormatTypeCode()+").");

		byte[] msg = getMessage();

		horizontal_position_available = getFormatTypeCode() != 0;

		movement = (byte) ((((msg[0]&0x7)<<4) | ((msg[1]&0xF0)>>>4))&0x7F);
		heading_status = (msg[1]&0x8) != 0;
		ground_track = (byte) ((((msg[1]&0x7)<<4) | ((msg[2]&0xF0)>>>4))&0x7F);

		time_flag = ((msg[2]>>>3)&0x1) == 1;
		cpr_format = ((msg[2]>>>2)&0x1) == 1;
		cpr_encoded_lat = (((msg[2]&0x3)<<15) | ((msg[3]&0xFF)<<7) | ((msg[4]>>>1)&0x7F)) & 0x1FFFF;
		cpr_encoded_lon = (((msg[4]&0x1)<<16) | ((msg[5]&0xFF)<<8) | (msg[6]&0xFF)) & 0x1FFFF;
	}

	/**
	 * @return NIC supplement that was set before
	 */
	public boolean getNICSupplementA() {
		return nic_supplA;
	}

	/**
	 * @param nic_suppl Navigation Integrity Category (NIC) supplement from operational status message.
	 *        Otherwise worst case is assumed for containment radius limit and NIC.
	 */
	public void setNICSupplementA(boolean nic_suppl) {
		this.nic_supplA = nic_suppl;
	}
	
	/**
	 * @return NIC supplement that was set before
	 */
	public boolean getNICSupplementC() {
		return nic_supplC;
	}

	/**
	 * @param nic_suppl Navigation Integrity Category (NIC) supplement C from operational status message.
	 *        Otherwise worst case is assumed for containment radius limit and NIC. It's from the
	 *        surface capability class (CC) subfield of Operational Status Messages
	 */
	public void setNICSupplementC(boolean nic_suppl) {
		this.nic_supplC = nic_suppl;
	}
	
	/**
	 * @return horizontal containment radius limit in meters. A return value of 0 means "unkown".
	 *         If NIC supplement is set before, the return value is exactly according to DO-260B.
	 *         Otherwise it can be a little worse than it actually is. 0 means unkown.
	 */
	public double getHorizontalContainmentRadiusLimit() {
		switch (getFormatTypeCode()) {
		case 0: return 0;
		case 5: return 7.5;
		case 6: return 25;
		case 7:
			if (nic_supplA) return 75;
			else return 185.2;
		case 8:
			if (nic_supplA && nic_supplC) return 370.4;
			else if (nic_supplA && !nic_supplC) return 555.6;
			else if (!nic_supplA && nic_supplC) return 1111.2;
		default: return 0;
		}
	}

	/**
	 * @return Navigation integrity category. A NIC of 0 means "unkown".
	 *         If NIC supplement is set before, the return value is exactly according to DO-260B.
	 *         Otherwise it might be a little worse than it actually is.
	 */
	public byte getNavigationIntegrityCategory() {
		switch (getFormatTypeCode()) {
		case 0: return 0;
		case 5: return 11;
		case 6: return 10;
		case 7:
			if (nic_supplA) return 9;
			else return 8;
		case 8:
			if (nic_supplA && nic_supplC) return 7;
			else if (nic_supplA && !nic_supplC) return 6;
			else if (!nic_supplA && nic_supplC) return 6;
			else return 0;
		default: return 0;
		}
	}

	/**
	 * @return whether horizontal position information is available
	 */
	public boolean hasPosition() {
		return horizontal_position_available;
	}
	
	/**
	 * @return whether ground speed information is available
	 */
	public boolean hasGroundSpeed() {
		return movement >= 1 && movement <= 124;
	}
	
	/**
	 * @return speed in m/s
	 * @throws MissingInformationException if ground speed is not available
	 */
	public double getGroundSpeed() throws MissingInformationException {
		double speed;
		
		if (movement == 1)
			speed = 0;
		else if (movement >= 2 && movement <= 8)
			speed = 0.125+(movement-2)*0.125;
		else if (movement >= 9 && movement <= 12)
			speed = 1+(movement-9)*0.25;
		else if (movement >= 13 && movement <= 38)
			speed = 2+(movement-13)*0.5;
		else if (movement >= 39 && movement <= 93)
			speed = 15+(movement-39);
		else if (movement >= 94 && movement <= 108)
			speed = 70+(movement-94)*2;
		else if (movement >= 109 && movement <= 123)
			speed = 100+(movement-109)*5;
		else if (movement == 124)
			speed = 175;
		else
			throw new MissingInformationException("Ground speed info not available!");
		
		return speed*0.514444;
	}
	
	/**
	 * @return speed resolution (accuracy) in m/s
	 * @throws MissingInformationException if ground speed is not available
	 */
	public double getGroundSpeedResolution() throws MissingInformationException {
		double resolution;
		
		if (movement >= 1 && movement <= 8)
			resolution = 0.125;
		else if (movement >= 9 && movement <= 12)
			resolution = 0.25;
		else if (movement >= 13 && movement <= 38)
			resolution = 0.5;
		else if (movement >= 39 && movement <= 93)
			resolution = 1;
		else if (movement >= 94 && movement <= 108)
			resolution = 2;
		else if (movement >= 109 && movement <= 123)
			resolution = 5;
		else if (movement == 124)
			resolution = 175;
		else
			throw new MissingInformationException("Ground speed info not available!");
		
		return resolution*0.514444;
	}
	
	/**
	 * @return whether valid heading information is available
	 */
	public boolean hasValidHeading() {
		return heading_status;
	}
	
	/**
	 * @return heading in decimal degrees ([0, 360]). 0° = geographic north
	 * @throws MissingInformationException if no valid heading info is available
	 */
	public double getHeading() throws MissingInformationException {
		if (!heading_status)
			throw new MissingInformationException("No valid heading information available!");
		
		return ground_track*360/128;
	}

	/**
	 * @return flag which will indicate whether or not the Time of Applicability of the message
	 *         is synchronized with UTC time. False will denote that the time is not synchronized
	 *         to UTC. True will denote that Time of Applicability is synchronized to UTC time.
	 */
	public boolean isTime_flag() {
		return time_flag;
	}

	/**
	 * @return the CPR encoded binary latitude
	 */
	public int getCPREncodedLatitude() {
		return cpr_encoded_lat;
	}

	/**
	 * @return the CPR encoded binary longitude
	 */
	public int getCPREncodedLongitude() {
		return cpr_encoded_lon;
	}

	/**
	 * @return whether message is odd format. Returns false if message is even format. This is
	 *         needed for position decoding as the CPR algorithm uses both formats.
	 */
	public boolean isOddFormat() {
		return cpr_format;
	}

	/**
	 * @return true, if barometric altitude. False if GNSS is used to determine altitude
	 */
	public boolean isBarometricAltitude() {
		return this.getFormatTypeCode() < 20;
	}

	/**
	 * @param Rlat Even or odd Rlat value (CPR internal)
	 * @return the number of even longitude zones at a latitude
	 */
	private double NL(double Rlat) {
		if (Rlat == 0) return 59;
		else if (Math.abs(Rlat) == 87) return 2;
		else if (Math.abs(Rlat) > 87) return 1;

		double tmp = 1-(1-Math.cos(Math.PI/(2.0*15.0)))/Math.pow(Math.cos(Math.PI/180.0*Math.abs(Rlat)), 2);
		return Math.floor(2*Math.PI/Math.acos(tmp));
	}
	
	/**
	 * Modulo operator in java has stupid behavior
	 */
	private static double mod(double a, double b) {
		return ((a%b)+b)%b;
	}

	/**
	 * This method can only be used if another position report with a different format (even/odd) is available
	 * and set with msg.setOtherFormatMsg(other).
	 * @param other position message of the other format (even/odd). Note that the time between
	 *        both messages should be not longer than 50 seconds! 
	 * @param ref reference position used to resolve the ambiguity of the CPR result by choosing the closest
	 *        position to the reference position
	 * @return globally unambiguously decoded position tuple (latitude, longitude). The positional
	 *         accuracy maintained by the CPR encoding will be approximately 1.25 meters.
	 *         A message of the other format is needed for global decoding.
	 * @throws MissingInformationException if no position information is available in one of the messages
	 * @throws IllegalArgumentException if input message was emitted from a different transmitter
	 * @throws PositionStraddleError if position messages straddle latitude transition
	 * @throws BadFormatException other has the same format (even/odd)
	 */
	public Position getGlobalPosition(SurfacePositionMsg other, Position ref) throws MissingInformationException, 
		PositionStraddleError, BadFormatException {
		if (!tools.areEqual(other.getIcao24(), getIcao24()))
				throw new IllegalArgumentException(
						String.format("Transmitter of other message (%s) not equal to this (%s).",
						tools.toHexString(other.getIcao24()), tools.toHexString(this.getIcao24())));
		
		if (other.isOddFormat() == this.isOddFormat())
			throw new BadFormatException("Expected "+(isOddFormat()?"even":"odd")+" message format.", other.toString());

		if (!horizontal_position_available)
			throw new MissingInformationException("No position information available!");
		if (!other.hasPosition())
			throw new MissingInformationException("Other message has no position information.");

		SurfacePositionMsg even = isOddFormat()?other:this;
		SurfacePositionMsg odd = isOddFormat()?this:other;

		// Helper for latitude single(Number of zones NZ is set to 15)
		double Dlat0 = 90.0/60.0;
		double Dlat1 = 90.0/59.0;

		// latitude index
		double j = Math.floor((59.0*even.getCPREncodedLatitude()-60.0*odd.getCPREncodedLatitude())/((double)(1<<17))+0.5);

		// global latitudes
		double Rlat0 = Dlat0 * (mod(j,60)+even.getCPREncodedLatitude()/((double)(1<<17)));
		double Rlat1 = Dlat1 * (mod(j,59)+odd.getCPREncodedLatitude()/((double)(1<<17)));
		
		// Southern hemisphere?
		if (Rlat0 >= 270 && Rlat0 <= 360) Rlat0 -= 360;
		if (Rlat1 >= 270 && Rlat1 <= 360) Rlat1 -= 360;

		// Northern hemisphere?
		if (Rlat0 <= -270 && Rlat0 >= -360) Rlat0 += 360;
		if (Rlat1 <= -270 && Rlat1 >= -360) Rlat1 += 360;

		// ensure that the number of even longitude zones are equal
		if (NL(Rlat0) != NL(Rlat1))
			throw new org.opensky.libadsb.exceptions.PositionStraddleError(
				"The two given position straddle a transition latitude "+
				"and cannot be decoded. Wait for positions where they are equal.");

		// Helper for longitude
		double Dlon0 = 90.0/Math.max(1.0, NL(Rlat0));
		double Dlon1 = 90.0/Math.max(1.0, NL(Rlat1)-1);

		// longitude index
		double NL_helper = NL(isOddFormat()?Rlat1:Rlat0); // assuming that this is the newer message
		double m = Math.floor((even.getCPREncodedLongitude()*(NL_helper-1)-odd.getCPREncodedLongitude()*NL_helper)/((double)(1<<17))+0.5);

		// global longitude
		double Rlon0 = Dlon0 * (mod(m,Math.max(1.0, NL(Rlat0))) + even.getCPREncodedLongitude()/((double)(1<<17)));
		double Rlon1 = Dlon1 * (mod(m,Math.max(1.0, NL(Rlat1)-1)) + odd.getCPREncodedLongitude()/((double)(1<<17)));
		
		Double lon = isOddFormat() ? Rlon1 : Rlon0;
		Double lat = isOddFormat() ? Rlat1 : Rlat0;
		
		// check the four possible positions
		Position tmp, result = new Position(lon, lat, 0.);
		for (int o : lon_offs) {
			tmp = new Position(lon+o, lat, 0.0);
			if (tmp.distanceTo(ref) < result.distanceTo(ref))
				result = tmp;
		}
		
		if (result.getLongitude()>180)
			result.setLongitude(result.getLongitude()-360);
		
		return result;
	}
	
	/**
	 * This method uses a locally unambiguous decoding for position messages. It
	 * uses a reference position known to be within 45NM (= 83.34km) of the true target
	 * position. the reference point may be a previously tracked position that has
	 * been confirmed by global decoding (see getGlobalPosition()).
	 * @param ref reference position for local CPR
	 * @return decoded position. The positional
	 *         accuracy maintained by the CPR encoding will be approximately 5.1 meters.
	 * @throws MissingInformationException if no position information is available
	 */
	public Position getLocalPosition(Position ref) throws MissingInformationException {
		if (!horizontal_position_available)
			throw new MissingInformationException("No position information available!");
		
		// latitude zone size
		double Dlat = isOddFormat() ? 90.0/59.0 : 90.0/60.0;
		
		// latitude zone index
		double j = Math.floor(ref.getLatitude()/Dlat) + Math.floor(0.5+(mod(ref.getLatitude(), Dlat))/Dlat-getCPREncodedLatitude()/((double)(1<<17)));
		
		// decoded position latitude
		double Rlat = Dlat*(j+getCPREncodedLatitude()/((double)(1<<17)));
		
		// longitude zone size
		double Dlon = 90.0/Math.max(1.0, NL(Rlat)-(isOddFormat()?1.0:0.0));
		
		// longitude zone coordinate
		double m =
				Math.floor(ref.getLongitude()/Dlon) +
				Math.floor(0.5+(mod(ref.getLongitude(),Dlon))/Dlon-(float)getCPREncodedLongitude()/((double)(1<<17)));
		
		// and finally the longitude
		double Rlon = Dlon * (m + getCPREncodedLongitude()/((double)(1<<17)));
		
//		System.out.println("Loc: EncLon: "+getCPREncodedLongitude()+
//				" m: "+m+" Dlon: "+Dlon+ " Rlon2: "+Rlon2);
		
		return new Position(Rlon, Rlat, 0.0);
	}
	
	public String toString() {
		try {
			return super.toString()+"\n"+
					"Surface Position:\n"+
					"\tSpeed:\t\t"+(hasGroundSpeed() ? getGroundSpeed() : "unkown")+
					"\n\tSpeed Resolution:\t\t"+(hasGroundSpeed() ? getGroundSpeedResolution() : "unkown")+
					"\n\tHeading:\t\t"+(hasValidHeading() ? getHeading() : "unkown")+
					"\n\tFormat:\t\t"+(isOddFormat()?"odd":"even")+
					"\n\tHas position:\t"+(hasPosition()?"yes":"no");
		} catch (Exception e) {
			// should never happen
			return "An exception was thrown.";
		}
	}

}
