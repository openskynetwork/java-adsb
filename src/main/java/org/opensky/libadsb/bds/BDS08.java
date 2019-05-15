package org.opensky.libadsb.bds;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.msgs.ExtendedSquitter;
import org.opensky.libadsb.msgs.ModeSReply;

/**
 * Parser for BDS 0,8 registers (extended squitter aircraft identification and category)
 * <br><br>
 * Note: most of the code was copied from the ADS-B decoders!
 */
public class BDS08 extends BDS20 {

    private byte emitter_category;
    private short typecode;

    public BDS08(byte[] payload) throws BadFormatException {
        super(payload, false);

        setBDSSelector((byte) 0x08);

        typecode = (short) ((payload[0]>>3) & 0x1f);

        if (typecode < 1 || typecode > 4) {
            throw new BadFormatException("Format is not BDS 0,8! Wrong format type code ("+typecode+").");
        }

        emitter_category = (byte) (payload[0] & 0x7);
    }

    /**
     * @return the emitter's category (numerical)
     */
    public byte getEmitterCategory() {
        return emitter_category;
    }

    /**
     * @return the decription of the emitter's category according to
     *         the ADS-B message format specification
     */
    public String getCategoryDescription () {
        // category descriptions according
        // to the ADS-B specification
        String[][] categories = {{
                "No ADS-B Emitter Category Information",
                "Light (< 15500 lbs)",
                "Small (15500 to 75000 lbs)",
                "Large (75000 to 300000 lbs)",
                "High Vortex Large (aircraft such as B-757)",
                "Heavy (> 300000 lbs)",
                "High Performance (> 5g acceleration and 400 kts)",
                "Rotorcraft"
        },{
                "No ADS-B Emitter Category Information",
                "Glider / sailplane",
                "Lighter-than-air",
                "Parachutist / Skydiver",
                "Ultralight / hang-glider / paraglider",
                "Reserved",
                "Unmanned Aerial Vehicle",
                "Space / Trans-atmospheric vehicle",
        },{
                "No ADS-B Emitter Category Information",
                "Surface Vehicle – Emergency Vehicle",
                "Surface Vehicle – Service Vehicle",
                "Point Obstacle (includes tethered balloons)",
                "Cluster Obstacle",
                "Line Obstacle",
                "Reserved",
                "Reserved"
        },{
                "Reserved",
                "Reserved",
                "Reserved",
                "Reserved",
                "Reserved",
                "Reserved",
                "Reserved",
                "Reserved"
        }};

        return categories[4-typecode][emitter_category];
    }

    @Override
    public String toString() {
        return super.toString()+"\n"+
                "BDS 0,8:\n"+
                "\tEmitter category:\t"+getCategoryDescription()+" ("+getEmitterCategory()+")\n"+
                "\tCallsign:\t\t"+new String(getIdentity());
    }

}
