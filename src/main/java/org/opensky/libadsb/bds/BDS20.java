package org.opensky.libadsb.bds;

import org.opensky.libadsb.exceptions.BadFormatException;

/**
 * Parser for BDS 2,0 registers (aircraft identification)
 * <br><br>
 * Note: most of the code was copied from the ADS-B decoders!
 */
public class BDS20 extends BinaryDataStore {

    private byte[] identity;

    BDS20(byte[] payload, boolean check_selector) throws BadFormatException {
        super(payload);

        setBDSSelector((byte) 0x20);

        if (check_selector && payload[0] != 0x20)
            throw new BadFormatException(String.format("Wrong BDS code (%02x).", payload[0]));

        // extract identity
        identity = new byte[8];
        int byte_off, bit_off;
        for (int i=8; i>=1; i--) {
            // calculate offsets
            byte_off = (i*6)/8; bit_off = (i*6)%8;

            // char aligned with byte?
            if (bit_off == 0) identity[i-1] = (byte) (payload[byte_off]&0x3F);
            else {
                ++byte_off;
                identity[i-1] = (byte) (payload[byte_off]>>>(8-bit_off)&(0x3F>>>(6-bit_off)));
                // should we add bits from the next byte?
                if (bit_off < 6) identity[i-1] |= payload[byte_off-1]<<bit_off&0x3F;
            }
        }
    }

    public BDS20(byte[] payload) throws BadFormatException {
        this(payload, true);
    }

    /**
     * Maps ADS-B encoded to readable characters
     * @param digit encoded digit
     * @return readable character (# for unknown)
     */
    private static char mapChar (byte digit) {
        if (digit>0 && digit<27) return (char) ('A'+digit-1);
        else if (digit>47 && digit<58) return (char) ('0'+digit-48);
        else if (digit == 32) return ' ';
        else return '#';
    }

    /**
     * Maps ADS-B encoded to readable characters
     * @param digits array of encoded digits
     * @return array of decoded characters
     */
    private static char[] mapChar (byte[] digits) {
        char[] result = new char[digits.length];

        for (int i=0; i<digits.length; i++)
            result[i] = mapChar(digits[i]);

        return result;
    }

    /**
     * @return the call sign as 8 characters array
     */
    public char[] getIdentity() {
        return mapChar(identity);
    }

    /**
     * @return the decription of the emitter's category according to
     *         the ADS-B message format specification
     */

    public String toString() {
        // this class is specialized by BDS 0,8
        if (getBDSSelector() == 0x20)
            return super.toString()+"\n"+
                    "BDS 2,0:\n"+
                    "\tCallsign:\t\t"+new String(getIdentity());
        else return super.toString();
    }

}
