package org.opensky.libadsb;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.PositionStraddleError;
import org.opensky.libadsb.msgs.AirbornePositionV0Msg;
import org.opensky.libadsb.msgs.SurfacePositionV0Msg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Math.abs;

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
 * This class is a stateful position decoder for one airplane.
 * For each flight that is being tracked, create an instance of
 * this class for a proper position decoding. The reasonableness
 * test implemented by the decodePosition function tries to detect
 * encoding errors and transponder misbehavior.
 * 
 * Please refer to "Analysis of anomalies in ADS-B and its GPS data"
 * by Busyairah et al.
 * 
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 *
 */
public class PositionDecoder {
	private AirbornePositionV0Msg last_even_airborne;
	private double last_even_airborne_time;
	private AirbornePositionV0Msg last_odd_airborne;
	private double last_odd_airborne_time;
	private SurfacePositionV0Msg last_even_surface;
	private double last_even_surface_time;
	private SurfacePositionV0Msg last_odd_surface;
	private double last_odd_surface_time;
	private Position last_pos; // lat lon
	private double last_time;
	private int num_reasonable; // number of successive reasonable msgs
	private Logger logger;

	// distance to receiver threshold
	private static final int MAX_DIST_TO_SENDER = 700000; // 700km

	public PositionDecoder() {
		logger = LoggerFactory.getLogger(this.getClass());
	}

	/**
	 * This function is used to induce some tolerance if messages
	 * were received shortly after (due to Internet jitter in timestmaps).
	 * This function is derived empirically
	 * @param timeDifference the time between the reception of both messages
	 * @param distance covered
	 * @param surface is the airplane e.g. taxiing?
	 * @return whether distance is realistic
	 */
	public static boolean withinThreshold (double timeDifference, double distance, boolean surface) {
		double x = abs(timeDifference);
		double d = abs(distance);

//		if (d/x >= (surface?51.44:514.4)*2.5)
//			System.err.format("%.2f/%.2f=%.2f\n", d, x, d/x);

		// may be due to Internet jitter; distance is realistic
		if (x < 0.7 && d < 2000) return true;
		else return d/x < (surface?51.44:514.4)*2.5; // 1000 knots for airborne, 100 for surface
	}

	private static boolean withinThreshold (double timeDifference, double distance) {
		return withinThreshold(timeDifference, distance, false);
	}

	/**
	 * This function can be used to check whether distance between sender and
	 * receiver is reasonable. Sometimes transponders report erroneous positions
	 * and this simply constitutes an additional reasonableness test. The maximum
	 * distance is an upper bound on the line of sight between airplanes and
	 * ground receivers.
	 * @param receiver the position of the receiver
	 * @param sender the reported position
	 * @return true is the distance between the two positions is reasonable
	 */
	public static boolean withinReasonableRange(Position receiver, Position sender) {
		return receiver.haversine(sender)<=MAX_DIST_TO_SENDER;
	}

	/**
	 * @param time time of applicability/reception of position report
	 * @param msg airborne position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in feet. altitude might be null
	 *         if unavailable. On error, the returned position is null. Check the .isReasonable() flag before using
	 *         the position.
	 */
	public Position decodePosition(double time, AirbornePositionV0Msg msg) {
		boolean local = false, global = false;

		if (!msg.hasPosition())
			return null;

		if (time < last_time) {
			logger.debug("Position messages should be ordered!");
		}

		// decide whether to use global or local position decoding
		if (last_pos != null && abs(time-last_time) < 640.0) { // 640 seconds corresponds to 180NM with 1000 knots
			local = true;
		}

		// can I do global decoding?
		AirbornePositionV0Msg last_other = msg.isOddFormat() ? last_even_airborne : last_odd_airborne;
		double last_other_time = msg.isOddFormat() ? last_even_airborne_time : last_odd_airborne_time;
		if (last_other != null && abs(time-last_other_time) < 10.0) { // less than 10 seconds; see 1090 MOPS
			global = true;
		}

		// if I can do both, use them for validation
		Position global_pos = null;
		if (global) { // do global CPR
			try {
				global_pos = msg.getGlobalPosition(last_other);
			} catch (BadFormatException e) {
				logger.debug("Cannot do global CPR due to bad format (icao24: {}).",
						tools.toHexString(msg.getIcao24()));
				global = false;
			} catch (PositionStraddleError e) {
				logger.debug("Position staddle (icao24: {}).",
						tools.toHexString(msg.getIcao24()));
				global = false;
			}
			if (global_pos == null) global = false;
		}

		Position local_pos = null;
		if (local) { // do local CPR
			local_pos = msg.getLocalPosition(last_pos);
			if (local_pos == null) local = false;
		}

		//////// Reasonableness Test //////////

		boolean reasonable = true; // be positive :-)
		double distance_threshold = 10.0; // 10 is a random small distance

		// check distance between global and local position if possible
		if (local && global && global_pos.haversine(local_pos) > distance_threshold) {  // should be almost equal
			logger.debug("Local and global differ by {} (icao24: {})",
					global_pos.haversine(local_pos), tools.toHexString(msg.getIcao24()));
			reasonable = false;
		}

		// check if it's realistic that the airplane covered this distance
		if (global && last_pos != null &&
				!withinThreshold(time-last_time, global_pos.haversine(last_pos))) { // faster than 1000 knots???
			logger.debug("'{}' would have been too fast ({} m/s) from other position.",
					tools.toHexString(msg.getIcao24()), global_pos.haversine(last_pos)/abs(time-last_time));
			reasonable = false;
		}

		// use local CPR to verify even and odd position
		if (global) {
			try {
				// check local/global dist of new message
				double dist = global_pos.haversine(msg.getLocalPosition(global_pos));
				if (dist > distance_threshold) {
					reasonable = false;
					logger.debug("Local/Global differ for new message by {} m (icao24: {})",
							dist, tools.toHexString(msg.getIcao24()));
				}

				// check local/global dist of old message
				Position other_pos = last_other.getGlobalPosition(msg);
				dist = other_pos.haversine(last_other.getLocalPosition(other_pos));
				if (dist > distance_threshold) {
					reasonable = false;
					logger.debug("Local/Global differ for old message by {} m (icao24: {})",
							dist, tools.toHexString(msg.getIcao24()));
				}

				if (!withinThreshold(time-last_other_time, global_pos.haversine(other_pos))) { // faster than 1000 knots
					reasonable = false;
					logger.debug("'{}' would have been too fast ({} m/s) for global.",
							tools.toHexString(msg.getIcao24()), global_pos.haversine(other_pos)/abs(last_other_time-time));
				}
			} catch (BadFormatException e) {
				reasonable = false;
			} catch (PositionStraddleError e) {
				reasonable = false;
			}
		}

		// check if it's realistic that the airplane covered this distance
		if (local_pos != null && last_pos != null &&
				!withinThreshold(last_time-time, local_pos.haversine(last_pos))) { // faster than 1000 knots???
			logger.debug("'{}' would be too fast ({}/{} = {} m/s).",
					tools.toHexString(msg.getIcao24()),
					local_pos.haversine(last_pos), abs(time-last_time),
					local_pos.haversine(last_pos)/abs(time-last_time));
			reasonable = false;
		}

		// store latest position message
		if (msg.isOddFormat() && msg.hasPosition()) {
			last_odd_airborne = msg;
			last_odd_airborne_time = time;
		}
		else if (!msg.isOddFormat() && msg.hasPosition()) {
			last_even_airborne = msg;
			last_even_airborne_time = time;
		}

		Position ret = global ? global_pos : local_pos;

		if (ret != null) {
			// is it a valid coordinate?
			if (Math.abs(ret.getLongitude()) > 180.0 || Math.abs(ret.getLatitude()) > 90.0) {
				reasonable = false;
			}
			ret.setReasonable(reasonable);
		}
		last_pos = ret;
		last_time = time;

		if (!reasonable)
			num_reasonable = 0;
		else if (num_reasonable++<2) // at least n good msgs before
			ret = null;

		return ret;
	}

	/**
	 * @param time time of applicability/reception of position report (seconds)
	 * @param receiver position of the receiver to check if received position was more than 600km away
	 * @param msg airborne position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 */
	public Position decodePosition(double time, Position receiver, AirbornePositionV0Msg msg) {
		Position ret = decodePosition(time, msg);
		if (ret != null && receiver != null && !withinReasonableRange(receiver, ret)) {
			ret.setReasonable(false);
			num_reasonable = 0;
		}
		return ret;
	}

	/**
	 * @param receiver position of the receiver to be included in the reasonableness test
	 * @param msg airborne position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 */
	public Position decodePosition(Position receiver, AirbornePositionV0Msg msg) {
		return decodePosition(System.currentTimeMillis()/1000.0, receiver, msg);
	}

	/**
	 * Use this method for live decoding only! It assumes that time of applicability
	 * equals current time! Using this function for older messages might result in
	 * false positions! Prefer using one of the other decodePosition methods with explicit
	 * handling of reception times. We assume in general that positions are passed to this
	 * decoder ordered in time, i.e. as they are received.
	 * @param msg airborne position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 */
	public Position decodePosition(AirbornePositionV0Msg msg) {
		return decodePosition(System.currentTimeMillis()/1000.0, msg);
	}
	
	/**
	 * @param time time of applicability/reception of position report (seconds)
	 * @param msg surface position message
	 * @param ref reference position to resolve ambiguous surface position; set to null if no reference known; in the latter case, the decoder
	 *        uses the last known position of the aircraft.
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 */
	public Position decodePosition(double time, SurfacePositionV0Msg msg, Position ref) {
		boolean local = false, global = false;
		
		if (!msg.hasPosition() || last_pos == null && ref == null)
			return null;
		
		Position real_ref = last_pos != null ? last_pos : ref;
		
		if (time <= last_time) {
			logger.debug("Position messages should be ordered!");
		}

		// decide whether to use global or local position decoding
		if (last_pos != null && abs(time-last_time) < 1620.0) { // 45NM with 100 knots; see 1090 MOPS
			local = true;
		}

		// can I do global decoding?
		SurfacePositionV0Msg last_other = msg.isOddFormat() ? last_even_surface : last_odd_surface;
		double last_other_time = msg.isOddFormat() ? last_even_surface_time : last_odd_surface_time;
		if (last_other != null) {
			if (last_other.hasGroundSpeed() && last_other.getGroundSpeed() <= 25.0 && msg.hasGroundSpeed()
					&& msg.getGroundSpeed() <= 25.0 && abs(time - last_other_time) <= 50.0) { // <= 50 seconds and speeds <= 25 knots; see 1090 MOPS
				global = true;

			} else if (abs(time - last_other_time) <= 25.0) { // <= 25 seconds; see 1090 MOPS
				global = true;
			}
		}

		// if I can do both, use them for validation
		Position global_pos = null;
		if (global) { // do global CPR
			try {
				global_pos = msg.getGlobalPosition(last_other, real_ref);
			} catch (BadFormatException e) {
				logger.debug("Cannot do global CPR due to bad format (icao24: {}).",
						tools.toHexString(msg.getIcao24()));
				global = false;
			} catch (PositionStraddleError e) {
				logger.debug("Position staddle (icao24: {}).",
						tools.toHexString(msg.getIcao24()));
				global = false;
			}
			if (global_pos == null) global = false;
		}

		Position local_pos = null;
		if (local) { // do local CPR
			local_pos = msg.getLocalPosition(last_pos);
			if (local_pos == null) local = false;
		}

		//////// Reasonableness Test //////////

		boolean reasonable = true; // be positive :-)
		double distance_threshold = 10.0; // 10 is a random small distance

		// check distance between global and local position if possible
		if (local && global && global_pos.haversine(local_pos) > distance_threshold) {  // should be almost equal
			logger.debug("Local and global differ by {} (icao24: {})\n",
					global_pos.haversine(local_pos), tools.toHexString(msg.getIcao24()));
			reasonable = false;
		}

		// check if it's realistic that the airplane covered this distance
		if (global && last_pos != null &&
				!withinThreshold(time-last_time, global_pos.haversine(last_pos), true)) { // faster than 1000 knots???
			logger.debug("'{}' would have been too fast ({} m/s) from other position.\n",
					tools.toHexString(msg.getIcao24()), global_pos.haversine(last_pos)/abs(time-last_time));
			reasonable = false;
		}

		// use local CPR to verify even and odd position
		if (global) {
			try {
				// check local/global dist of new message
				double dist = global_pos.haversine(msg.getLocalPosition(global_pos));
				if (dist > distance_threshold) {
					reasonable = false;
					logger.debug("Local/Global differ for new message by {} m (icao24: {})",
							dist, tools.toHexString(msg.getIcao24()));
				}

				// check local/global dist of old message
				Position other_pos = last_other.getGlobalPosition(msg, real_ref);
				dist = other_pos.haversine(last_other.getLocalPosition(other_pos));
				if (dist > distance_threshold) {
					reasonable = false;
					logger.debug("Local/Global differ for old message by {} m (icao24: {})",
							dist, tools.toHexString(msg.getIcao24()));
				}

				if (!withinThreshold(time-last_other_time, global_pos.haversine(other_pos), true)) { // faster than 1000 knots
					reasonable = false;
					logger.debug("'{}' would have been too fast ({} m/s) for global.",
							tools.toHexString(msg.getIcao24()), global_pos.haversine(other_pos)/abs(last_other_time-time));
				}
			} catch (BadFormatException e) {
				reasonable = false;
			} catch (PositionStraddleError e) {
				reasonable = false;
			}
		}

		// check if it's realistic that the airplane covered this distance
		if (local_pos != null && last_pos != null &&
				!withinThreshold(last_time-time, local_pos.haversine(last_pos), true)) { // faster than 1000 knots???
			logger.debug("'{}' would be too fast ({}/{} = {} m/s).",
					tools.toHexString(msg.getIcao24()),
					local_pos.haversine(last_pos), abs(time-last_time),
					local_pos.haversine(last_pos)/abs(time-last_time));
			reasonable = false;
		}

		// store latest position message
		if (msg.isOddFormat() && msg.hasPosition()) {
			last_odd_surface = msg;
			last_odd_surface_time = time;
		}
		else if (!msg.isOddFormat() && msg.hasPosition()) {
			last_even_surface = msg;
			last_even_surface_time = time;
		}

		Position ret = global ? global_pos : local_pos;

		if (ret != null) {
			// is it a valid coordinate?
			if (Math.abs(ret.getLongitude()) > 180.0 || Math.abs(ret.getLatitude()) > 90.0) {
				reasonable = false;
			}
			ret.setReasonable(reasonable);
		}

		last_pos = ret;
		last_time = time;

		if (!reasonable)
			num_reasonable = 0;
		else if (num_reasonable++ < 2) // at least n good msgs before
			ret = null;

		return ret;
	}

	/**
	 * Shortcut for using the last known position for reference; no reasonableness check on distance to receiver
	 * @param time time of applicability/reception of position report (seconds)
	 * @param msg surface position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 */
	public Position decodePosition(double time, SurfacePositionV0Msg msg) {
		if (last_pos == null)
			return null;

		return decodePosition(time, msg, last_pos);
	}

	/**
	 * Shortcut for live decoding; no reasonableness check on distance to receiver
	 * @param reference used for reasonableness test and to decide which of the four resulting positions of the CPR algorithm is the right one
	 * @param msg airborne position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 */
	public Position decodePosition(SurfacePositionV0Msg msg, Position reference) {
		return decodePosition(System.currentTimeMillis()/1000.0, msg, reference);
	}

	/**
	 * Performs all reasonableness tests.
	 * @param time time of applicability/reception of position report (seconds)
	 * @param reference position used to decide which position of the four results of the CPR algorithm is the right one
	 * @param receiver position to check if received position was more than 700km away and 
	 * @param msg surface position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 */
	public Position decodePosition(double time, Position receiver, SurfacePositionV0Msg msg, Position reference) {
		Position ret = decodePosition(time, msg, reference);
		if (ret != null && receiver != null && !withinReasonableRange(receiver, ret)) {
			ret.setReasonable(false);
			num_reasonable = 0;
		}
		return ret;
	}

	/**
	 * Returns time when the last position was decoded
	 * @return the time in seconds
	 */
	public double getLastUsedTime() {
		return last_time;
	}

}
