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
 * Exception which is thrown when incompatible positions are used
 * for CPR decoding.
 * 
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class PositionStraddleError extends Exception {
	private static final long serialVersionUID = 6482688479919911669L;

	public PositionStraddleError(String reason) {
		super(reason);
	}
}
