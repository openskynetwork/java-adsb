package org.opensky.libadsb.msgs;

import java.io.Serializable;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.MissingInformationException;
import org.opensky.libadsb.exceptions.UnspecifiedFormatError;

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
 * Decoder for ADS-B operational status messages (Page N-50, DO-260B)
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class OperationalStatusMsg extends ExtendedSquitter implements Serializable {

	private static final long serialVersionUID = 8257765069421399591L;
	private byte subtype_code;
	private int capability_class_code; // actually 16 bit unsigned
	private int operational_mode_code; // actually 16 bit unsigned
	private byte airplane_len_width; // only in subtype_code == 1 surface msgs
	private byte version;
	private boolean nic_suppl; // may be passed to position messages
	private byte nac_pos; // navigational accuracy category - position
	private byte geometric_vertical_accuracy; // bit 49 and 50
	private byte sil; // surveillance integrity level
	private boolean nic_trk_hdg; // NIC baro for airborne status, heading/ground track info else
	private boolean hrd; // heading info is based on true north (0) or magnetic north (1)

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected OperationalStatusMsg() { }

	/**
	 * @param raw_message The full Mode S message in hex representation
	 * @throws BadFormatException if message has the wrong typecode
	 * @throws UnspecifiedFormatError if message has the wrong subtype
	 */
	public OperationalStatusMsg(String raw_message) throws BadFormatException, UnspecifiedFormatError {
		this(new ExtendedSquitter(raw_message));
	}
	
	/**
	 * @param squitter extended squitter which contains this message
	 * @throws BadFormatException  if message has the wrong typecode
	 * @throws UnspecifiedFormatError if message has the wrong subtype
	 */
	public OperationalStatusMsg(ExtendedSquitter squitter) throws BadFormatException, UnspecifiedFormatError {
		super(squitter);
		setType(subtype.ADSB_STATUS);
		
		if (getFormatTypeCode() != 31) {
			throw new BadFormatException("Operational status messages must have typecode 31.");
		}
		
		byte[] msg = this.getMessage();
		
		byte subtype_code = (byte)(msg[0] & 0x7);
		if (subtype_code > 1) // currently only 0 and 1 specified, 2-7 are reserved
			throw new UnspecifiedFormatError("Operational status message subtype "+subtype_code+" reserved.");
		
		if (subtype_code == 0) { // airborne
			capability_class_code = (msg[1]<<8)|msg[2];
		}
		else { // surface
			capability_class_code = (msg[1]<<4)|(msg[2]&0xF0)>>>4;
			airplane_len_width = (byte) (msg[2]&0xF);
		}
		operational_mode_code = (msg[3]<<8)|msg[4];
		version = (byte) (msg[5]>>>5);
		nic_suppl = ((msg[5] & 0x10) != 0);
		nac_pos = (byte) (msg[5] & 0xF);
		geometric_vertical_accuracy = (byte) (msg[6]>>>6);
		sil = (byte) ((msg[6]>>>4)&0x3);
		nic_trk_hdg = ((msg[6] & 0x8) != 0);
		hrd = ((msg[6] & 0x4) != 0);
	}

	/**
	 * @return the subtype code is 0 for airborne operational status msgs
	 *         and 1 for surface operational status msgs; all other codes
	 *         are "reserved"
	 */
	public byte getSubtypeCode() {
		return subtype_code;
	}

	/**
	 * @return whether operational TCAS is available; only for subtype 0
	 * @throws MissingInformationException if message has the wrong subtype or capability class code is unknown
	 */
	public boolean hasOperationalTCAS() throws MissingInformationException {
		if ((capability_class_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown capability class code!");
		
		if (subtype_code == 0)
			return (capability_class_code & 0x2000) != 0;
		else throw new MissingInformationException("TCAS capability info not available in surface status reports.");
	}

	/**
	 * @return whether 1090ES IN is available 
	 * @throws MissingInformationException if capability class code is unknown
	 */
	public boolean has1090ESIn() throws MissingInformationException {
		if ((capability_class_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown capability class code!");
		
		return (capability_class_code & 0x1000) != 0;
	}

	/**
	 * @return whether aircraft has capability of sending messages to support Air-Referenced
	 *         Velocity Reports; only for subtype 0
	 * @throws MissingInformationException if message has the wrong subtype or capability class code is unknown
	 */
	public boolean supportsAirReferencedVelocity() throws MissingInformationException {
		if ((capability_class_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown capability class code!");
		
		if (subtype_code == 0)
			return (capability_class_code & 0x200) != 0;
		else throw new MissingInformationException("No ARV info available in surface status reports.");
	}

	/**
	 * @return whether transponder has less than 70 Watts transmit power; only for subtype 1
	 * @throws MissingInformationException if message has the wrong subtype or capability class code is unknown
	 */
	public boolean hasLowTxPower() throws MissingInformationException {
		if ((capability_class_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown capability class code!");
		
		if (subtype_code == 1)
			return (capability_class_code & 0x200) != 0;
		else throw new MissingInformationException("No Tx power info available in airborne status reports.");
	}

	/**
	 * @return whether aircraft has capability of sending messages to support Target
	 *         State Reports; only for subtype 0
	 * @throws MissingInformationException if message has the wrong subtype or capability class code is unknown
	 */
	public boolean supportsTargetStateReport() throws MissingInformationException {
		if ((capability_class_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown capability class code!");

		if (subtype_code == 0)
			return (capability_class_code & 0x100) != 0;
		else throw new MissingInformationException("No info about target state report capabilities in surface status reports.");
		
	}

	/**
	 * @return whether target change reports are supported; only for subtype 0
	 * @throws MissingInformationException if message has the wrong subtype or capability class code is unknown
	 */
	public boolean supportsTargetChangeReport() throws MissingInformationException {
		if ((capability_class_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown capability class code!");

		if (subtype_code == 0) {
			byte target_change_report_capability = (byte) (capability_class_code & 0xC0);
			return target_change_report_capability == 1 | target_change_report_capability == 2;
		}
		else throw new MissingInformationException("No info about TC report capabilities in surface status reports.");
	}

	/**
	 * @return whether aircraft has an UAT receiver
	 * @throws MissingInformationException if capability class code is unknown
	 */
	public boolean hasUATIn() throws MissingInformationException {
		if ((capability_class_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown capability class code!");
		
		return (capability_class_code & (subtype_code == 0 ? 0x20 : 0x100)) != 0;
	}

	/**
	 * @return navigation accuracy category for velocity; only for subtype 1
	 * @throws MissingInformationException if message has the wrong subtype or capability class code is unknown
	 */
	public byte getNACV() throws MissingInformationException {
		if ((capability_class_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown capability class code!");
		
		if (subtype_code == 1)
			return (byte) ((capability_class_code & 0xE0)>>>5);
		else throw new MissingInformationException("No navigation accuracy category for velocity in airborne status reports.");
	}

	/**
	 * @return NIC supplement C for use on the surface; only for subtype 1
	 * @throws MissingInformationException if message has the wrong subtype or capability class code is unknown
	 */
	public boolean getNICSupplementC() throws MissingInformationException {
		if ((capability_class_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown capability class code!");
		
		if (subtype_code == 1)
			return (capability_class_code & 0x10) != 0;
		else throw new MissingInformationException("No NIC supplement C for velocity in airborne status reports.");
	}

	/**
	 * @return whether TCAS Resolution Advisory (RA) is active
	 * @throws MissingInformationException if operational mode code is unknown
	 */
	public boolean hasTCASResolutionAdvisory() throws MissingInformationException {
		if ((operational_mode_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown operational mode code!");
		
		return (operational_mode_code&0x2000) != 0;
	}

	/**
	 * @return whether the IDENT switch is active
	 * @throws MissingInformationException if operational mode code is unknown
	 */
	public boolean hasActiveIDENTSwitch() throws MissingInformationException {
		if ((operational_mode_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown operational mode code!");
		
		return (operational_mode_code&0x1000) != 0;
	}

	/**
	 * @return whether aircraft uses a single antenna or two
	 * @throws MissingInformationException if operational mode code is unknown
	 */
	public boolean usesSingleAntenna() throws MissingInformationException {
		if ((operational_mode_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown operational mode code!");
		
		return (operational_mode_code&0x400) != 0;
	}

	/**
	 * @return system design assurance (see A.1.4.10.14 in RTCA DO-260B)
	 * @throws MissingInformationException if operational mode code is unknown
	 */
	public byte getSystemDesignAssurance() throws MissingInformationException {
		if ((operational_mode_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown operational mode code!");
		
		return (byte) ((operational_mode_code&0x300)>>>8);
	}

	/**
	 * @return encoded longitudinal distance of the GPS Antenna from the NOSE of the aircraft
	 *         (see Table A-34, RTCA DO-260B)
	 * @throws MissingInformationException if operational mode code is unknown
	 */
	public byte getGPSAntennaOffset() throws MissingInformationException {
		if ((operational_mode_code & 0xC000) != 0)
			throw new MissingInformationException("Unknown operational mode code!");
		
		if (subtype_code == 1)
			return (byte) (operational_mode_code&0xFF);
		else throw new MissingInformationException("No information about GPS antenna offset in airborne status reports.");
	}

	/**
	 * @return the airplane's length in meters; -1 for unkown
	 * @throws MissingInformationException if message has the wrong subtype
	 */
	public int getAirplaneLength() throws MissingInformationException {
		if (subtype_code == 1)
			switch (airplane_len_width) {
			case 1: return 15;
			case 2: case 3: return 25;
			case 4: case 5: return 35;
			case 6: case 7: return 45;
			case 8: case 9: return 55;
			case 10: case 11: return 65;
			case 12: case 13: return 75;
			case 14: case 15: return 85;
			default: return -1;
			}
		else throw new MissingInformationException("No aircraft size information in airborne status reports.");
	}

	/**
	 * @return the airplane's width in meters
	 * @throws MissingInformationException if message has the wrong subtype
	 */
	public double getAirplaneWidth() throws MissingInformationException {
		if (subtype_code == 1)
			switch (airplane_len_width) {
			case 1: return 23;
			case 2: return 28.5;
			case 3: return 34;
			case 4: return 33;
			case 5: return 38;
			case 6: return 39.5;
			case 7: return 45;
			case 8: return 45;
			case 9: return 52;
			case 10: return 59.5;
			case 11: return 67;
			case 12: return 72.5;
			case 13: return 80;
			case 14: return 80;
			case 15: return 90;
			default: return -1;
			}
		else throw new MissingInformationException("No aircraft size information in airborne status reports.");
	}

	/**
	 * @return the version number of the formats and protocols in use on the aircraft installation.<br>
	 *         0: Conformant to DO-260/ED-102 and DO-242<br>
	 *         1: Conformant to DO-260A and DO-242A<br>
	 *         2: Conformant to DO-260B/ED-102A and DO-242B<br>
	 *         3-7: reserved
	 */
	public byte getVersion() {
		return version;
	}

	/**
	 * @return the NIC supplement A to the format type code of position messages
	 */
	public boolean getNICSupplementA() {
		return nic_suppl;
	}

	/**
	 * @return the navigation accuracy for position messages; rather use getPositionUncertainty
	 */
	public byte getPositionNAC() {
		return nac_pos;
	}

	/**
	 * @return the estimated position uncertainty according to the position NAC in meters (-1 for unknown)
	 */
	public double getPositionUncertainty() {
		switch (nac_pos) {
		case 1: return 18.52;
		case 2: return 7.408;
		case 3: return 3.704;
		case 4: return 1852.0;
		case 5: return 926.0;
		case 6: return 555.6;
		case 7: return 185.2;
		case 8: return 92.6;
		case 9: return 30.0;
		case 10: return 10.0;
		case 11: return 3.0;
		default: return -1;
		}
	}

	/**
	 * @return the geometric vertical accuracy in meters or -1 for unknown
	 */
	public int getGeometricVerticalAccuracy() {
		if (geometric_vertical_accuracy == 1)
			return 150;
		else if (geometric_vertical_accuracy == 2)
			return 45;
		else return -1;
	}

	/**
	 * @return the source integrity level (SIL) which indicates the propability of exceeding
	 *         the NIC containment radius (see table A-15 in RCTA DO-260B)
	 */
	public byte getSourceIntegrityLevel() {
		return sil;
	}

	/**
	 * @return the barometric altitude integrity code which indicates whether
	 *         barometric pressure altitude has been cross-checked against other
	 *         sources of pressure altitude. If false, altitude data has not been
	 *         cross-checked.
	 * @throws MissingInformationException if this is a surface message
	 */
	public boolean getBarometricAltitudeIntegrityCode() throws MissingInformationException {
		if (subtype_code == 0)
			return nic_trk_hdg;
		else throw new MissingInformationException("No barometric altitude integrity code in surface messages.");
	}

	/**
	 * @return the Track Angle/Heading allows correct interpretation of the data
	 *         contained in the Heading/Ground Track subfield of ADS-B Surface
	 *         Position Messages.
	 * @throws MissingInformationException if no additional track/heading info is available
	 */
	public boolean getTrackHeadingInfo() throws MissingInformationException {
		if (subtype_code == 1)
			return nic_trk_hdg;
		else throw new MissingInformationException("No additional track/heading info available in airborne status reports.");
	}

	/**
	 * @return 0 if horizontal reference direction is the true north, 1 if magnetic north
	 */
	public boolean getHorizontalReferenceDirection() {
		return hrd;
	}

	/* (non-Javadoc)
	 * @see org.opensky.libadsb.ExtendedSquitter#toString()
	 */
	public String toString() {
		String retstr;
		if (subtype_code == 0) { // airborne stuff
			retstr = "Airborne operational status:\n";
			retstr += "\tHas TCAS: ";
			try  {
				retstr += (hasOperationalTCAS() ? "yes" : "no")+"\n";
			} catch (MissingInformationException e) {
				retstr += "unknown\n";
			}
			retstr += "\tAir-referenced velocity: ";
			try  {
				retstr += (supportsAirReferencedVelocity() ? "yes" : "no")+"\n";
			} catch (MissingInformationException e) {
				retstr += "unknown\n";
			}
			retstr += "\tTarget State Reports: ";
			try  {
				retstr += (supportsTargetStateReport() ? "yes" : "no")+"\n";
			} catch (MissingInformationException e) {
				retstr += "unknown\n";
			}
			retstr += "\tTarget Change Reports: ";
			try  {
				retstr += (supportsTargetChangeReport() ? "yes" : "no")+"\n";
			} catch (MissingInformationException e) {
				retstr += "unknown\n";
			}
			retstr += "\tBarometric altitude cross-checked: ";
			try  {
				retstr += (getBarometricAltitudeIntegrityCode() ? "yes" : "no")+"\n";
			} catch (MissingInformationException e) {
				retstr += "unknown\n";
			}
		}
		else if (subtype_code == 1) {
			retstr = "Surfce operational status:\n";
			retstr += "\tUses low tx power: ";
			try  {
				retstr += (hasLowTxPower() ? "yes" : "no")+"\n";
			} catch (MissingInformationException e) {
				retstr += "unknown\n";
			}
			retstr += "\tNAC category velocity: ";
			try  {
				retstr += getNACV()+"\n";
			} catch (MissingInformationException e) {
				retstr += "unknown\n";
			}
			retstr += "\tGPA antenna offset: ";
			try  {
				retstr += getGPSAntennaOffset()+"\n";
			} catch (MissingInformationException e) {
				retstr += "unknown\n";
			}
			retstr += "\tAirplane length/width: ";
			try  {
				retstr += getAirplaneLength()+"/"+getAirplaneWidth()+"\n";
			} catch (MissingInformationException e) {
				retstr += "unknown\n";
			}
			retstr += "\tTrack Angle/Heading info: ";
			try  {
				retstr += (hasLowTxPower() ? "true" : "false")+"\n";
			} catch (MissingInformationException e) {
				retstr += "unknown\n";
			}
		}
		else return super.toString()+"\nUnspecified Operational Status message.";
		
		retstr += "\tHas 1090ES IN: ";
		try  {
			retstr += (has1090ESIn() ? "yes" : "no")+"\n";
		} catch (MissingInformationException e) {
			retstr += "unknown\n";
		}
		retstr += "\tHas UAT IN: ";
		try  {
			retstr += (hasUATIn() ? "yes" : "no")+"\n";
		} catch (MissingInformationException e) {
			retstr += "unknown\n";
		}
		retstr += "\tUses TCAS: ";
		try  {
			retstr += (hasTCASResolutionAdvisory() ? "yes" : "no")+"\n";
		} catch (MissingInformationException e) {
			retstr += "unknown\n";
		}
		retstr += "\tIDENT Switch on: ";
		try  {
			retstr += (hasActiveIDENTSwitch() ? "yes" : "no")+"\n";
		} catch (MissingInformationException e) {
			retstr += "unknown\n";
		}
		retstr += "\tUses single antenna: ";
		try  {
			retstr += (usesSingleAntenna() ? "yes" : "no")+"\n";
		} catch (MissingInformationException e) {
			retstr += "unknown\n";
		}
		retstr += "\tSystem design assurance: ";
		try  {
			retstr += getSystemDesignAssurance()+"\n";
		} catch (MissingInformationException e) {
			retstr += "unknown\n";
		}
		retstr += "\tADS-B version: "+getVersion()+"\n";
		retstr += "\tNIC supplement A: ";
		retstr += (getNICSupplementA() ? "true" : "false")+"\n";
		retstr += "\tPosition NAC: "+getPositionNAC()+"\n";
		retstr += "\tVertical Accuracy: "+getGeometricVerticalAccuracy()+"\n";
		retstr += "\tSource Integrity Level: "+getSourceIntegrityLevel()+"\n";
		retstr += "\tHorizontal reference: "+getHorizontalReferenceDirection();
		
		return super.toString()+"\n"+retstr;
	}
	
}
