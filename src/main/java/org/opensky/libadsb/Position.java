package org.opensky.libadsb;

import java.io.Serializable;

import static java.lang.Math.*;

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
 * Container class for WGS84 positions
 *
 * @author Markus Fuchs (fuchs@opensky-network.org)
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class Position implements Serializable {
	private static final long serialVersionUID = 1562401753853965728L;

	// WGS84 ellipsoid constants
	private final static double a = 6378137.0; // semi-major axis
	private final static double f = 1/298.257223563; // flattening
	private final static double b = a*(1-f); // semi-minor axis
	private final static double e2 = 2*f-f*f; // eccentricity squared

	private Double longitude;
	private Double latitude;
	private Double altitude;
	private boolean reasonable;

	public Position() {
		longitude = null;
		latitude = null;
		altitude = null;
		setReasonable(true); // be optimistic :-)
	}

	/**
	 * @param lon longitude in decimal degrees
	 * @param lat latitude in decimal degrees
	 * @param alt altitude in feet
	 */
	public Position(Double lon, Double lat, Double alt) {
		longitude = lon;
		latitude = lat;
		altitude = alt;
		setReasonable(true);
	}

	/**
	 * @return longitude in decimal degrees
	 */
	public Double getLongitude() {
		return longitude;
	}

	/**
	 * @param longitude in decimal degrees
	 */
	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}

	/**
	 * @return latitude in decimal degrees
	 */
	public Double getLatitude() {
		return latitude;
	}

	/**
	 * @param latitude in decimal degrees
	 */
	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}

	/**
	 * @return altitude in feet
	 */
	public Double getAltitude() {
		return altitude;
	}

	/**
	 * @param altitude in feet
	 */
	public void setAltitude(Double altitude) {
		this.altitude = altitude;
	}

	/**
	 * Calculates the two-dimensional great circle distance (haversine)
	 * @param other position to which we calculate the distance
	 * @return distance between the this and other position in meters
	 */
	public Double haversine(Position other) {
		double lon0r = toRadians(this.longitude);
		double lat0r = toRadians(this.latitude);
		double lon1r = toRadians(other.longitude);
		double lat1r = toRadians(other.latitude);
		double a = pow(sin((lat1r - lat0r) / 2.0), 2);
		double b = cos(lat0r) * cos(lat1r) * pow(sin((lon1r - lon0r) / 2.0), 2);

		return 6371000.0 * 2 * asin(sqrt(a + b));
	}

	/**
	 * Converts the WGS84 position to cartesian coordinates
	 * @return earth-centered earth-fixed coordinates as [x, y, z]
	 */
	public double[] toECEF () {
		double lon0r = toRadians(this.longitude);
		double lat0r = toRadians(this.latitude);
		double height = tools.feet2Meters(altitude);

		double v = a / Math.sqrt(1 - e2*Math.sin(lat0r)*Math.sin(lat0r));

		return new double[] {
				(v + height) * Math.cos(lat0r) * Math.cos(lon0r), // x
				(v + height) * Math.cos(lat0r) * Math.sin(lon0r), // y
				(v * (1 - e2) + height) * Math.sin(lat0r) // z
		};
	}

	/**
	 * Converts a cartesian earth-centered earth-fixed coordinate into an WGS84 LLA position
	 * @param x coordinate in meters
	 * @param y coordinate in meters
	 * @param z coordinate in meters
	 * @return a position object representing the WGS84 position
	 */
	public static Position fromECEF (double x, double y, double z) {

		double p = sqrt(x*x + y*y);
		double th = atan2(a * z, b * p);
		double lon = atan2(y, x);
		double lat = atan2(
				(z + (a*a - b*b) / (b*b) * b * pow(sin(th), 3)),
				p - e2 * a * pow(cos(th), 3));
		double N = a / sqrt(1 - pow(sqrt(e2) * sin(lat), 2));
		double alt = p / cos(lat) - N;

		// correct for numerical instability in altitude near exact poles:
		// after this correction, error is about 2 millimeters, which is about
		// the same as the numerical precision of the overall function
		if (abs(x) < 1 & abs(y) < 1)
			alt = abs(z) - b;

		return new Position(toDegrees(lon), toDegrees(lat), tools.meters2Feet(alt));
	}

	/**
	 * Calculate the three-dimensional distance between this and another position.
	 * This method assumes that the coordinates are WGS84.
	 * @param other position
	 * @return 3d distance in meters or null if lat, lon, or alt is missing
	 */
	public Double distance3d(Position other) {
		if (other == null || latitude == null || longitude == null || altitude == null)
			return null;

		double[] xyz1 = this.toECEF();
		double[] xyz2 = other.toECEF();

		return Math.sqrt(
				Math.pow(xyz2[0] - xyz1[0], 2) +
						Math.pow(xyz2[1] - xyz1[1], 2) +
						Math.pow(xyz2[2] - xyz1[2], 2)
		);
	}

	/**
	 * This is used to mark positions as unreasonable if a
	 * plausibility check fails during decoding. Some transponders
	 * broadcast false positions and if detected, this flag is unset.
	 * Note that we assume positions to be reasonable by default.
	 * @return true if position has been flagged reasonable by the decoder
	 */
	public boolean isReasonable() {
		return reasonable;
	}

	/**
	 * Set/unset reasonable flag.
	 * @param reasonable false if position is considered unreasonable
	 */
	public void setReasonable(boolean reasonable) {
		this.reasonable = reasonable;
	}

	@Override
	public String toString() {
		return "Position{" +
				"longitude=" + longitude +
				", latitude=" + latitude +
				", altitude=" + altitude +
				", reasonable=" + reasonable +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Position)) return false;

		Position position = (Position) o;

		if (reasonable != position.reasonable) return false;
		if (longitude != null ? !longitude.equals(position.longitude) : position.longitude != null) return false;
		if (latitude != null ? !latitude.equals(position.latitude) : position.latitude != null) return false;
		return altitude != null ? altitude.equals(position.altitude) : position.altitude == null;
	}

	@Override
	public int hashCode() {
		int result = longitude != null ? longitude.hashCode() : 0;
		result = 31 * result + (latitude != null ? latitude.hashCode() : 0);
		result = 31 * result + (altitude != null ? altitude.hashCode() : 0);
		result = 31 * result + (reasonable ? 1 : 0);
		return result;
	}
}
