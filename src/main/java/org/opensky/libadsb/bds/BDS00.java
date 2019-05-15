package org.opensky.libadsb.bds;

import org.opensky.libadsb.exceptions.BadFormatException;

/**
 * Dummy BDS register for 'all zero' cases
 */
public class BDS00 extends BinaryDataStore {

    public BDS00() throws BadFormatException {
        super(new byte[7]);
        setBDSSelector((byte) 0x00);
    }

    public String toString() {
        return super.toString()+"\nAll zeroes.";
    }

}
