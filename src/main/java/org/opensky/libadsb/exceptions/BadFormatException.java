package org.opensky.libadsb.exceptions;

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
 * Exception which is thrown when someone calls a getter but the information
 * is actually not available. The programmer has to check the subtype codes
 * to avoid this exception.
 * 
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class BadFormatException extends Exception {
	private static final long serialVersionUID = 5630832543039853589L;

	private String msg;
	private String reason;
	
	public BadFormatException(String reason, String message) {
		super(reason);
		this.msg = message;
		this.reason = reason;
	}
	
	public BadFormatException(String reason) {
		super(reason);
		this.msg = "[unknown]"; // unknown
		this.reason = reason;
	}

	@Override
	public String getMessage() {
		return "Message '" + this.msg + "' has an illegal format: "
				+ this.reason;
	}
}
