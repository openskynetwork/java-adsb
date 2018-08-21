package org.opensky.libadsb.msgs;

import org.opensky.libadsb.exceptions.BadFormatException;

import java.io.Serializable;

/**
 * @author Markus Fuchs (fuchs@opensky-network.org)
 */
public class SurfacePositionV1Msg extends SurfacePositionV0Msg implements Serializable {

	private boolean nic_suppl_a;

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected SurfacePositionV1Msg() { }

	/**
	 * @param raw_message raw ADS-B surface position message as hex string
	 * @throws BadFormatException if message has wrong format
	 */
	public SurfacePositionV1Msg(String raw_message) throws BadFormatException {
		this(new ExtendedSquitter(raw_message));
	}

	/**
	 * @param raw_message raw ADS-B surface position message as byte array
	 * @throws BadFormatException if message has wrong format
	 */
	public SurfacePositionV1Msg(byte[] raw_message) throws BadFormatException {
		this(new ExtendedSquitter(raw_message));
	}

	/**
	 * @param squitter extended squitter which contains this surface position msg
	 * @throws BadFormatException if message has wrong format
	 */
	public SurfacePositionV1Msg(ExtendedSquitter squitter) throws BadFormatException {
		super(squitter);
		setType(subtype.ADSB_SURFACE_POSITION_V1);
	}

	/**
	 * @return NIC supplement that was set before
	 */
	public boolean hasNICSupplementA() {
		return nic_suppl_a;
	}

	/**
	 * @param nic_suppl Navigation Integrity Category (NIC) supplement from operational status message.
	 *        Otherwise worst case is assumed for containment radius limit and NIC.
	 */
	public void setNICSupplementA(boolean nic_suppl) {
		this.nic_suppl_a = nic_suppl;
	}

	/**
	 * The position error, i.e., 95% accuracy for the horizontal position. For the navigation accuracy category
	 * (NACp) see {@link AirborneOperationalStatusV1Msg}. Values according to DO-260B Table N-11.
	 *
	 * The horizontal containment radius is also known as "horizontal protection level".
	 *
	 * @return horizontal containment radius limit in meters. A return value of -1 means "unknown".
	 *         If aircraft uses ADS-B version 1+, set NIC supplement A from Operational Status Message
	 *         for better precision.
	 */
	@Override
	public double getHorizontalContainmentRadiusLimit() {
		switch (getFormatTypeCode()) {
			case 0: return -1;
			case 5: return 7.5;
			case 6: return 25;
			case 7:
				return hasNICSupplementA() ? 75 : 185.2;
			case 8:
				return 185.2;
			default: return -1;
		}
	}

	/**
	 * Values according to DO-260B Table N-11
	 * @return Navigation integrity category. A NIC of 0 means "unkown". If aircraft uses ADS-B version 1+,
	 * set NIC supplement A from Operational Status Message for better precision.
	 */
	@Override
	public byte getNIC() {
		switch (getFormatTypeCode()) {
			case 0: case 8: return 0;
			case 5: return 11;
			case 6: return 10;
			case 7:
				return (byte) (hasNICSupplementA() ? 9 : 8);
			default: return 0;
		}
	}

}
