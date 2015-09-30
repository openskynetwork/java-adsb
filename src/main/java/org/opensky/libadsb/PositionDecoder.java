package org.opensky.libadsb;

import static java.lang.Math.*;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.MissingInformationException;
import org.opensky.libadsb.exceptions.PositionStraddleError;
import org.opensky.libadsb.msgs.AirbornePositionMsg;
import org.opensky.libadsb.msgs.SurfacePositionMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @author Matthias Schäfer <schaefer@sero-systems.de>
 *
 */
public class PositionDecoder {
	private AirbornePositionMsg last_even_airborne;
	private double last_even_airborne_time;
	private AirbornePositionMsg last_odd_airborne;
	private double last_odd_airborne_time;
	private SurfacePositionMsg last_even_surface;
	private double last_even_surface_time;
	private SurfacePositionMsg last_odd_surface;
	private double last_odd_surface_time;
	private Position last_pos; // lat lon
	private double last_time;
	private boolean supplA;
	private boolean supplC;
	private int num_reasonable; // number of successive reasonable msgs
	private Logger logger;
	
	// distance to receiver threshold
	private static final int MAX_DIST_TO_SENDER = 700000; // 700km
	
	public PositionDecoder() {
		last_even_airborne = null;
		last_odd_airborne = null;
		last_even_surface = null;
		last_odd_surface = null;
		last_pos = null;
		supplA = false;
		supplC = false;
		num_reasonable = 0;
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
		return receiver.distanceTo(sender)<=MAX_DIST_TO_SENDER;
	}
	
	/**
	 * Note: positions must be ordered in time
	 * @param time time of applicability/reception of position report
	 * @param msg airborne position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable<br />
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 */
	public Position decodePosition(double time, AirbornePositionMsg msg) {
		boolean local = false, global = false;
		
		if (!msg.hasPosition())
			return null;
		
		if (time < last_time) {
			logger.warn("Position messages should be ordered!");
			return null; // positions should be ordered
		}
		
		// decide whether to use global or local position decoding
		if (last_pos != null && abs(time-last_time) < 640) { // 640 seconds corresponds to 180NM with 1000 knots
			local = true;
		}
		
		// can I do global decoding?
		AirbornePositionMsg last_other = msg.isOddFormat() ? last_even_airborne : last_odd_airborne;
		double last_other_time = msg.isOddFormat() ? last_even_airborne_time : last_odd_airborne_time;
		if (last_other != null && abs(time-last_other_time) < 10) { // less than 10 seconds; see 1090 MOPS
			global = true;
		}
		
		// if I can do both, use them for validation
		Position global_pos = null;
		if (global) { // do global CPR
			try {
				global_pos = msg.getGlobalPosition(last_other);
			} catch (BadFormatException e) {
				logger.warn("Cannot do global CPR due to bad format (icao24: %s).\n",
						tools.toHexString(msg.getIcao24()));
				global = false;
			} catch (PositionStraddleError e) {
				logger.debug("Position staddle (icao24: %s).\n",
						tools.toHexString(msg.getIcao24()));
				global = false;
			} catch (MissingInformationException e) {
				logger.debug("Missing information (global, icao24: %s).\n",
						tools.toHexString(msg.getIcao24()));
				global = false;
			}
		}
		
		Position local_pos = null;
		if (local) { // do local CPR
			try { local_pos = msg.getLocalPosition(last_pos); }
			catch (MissingInformationException e) {
				logger.debug("Missing information (local, icao24: %s).\n",
						tools.toHexString(msg.getIcao24()));
				local = false;
			}
		}

		//////// Reasonableness Test //////////
		
		boolean reasonable = true; // be positive :-)
		double distance_threshold = 10.0; // 10 is a random small distance
		
		// check distance between global and local position if possible
		if (local && global && global_pos.distanceTo(local_pos) > distance_threshold) {  // should be almost equal
			logger.debug("Local and global differ by %.2f (icao24: %s)\n",
					global_pos.distanceTo(local_pos), tools.toHexString(msg.getIcao24()));
			reasonable = false;
		}

		// check if it's realistic that the airplane covered this distance
		if (global && last_pos != null &&
				!withinThreshold(time-last_time, global_pos.distanceTo(last_pos))) { // faster than 1000 knots???
			logger.debug("'%s' would have been too fast (%.2f m/s) from other position.\n",
					tools.toHexString(msg.getIcao24()), global_pos.distanceTo(last_pos)/abs(time-last_time));
			reasonable = false;
		}
		
		// use local CPR to verify even and odd position
		if (global) {
			try {
				// check local/global dist of new message
				double dist = global_pos.distanceTo(msg.getLocalPosition(global_pos));
				if (dist > distance_threshold) {
					reasonable = false;
					logger.debug("Local/Global differ for new message by %.2f m (icao24: %s)\n",
							dist, tools.toHexString(msg.getIcao24()));
				}
				
				// check local/global dist of old message
				Position other_pos = last_other.getGlobalPosition(msg);
				dist = other_pos.distanceTo(last_other.getLocalPosition(other_pos));
				if (dist > distance_threshold) {
					reasonable = false;
					logger.debug("Local/Global differ for old message by %.2f m (icao24: %s)\n",
							dist, tools.toHexString(msg.getIcao24()));
				}
				
				if (!withinThreshold(time-last_other_time, global_pos.distanceTo(other_pos))) { // faster than 1000 knots
					reasonable = false;
					logger.debug("'%s' would have been too fast (%.2f m/s) for global.\n",
							tools.toHexString(msg.getIcao24()), global_pos.distanceTo(other_pos)/abs(last_other_time-time));
				}
			} catch (MissingInformationException e) {
				reasonable = false;
			} catch (BadFormatException e) {
				reasonable = false;
			} catch (PositionStraddleError e) {
				reasonable = false;
			}
		}

		// check if it's realistic that the airplane covered this distance
		if (local_pos != null && last_pos != null &&
				!withinThreshold(last_time-time, local_pos.distanceTo(last_pos))) { // faster than 1000 knots???
			logger.debug("'%s' would be too fast (%.2f/%.2f = %.2f m/s).\n",
					new Object[] {
					tools.toHexString(msg.getIcao24()),
					local_pos.distanceTo(last_pos), abs(time-last_time),
					local_pos.distanceTo(last_pos)/abs(time-last_time)
			});
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
			if (Math.abs(ret.getLongitude()) > 90.0 ||
					ret.getLatitude() < 0.0 || ret.getLatitude() > 180.0) {
				reasonable = false;
			}
			ret.setReasonable(reasonable);
		}
		last_pos = ret;
		last_time = time;
		
		if (!reasonable)
			num_reasonable = 0;
		else if (reasonable && num_reasonable++<2) // at least n good msgs before
			ret = null;

		return ret;
	}
	
	/**
	 * Note: positions must be ordered in time
	 * @param time time of applicability/reception of position report
	 * @param receiver position of the receiver to check if received position was more than 600km away
	 * @param msg airborne position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable<br />
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 * @return
	 */
	public Position decodePosition(double time, Position receiver, AirbornePositionMsg msg) {
		Position ret = decodePosition(time, msg);
		if (ret != null && receiver != null) {
			ret.setReasonable(ret.isReasonable() && withinReasonableRange(receiver, ret));
			num_reasonable = 0;
		}
		return ret;
	}
	
	/**
	 * @param receiver position of the receiver to be included in the reasonableness test
	 * @param msg airborne position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable<br />
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 * @return
	 */
	public Position decodePosition(Position receiver, AirbornePositionMsg msg) {
		return decodePosition(System.currentTimeMillis()/1000.0, receiver, msg);
	}
	
	/**
	 * Note: use this method for live decoding only! Assumes that time of applicability
	 * equals current time! Using this function for older messages might result in false
	 * positions! Prefer using decodePosition with proper time of applicability.
	 * @param msg airborne position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable<br />
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 */
	public Position decodePosition(AirbornePositionMsg msg) {
		return decodePosition(System.currentTimeMillis()/1000.0, msg);
	}

	/**
	 * Note: positions must be ordered in time
	 * @param time time of applicability/reception of position report
	 * @param msg surface position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable<br />
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 */
	public Position decodePosition(double time, SurfacePositionMsg msg) {
		boolean local = false, global = false;
		
		if (!msg.hasPosition())
			return null;
		
		if (time <= last_time) {
			logger.warn("Position messages should be ordered!");
			return null; // positions should be ordered
		}
		
		// decide whether to use global or local position decoding
		if (last_pos != null && abs(time-last_time) < 1620) { // 45NM with 100 knots; see 1090 MOPS
			local = true;
		}
		
		// can I do global decoding?
		SurfacePositionMsg last_other = msg.isOddFormat() ? last_even_surface : last_odd_surface;
		double last_other_time = msg.isOddFormat() ? last_even_surface_time : last_odd_surface_time;
		if (last_other != null && abs(time-last_other_time) < 25) { // less than 25 seconds; see 1090 MOPS
			global = true;
		}
		
		// if I can do both, use them for validation
		Position global_pos = null;
		if (global) { // do global CPR
			try {
				global_pos = msg.getGlobalPosition(last_other);
			} catch (BadFormatException e) {
				logger.warn("Cannot do global CPR due to bad format (icao24: %s).\n",
						tools.toHexString(msg.getIcao24()));
				global = false;
			} catch (PositionStraddleError e) {
				logger.debug("Position staddle (icao24: %s).\n",
						tools.toHexString(msg.getIcao24()));
				global = false;
			} catch (MissingInformationException e) {
				logger.debug("Missing information (global, icao24: %s).\n",
						tools.toHexString(msg.getIcao24()));
				global = false;
			}
		}
		
		Position local_pos = null;
		if (local) { // do local CPR
			try { local_pos = msg.getLocalPosition(last_pos); }
			catch (MissingInformationException e) {
				logger.debug("Missing information (local, icao24: %s).\n",
						tools.toHexString(msg.getIcao24()));
				local = false;
			}
		}

		//////// Reasonableness Test //////////
		
		boolean reasonable = true; // be positive :-)
		double distance_threshold = 10.0; // 10 is a random small distance
		
		// check distance between global and local position if possible
		if (local && global && global_pos.distanceTo(local_pos) > distance_threshold) {  // should be almost equal
			logger.debug("Local and global differ by %.2f (icao24: %s)\n",
					global_pos.distanceTo(local_pos), tools.toHexString(msg.getIcao24()));
			reasonable = false;
		}

		// check if it's realistic that the airplane covered this distance
		if (global && last_pos != null &&
				!withinThreshold(time-last_time, global_pos.distanceTo(last_pos), true)) { // faster than 1000 knots???
			logger.debug("'%s' would have been too fast (%.2f m/s) from other position.\n",
					tools.toHexString(msg.getIcao24()), global_pos.distanceTo(last_pos)/abs(time-last_time));
			reasonable = false;
		}
		
		// use local CPR to verify even and odd position
		if (global) {
			try {
				// check local/global dist of new message
				double dist = global_pos.distanceTo(msg.getLocalPosition(global_pos));
				if (dist > distance_threshold) {
					reasonable = false;
					logger.debug("Local/Global differ for new message by %.2f m (icao24: %s)\n",
							dist, tools.toHexString(msg.getIcao24()));
				}
				
				// check local/global dist of old message
				Position other_pos = last_other.getGlobalPosition(msg);
				dist = other_pos.distanceTo(last_other.getLocalPosition(other_pos));
				if (dist > distance_threshold) {
					reasonable = false;
					logger.debug("Local/Global differ for old message by %.2f m (icao24: %s)\n",
							dist, tools.toHexString(msg.getIcao24()));
				}
				
				if (!withinThreshold(time-last_other_time, global_pos.distanceTo(other_pos), true)) { // faster than 1000 knots
					reasonable = false;
					logger.debug("'%s' would have been too fast (%.2f m/s) for global.\n",
							tools.toHexString(msg.getIcao24()), global_pos.distanceTo(other_pos)/abs(last_other_time-time));
				}
			} catch (MissingInformationException e) {
				reasonable = false;
			} catch (BadFormatException e) {
				reasonable = false;
			} catch (PositionStraddleError e) {
				reasonable = false;
			}
		}

		// check if it's realistic that the airplane covered this distance
		if (local_pos != null && last_pos != null &&
				!withinThreshold(last_time-time, local_pos.distanceTo(last_pos), true)) { // faster than 1000 knots???
			logger.debug("'%s' would be too fast (%.2f/%.2f = %.2f m/s).\n",
					new Object[] {
					tools.toHexString(msg.getIcao24()),
					local_pos.distanceTo(last_pos), abs(time-last_time),
					local_pos.distanceTo(last_pos)/abs(time-last_time)
			});
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
			if (Math.abs(ret.getLongitude()) > 90.0 ||
					ret.getLatitude() < 0.0 || ret.getLatitude() > 180.0) {
				reasonable = false;
			}
			ret.setReasonable(reasonable);
		}

		last_pos = ret;
		last_time = time;
		
		if (!reasonable)
			num_reasonable = 0;
		else if (reasonable && num_reasonable++<2) // at least n good msgs before
			ret = null;

		return ret;
	}
	
	/**
	 * Note: positions must be ordered in time
	 * @param time time of applicability/reception of position report
	 * @param receiver position of the receiver to check if received position was more than 600km away
	 * @param msg surface position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable<br />
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 * @return
	 */
	public Position decodePosition(double time, Position receiver, SurfacePositionMsg msg) {
		Position ret = decodePosition(time, msg);
		if (ret != null && receiver != null) {
			ret.setReasonable(ret.isReasonable() && withinReasonableRange(receiver, ret));
			num_reasonable = 0;
		}
		return ret;
	}
	
	/**
	 * @param receiver position of the receiver to be included in the reasonableness test
	 * @param msg airborne position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable<br />
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 * @return
	 */
	public Position decodePosition(Position receiver, SurfacePositionMsg msg) {
		return decodePosition(System.currentTimeMillis()/1000.0, receiver, msg);
	}
	
	/**
	 * Note: use this method for live decoding only! Assumes that time of applicability
	 * equals current time! Using this function for older messages might result in false
	 * positions! Prefer using decodePosition with proper time of applicability.
	 * @param msg airborne position message
	 * @return WGS84 coordinates with latitude and longitude in dec degrees, and altitude in meters. altitude might be null if unavailable<br />
	 *         On error, the returned position is null. Check the .isReasonable() flag before using the position.
	 */
	public Position decodePosition(SurfacePositionMsg msg) {
		return decodePosition(System.currentTimeMillis()/1000.0, msg);
	}

	/**
	 * NIC Supplement A from operational status messages
	 * @return NIC Supplement A from operational status messages
	 */
	public boolean getNICSupplementA() {
		return supplA;
	}

	/**
	 * NIC Supplement A from operational status messages
	 * @param supplA
	 */
	public void setNICSupplementA(boolean supplA) {
		this.supplA = supplA;
	}
	
	/**
	 * NIC Supplement C from operational status messages (subtype 1 only)
	 * @return NIC Supplement C from operational status messages (subtype 1 only)
	 */
	public boolean getNICSupplementC() {
		return supplC;
	}

	/**
	 * NIC Supplement C from operational status messages (subtype 1 only)
	 * @param supplC
	 */
	public void setNICSupplementC(boolean supplC) {
		this.supplC = supplC;
	}
	
	/**
	 * @return the time when the last position was decoded
	 */
	public double getLastUsedTime() {
		return last_time;
	}

}
