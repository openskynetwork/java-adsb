package org.opensky.libadsb.msgs;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.exceptions.UnspecifiedFormatError;

import java.io.Serializable;

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
 * @author Markus Fuchs (fuchs@opensky-network.org)
 */
public class SurfaceOperationalStatusV2Msg extends SurfaceOperationalStatusV1Msg implements Serializable {

	private boolean sil_supplement;

	/** protected no-arg constructor e.g. for serialization with Kryo **/
	protected SurfaceOperationalStatusV2Msg() { }

	/**
	 * @param raw_message The full Mode S message in hex representation
	 * @throws BadFormatException if message has the wrong typecode or ADS-B version
	 * @throws UnspecifiedFormatError if message has the wrong subtype
	 */
	public SurfaceOperationalStatusV2Msg(String raw_message) throws BadFormatException, UnspecifiedFormatError {
		this(new ExtendedSquitter(raw_message));
	}

	/**
	 * @param raw_message The full Mode S message as byte array
	 * @throws BadFormatException if message has the wrong typecode or ADS-B version
	 * @throws UnspecifiedFormatError if message has the wrong subtype
	 */
	public SurfaceOperationalStatusV2Msg(byte[] raw_message) throws BadFormatException, UnspecifiedFormatError {
		this(new ExtendedSquitter(raw_message));
	}

	/**
	 * @param squitter extended squitter which contains this message
	 * @throws BadFormatException  if message has the wrong typecode or ADS-B version
	 * @throws UnspecifiedFormatError if message has the wrong subtype
	 */
	public SurfaceOperationalStatusV2Msg(ExtendedSquitter squitter) throws BadFormatException, UnspecifiedFormatError {
		super(squitter);
		setType(subtype.ADSB_SURFACE_STATUS_V2);

		byte[] msg = this.getMessage();

		if ((byte) (msg[5]>>>5) != 2)
			throw new BadFormatException("Not a DO-260B/version 2 status message.");

		sil_supplement = ((msg[6] & 0x2) != 0);
	}

	/**
	 * DO-260B 2.2.3.2.7.2.14
	 * @return true if SIL (Source Integrity Level) is based on "per sample" probability, otherwise
	 * 			it's based on "per hour".
	 */
	public boolean getSILSupplement() {
		return sil_supplement;
	}

}
