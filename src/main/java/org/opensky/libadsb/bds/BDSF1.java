package org.opensky.libadsb.bds;

import org.opensky.libadsb.exceptions.BadFormatException;

/**
 * Parser for BDS F,1 registers (military applications)
 */
public class BDSF1 extends BinaryDataStore {

    private boolean mode1_full;
    private Short mode1_code;
    private Short mode2_code;

    public BDSF1(byte[] payload) throws BadFormatException {
        super(payload);
        setBDSSelector((byte) 0xf1);

        boolean mode1_available = (payload[0] & 0x80) > 0;
        mode1_full = (payload[0] & 0x40) > 0;
        mode1_code = (short) (((payload[0] & 0x3f)<<7) | ((payload[1]>>1) & 0x7f));
        boolean mode2_available = (payload[1] & 0x01) > 0;
        mode2_code = (short) (((payload[2] & 0xff)<<5) | ((payload[3]>>3) & 0xff));

        // check some things in favor of BDS register detection
        if (mode1_available && mode1_code>0)
            throw new BadFormatException("Mode 1 code is not zero although it's flagged 'unavailable'.");

        if (mode2_available && mode2_code>0)
            throw new BadFormatException("Mode 2 code is not zero although it's flagged 'unavailable'.");

        if (!mode1_full && (mode1_code & 0x1515) > 0)
            throw new BadFormatException("Mode 1 code is should only have 2 characters but C- and D-bits are set.");
    }

    public String toString() {
        return super.toString()+"\n"+
                "BDS F,1:\n"+
                String.format("\tMode 1 code full:\t%b\n", mode1_full) +
                String.format("\tMode 1 code:\t\t%04x\n", mode1_code) +
                String.format("\tMode 2 code:\t\t%04x\n", mode2_code);
    }

}
