package org.opensky.libadsb.msgs;

import org.opensky.libadsb.exceptions.BadFormatException;

import java.io.Serializable;

/**
 * @author Markus Fuchs (fuchs@opensky-network.org)
 */
public class SurfacePositionV2Msg extends SurfacePositionV1Msg implements Serializable {

	private boolean nic_suppl_c;

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected SurfacePositionV2Msg() { }

	/**
	 * @param raw_message raw ADS-B surface position message as hex string
	 * @throws BadFormatException if message has wrong format
	 */
	public SurfacePositionV2Msg(String raw_message) throws BadFormatException {
		this(new ExtendedSquitter(raw_message));
	}

	/**
	 * @param raw_message raw ADS-B surface position message as byte array
	 * @throws BadFormatException if message has wrong format
	 */
	public SurfacePositionV2Msg(byte[] raw_message) throws BadFormatException {
		this(new ExtendedSquitter(raw_message));
	}

	/**
	 * @param squitter extended squitter which contains this surface position msg
	 * @throws BadFormatException if message has wrong format
	 */
	public SurfacePositionV2Msg(ExtendedSquitter squitter) throws BadFormatException {
		super(squitter);
		setType(subtype.ADSB_SURFACE_POSITION_V2);
	}

	/**
	 * @return NIC supplement that was set before
	 */
	public boolean hasNICSupplementC() {
		return nic_suppl_c;
	}

	/**
	 * @param nic_suppl Navigation Integrity Category (NIC) supplement C from operational status message.
	 *        It's from the surface capability class (CC) subfield of Operational Status Messages
	 */
	public void setNICSupplementC(boolean nic_suppl) {
		this.nic_suppl_c = nic_suppl;
	}

	/**
	 * The position error, i.e., 95% accuracy for the horizontal position. For the navigation accuracy category
	 * (NACp) see {@link AirborneOperationalStatusV2Msg}. Values according to DO-260B Table 2-14.
	 *
	 * The horizontal containment radius is also known as "horizontal protection level".
	 *
	 * @return horizontal containment radius limit in meters. A return value of -1 means "unknown".
	 *         If aircraft uses ADS-B version 1+, set NIC supplement A from Operational Status Message
	 *         for better precision. For version 2 set NIC supplement C from Surface Operational Status
	 *         Message for even better precision.
	 */
	@Override
	public double getHorizontalContainmentRadiusLimit() {
		switch (getFormatTypeCode()) {
			case 0: return -1;
			case 5: return 7.5;
			case 6: return 25;
			case 7:
				return (byte) (hasNICSupplementA() ? 75 : 185.2);
			case 8:
				if (hasNICSupplementC() && hasNICSupplementA())
					return 370.4;
				else if (hasNICSupplementC())
					return 1111.2;
				else if (hasNICSupplementA())
					return 555.6;
				return -1;
			default: return -1;
		}
	}

	/**
	 * Values according to DO-260B Table 2-14
	 *
	 * @return Navigation integrity category. A NIC of 0 means "unkown". If aircraft uses ADS-B version 1+, set
	 * NIC supplement A from Operational Status Message for better precision. For version 2 set NIC supplement C
	 * from Surface Operational Status Message for even better precision.
	 */
	@Override
	public byte getNIC() {
		switch (getFormatTypeCode()) {
			case 0: return 0;
			case 5: return 11;
			case 6: return 10;
			case 7:
				return (byte) (hasNICSupplementA() ? 9 : 8);
			case 8:
				if (hasNICSupplementC() && hasNICSupplementA())
					return 7;
				else if (hasNICSupplementC() || hasNICSupplementA())
					return 6;
				return 0;
			default: return 0;
		}
	}
}
