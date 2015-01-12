package org.opensky.libadsb;

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
 * Some useful function when working with libadsb
 * @author Matthias Schäfer <schaefer@sero-systems.de>
 */
public class tools {
	/**
	 * Converts a byte into a hex string (e.g. 164=a4)
	 * @param b input byte
	 * @return hex representation of input byte
	 */
	public static String toHexString(byte b) {
		return String.format("%02x", (short)b & 0xFF);
	}
	
	
	/**
	 * Converts an array of bytes in a hex string
	 * @param bytes array of bytes
	 * @return concatenated hex representation of input byte array
	 */
	public static String toHexString(byte[] bytes) {
		StringBuilder result = new StringBuilder();
		for (byte b : bytes)
			result.append(toHexString(b));

		return result.toString();
	}
	
	/**
	 * Compares two byte arrays element by element
	 * @param array1 first array
	 * @param array2 second array
	 * @return array1 == array2
	 */
	public static boolean areEqual(byte[] array1, byte[] array2) {
		if (array1.length != array2.length) return false;
		
		for (int i=0; i<array1.length; ++i)
			if (array1[i] != array2[i]) return false;
		
		return true;
	}

	/**
	 * Checks whether a byte array just contains elements equal to zero
	 * @return true if all bytes of the array are 0
	 */
	public static boolean isZero(byte[] bytes) {
		int x = 0;
		for (int i = 0; i < bytes.length; i++) {
			x |= bytes[i];
		}
		return x == 0;
	}
}
