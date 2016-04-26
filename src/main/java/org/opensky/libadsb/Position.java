package org.opensky.libadsb;

import static java.lang.Math.asin;
import static java.lang.Math.cos;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toRadians;

import java.io.Serializable;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;

/**
 * Container class for WGS84 positions
 * 
 * @author Markus Fuchs (fuchs@sero-systems.de)
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class Position implements Serializable {
	private static final long serialVersionUID = 1562401753853965728L;
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
	 * @param alt altitude in meters
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
	 * @return altitude in meters
	 */
	public Double getAltitude() {
		return altitude;
	}

	/**
	 * @param altitude in meters
	 */
	public void setAltitude(Double altitude) {
		this.altitude = altitude;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null) return false;
		if (o instanceof Position) {
			final Position p = (Position) o;
			return new EqualsBuilder().append(getLatitude(), 
					p.getLatitude())
					.append(getLongitude(), p.getLongitude())
					.append(getAltitude(), p.getAltitude()).isEquals();
		}
		return false;
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(longitude)
				.append(latitude).append(altitude).toHashCode();
	}
	
	/**
	 * Calculates the two-dimensional great circle distance (haversine)
	 * @param other position to which we calculate the distance
	 * @return distance between the this and other position in meters
	 */
	public Double distanceTo(Position other) {
		double lon0r = toRadians(this.longitude);
		double lat0r = toRadians(this.latitude);
		double lon1r = toRadians(other.longitude);
		double lat1r = toRadians(other.latitude);
		double a = pow(sin((lat1r - lat0r) / 2.0), 2);
		double b = cos(lat0r) * cos(lat1r) * pow(sin((lon1r - lon0r) / 2.0), 2);

		return 6371000.0 * 2 * asin(sqrt(a + b));
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

}
