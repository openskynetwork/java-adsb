package org.opensky.libadsb;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.UnspecifiedFormatError;
import org.opensky.libadsb.msgs.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

/**
 * Generic decoder for Mode S Messages.
 *
 * @author Markus Fuchs (fuchs@opensky-network.org)
 */
public class ModeSDecoder {
	private Map<byte[], DecoderData> decoderData;
	private int afterLastCleanup;
	private long latestTimestamp;

	/**
	 * This function decodes a half-decoded Mode S reply to its
	 * deepest possible specialization. Use getType() to check its
	 * actual type afterwards.
	 * @param modes the incompletely decoded Mode S message
	 * @return an instance of the most specialized ModeSReply possible
	 * @throws UnspecifiedFormatError if format is not specified
	 * @throws BadFormatException if format contains error
	 */
	public ModeSReply decode(ModeSReply modes) throws BadFormatException, UnspecifiedFormatError {
		if (++afterLastCleanup > 100000 && decoderData.size() > 30000) gc();

		switch (modes.getDownlinkFormat()) {
			case 0: return new ShortACAS(modes);
			case 4: return new AltitudeReply(modes);
			case 5: return new IdentifyReply(modes);
			case 11: return new AllCallReply(modes);
			case 16: return new LongACAS(modes);
			case 17: case 18: case 19:
				// check whether this is an ADS-B message (see Figure 2-2, RTCA DO-260B)
				if (modes.getDownlinkFormat() == 17 ||
						modes.getDownlinkFormat() == 18 && modes.getFirstField() < 2 ||
						modes.getDownlinkFormat() == 19 && modes.getFirstField() == 0) {

					// interpret ME field as standard ADS-B
					ExtendedSquitter es1090 = new ExtendedSquitter(modes);

					// we need stateful decoding, because ADS-B version > 0 can only be assumed
					// if matching version info in operational status has been found.
					DecoderData dd = this.decoderData.get(modes.getIcao24());
					if (dd == null) {
						// create new DecoderData
						// assume ADS-B version 0 (as demanded by DO-260B N.1.2
						dd = new DecoderData();
						decoderData.put(modes.getIcao24(), dd);
					}

					// what kind of extended squitter?
					byte ftc = es1090.getFormatTypeCode();

					if (ftc >= 1 && ftc <= 4) // identification message
						return new IdentificationMsg(es1090);

					if (ftc >= 5 && ftc <= 8) {
						// surface position message
						switch(dd.adsbVersion) {
							case 0:
								return new SurfacePositionV0Msg(es1090);
							case 1:
								SurfacePositionV1Msg s1 = new SurfacePositionV1Msg(es1090);
								s1.setNICSupplementA(dd.nicSupplA);
								return s1;
							case 2:
								SurfacePositionV2Msg s2 = new SurfacePositionV2Msg(es1090);
								s2.setNICSupplementA(dd.nicSupplA);
								s2.setNICSupplementC(dd.nicSupplC);
								return s2;
						}
					}

					if ((ftc >= 9 && ftc <= 18) || (ftc >= 20 && ftc <= 22)) {
						// airborne position message
						switch(dd.adsbVersion) {
							case 0:
								return new AirbornePositionV0Msg(es1090);
							case 1:
								AirbornePositionV1Msg a1 = new AirbornePositionV1Msg(es1090);
								a1.setNICSupplementA(dd.nicSupplA);
								return a1;
							case 2:
								AirbornePositionV2Msg a2 = new AirbornePositionV2Msg(es1090);
								a2.setNICSupplementA(dd.nicSupplA);
								return a2;
						}
					}

					if (ftc == 19) { // possible velocity message, check subtype
						int subtype = es1090.getMessage()[0] & 0x7;

						if (subtype == 1 || subtype == 2) // velocity over ground
							return new VelocityOverGroundMsg(es1090);
						else if (subtype == 3 || subtype == 4) // airspeed & heading
							return new AirspeedHeadingMsg(es1090);
					}

					if (ftc == 28) { // aircraft status message, check subtype
						int subtype = es1090.getMessage()[0] & 0x7;

						if (subtype == 1) // emergency/priority status
							return new EmergencyOrPriorityStatusMsg(es1090);
						if (subtype == 2) // TCAS resolution advisory report
							return new TCASResolutionAdvisoryMsg(es1090);
					}

					if (ftc == 31) { // operational status message
						int subtype = es1090.getMessage()[0] & 0x7;

						dd.adsbVersion = (byte) (es1090.getMessage()[5]>>>5);
						if (subtype == 0) {
							// airborne
							switch (dd.adsbVersion) {
								case 0:
									return new OperationalStatusV0Msg(es1090);
								case 1:
									AirborneOperationalStatusV1Msg s1 = new AirborneOperationalStatusV1Msg(es1090);
									dd.nicSupplA = s1.getNICSupplementA();
									return s1;
								case 2:
									AirborneOperationalStatusV2Msg s2 = new AirborneOperationalStatusV2Msg(es1090);
									dd.nicSupplA = s2.getNICSupplementA();
									return s2;
							}
						} else if (subtype == 1) {
							// surface
							switch (dd.adsbVersion) {
								case 0:
									return new OperationalStatusV0Msg(es1090);
								case 1:
									SurfaceOperationalStatusV1Msg s1 = new SurfaceOperationalStatusV1Msg(es1090);
									dd.nicSupplA = s1.getNICSupplementA();
									dd.nicSupplC = s1.getNICSupplementC();
									return s1;
								case 2:
									SurfaceOperationalStatusV2Msg s2 = new SurfaceOperationalStatusV2Msg(es1090);
									dd.nicSupplA = s2.getNICSupplementA();
									dd.nicSupplC = s2.getNICSupplementC();
									return s2;
							}
						}
					}

					return es1090; // unknown extended squitter
				} else if (modes.getDownlinkFormat() == 18 && modes.getFirstField() == 6) {
					// TODO: ADS-R message (minor differences to ADS-B, see 2.2.18 in DO-260B
					return modes;
				} else if (modes.getDownlinkFormat() == 18 && modes.getFirstField() < 4 ||
						modes.getDownlinkFormat() == 18 && modes.getFirstField() == 5) {
					// TODO: TIS-B "ME" field
					// check IMF field for AA interpretation
					return modes;
				} else if (modes.getDownlinkFormat() == 18 && modes.getFirstField() == 4) {
					// TODO: TIS-B or ADS-R Management Message
					return modes;
				} else if (modes.getDownlinkFormat() == 19) {
					return new MilitaryExtendedSquitter(modes);
				}

				return modes; // this should never happen
			case 20: return new CommBAltitudeReply(modes);
			case 21: return new CommBIdentifyReply(modes);
			default:
				if (modes.getDownlinkFormat()>=24)
					return new CommDExtendedLengthMsg(modes);
				else return modes; // unknown mode s reply
		}
	}

	public <T extends SurfacePositionV0Msg> Position decodePosition(long time, T surfPos, Position receiverPos) {
		latestTimestamp = Math.max(latestTimestamp, time);
		DecoderData dd = this.decoderData.get(surfPos.getIcao24());
		if (dd == null) {
			dd = new DecoderData();
			decoderData.put(surfPos.getIcao24(), dd);
		}
		return dd.posDec.decodePosition(time/1000, surfPos, receiverPos);
	}

	public <T extends AirbornePositionV0Msg> Position decodePosition(long time, T airPos, Position receiverPos) {
		latestTimestamp = Math.max(latestTimestamp, time);
		DecoderData dd = this.decoderData.get(airPos.getIcao24());
		if (dd == null) {
			dd = new DecoderData();
			decoderData.put(airPos.getIcao24(), dd);
		}
		return dd.posDec.decodePosition(time/1000, receiverPos, airPos);
	}

	/**
	 * @param raw_message the Mode S message as byte array
	 * @return an instance of the most specialized ModeSReply possible
	 * @throws UnspecifiedFormatError if format is not specified
	 * @throws BadFormatException if format contains error
	 */
	public ModeSReply decode(byte[] raw_message) throws BadFormatException, UnspecifiedFormatError {
		return decode(new ModeSReply(raw_message));
	}

	/**
	 * @param raw_message the Mode S message as byte array
	 * @param noCRC indicates whether the CRC has been subtracted from the parity field
	 * @return an instance of the most specialized ModeSReply possible
	 * @throws UnspecifiedFormatError if format is not specified
	 * @throws BadFormatException if format contains error
	 */
	public ModeSReply decode(byte[] raw_message, boolean noCRC) throws BadFormatException, UnspecifiedFormatError {
		return decode(new ModeSReply(raw_message, noCRC));
	}

	/**
	 * @param raw_message the Mode S message in hex representation
	 * @return an instance of the most specialized ModeSReply possible
	 * @throws UnspecifiedFormatError if format is not specified
	 * @throws BadFormatException if format contains error
	 */
	public ModeSReply decode(String raw_message) throws BadFormatException, UnspecifiedFormatError {
		return decode(new ModeSReply(raw_message));
	}

	/**
	 * @param raw_message the Mode S message in hex representation
	 * @param noCRC indicates whether the CRC has been subtracted from the parity field
	 * @return an instance of the most specialized ModeSReply possible
	 * @throws UnspecifiedFormatError if format is not specified
	 * @throws BadFormatException if format contains error
	 */
	public ModeSReply decode(String raw_message, boolean noCRC) throws BadFormatException, UnspecifiedFormatError {
		return decode(new ModeSReply(raw_message, noCRC));
	}

	/**
	 * Clean state by removing decoders not used for more than an hour. This happens automatically
	 * every 1 Mio messages if more than 50000 aircraft are tracked.
	 */
	public void gc() {
		List<byte[]> toRemove = new ArrayList<byte[]>();
		for (byte[] key : decoderData.keySet())
			if (decoderData.get(key).posDec.getLastUsedTime()<latestTimestamp-3600000)
				toRemove.add(key);

		for (byte[] key : toRemove)
			decoderData.remove(key);
	}

	/**
	 * Represents the state of a decoder for a certain aircraft
	 */
	private static class DecoderData {
		byte adsbVersion = 0;
		boolean nicSupplA;
		boolean nicSupplC;
		PositionDecoder posDec = new PositionDecoder();
	}
}
