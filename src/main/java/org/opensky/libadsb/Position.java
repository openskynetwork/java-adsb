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
 * @author Markus Fuchs <fuchs@sero-systems.de>
 * @author Matthias Schäfer <schaefer@sero-systems.de>
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

	public Double getLongitude() {
		return longitude;
	}

	/**
	 * @param longitude in decimal degrees
	 */
	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}

	public Double getLatitude() {
		return latitude;
	}

	/**
	 * @param latitude in decimal degrees
	 */
	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}

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
	 * Haversine two-dimensional distance
	 * @param other position to calculate distance to
	 * @return great circle distance between the two positions in meters
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
	 * @return true if position has been flagged reasonable by the decoder
	 */
	public boolean isReasonable() {
		return reasonable;
	}

	public void setReasonable(boolean reasonable) {
		this.reasonable = reasonable;
	}

}
