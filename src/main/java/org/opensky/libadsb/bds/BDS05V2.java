package org.opensky.libadsb.bds;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.msgs.AirborneOperationalStatusV2Msg;

/**
 * Parser for BDS 0,5 registers (extended squitter airborne position version 2)
 * <br><br>
 * Note: most of the code was copied from the ADS-B decoders!
 */
public class BDS05V2 extends BDS05V1 {

    private boolean nic_suppl_a;

    public BDS05V2(byte[] payload) throws BadFormatException {
        super(payload);
    }

    /**
     * NIC supplement B as introduced in ADS-B version 2. The flag indicated single antenna in previous versions.
     * @return NIC supplement B to refine horizontal containment radius limit and navigation integrity category
     */
    public boolean hasNICSupplementB() {
        return super.hasSingleAntenna();
    }

    /**
     * The position error, i.e., 95% accuracy for the horizontal position. For the navigation accuracy category
     * (NACp) see {@link AirborneOperationalStatusV2Msg}. According to DO-260B Table 2-14.
     *
     * The horizontal containment radius is also known as "horizontal protection level".
     *
     * @return horizontal containment radius limit in meters. A return value of -1 means "unkown".
     *         If aircraft uses ADS-B version 2, set NIC supplement A from Operational Status Message
     *         for better precision. Otherwise, we'll be pessimistic.
     */
    public double getHorizontalContainmentRadiusLimit() {
        switch (getTypeCode()) {
            case 0: case 18: case 22: return -1;
            case 9: case 20: return 7.5;
            case 10: case 21: return 25;
            case 11:
                return hasNICSupplementB() && hasNICSupplementA() ? 75 : 185.2;
            case 12: return 370.4;
            case 13:
                if (!hasNICSupplementB()) return 926;
                else return hasNICSupplementA() ? 1111.2 : 555.6;
            case 14: return 1852;
            case 15: return 3704;
            case 16:
                return hasNICSupplementB() && hasNICSupplementA() ? 7408 : 14816;
            case 17: return 37040;
            default: return -1;
        }
    }

    /**
     * According to DO-260B Table 2-14.
     * @return Navigation integrity category. A NIC of 0 means "unkown".
     */
    public byte getNIC() {
        switch (getTypeCode()) {
            case 0: case 18: case 22: return 0;
            case 9: case 20: return 11;
            case 10: case 21: return 10;
            case 11:
                return (byte) (hasNICSupplementB() && hasNICSupplementA() ? 9 : 8);
            case 12: return 7;
            case 13: return 6;
            case 14: return 5;
            case 15: return 4;
            case 16:
                return (byte) (hasNICSupplementB() && hasNICSupplementA() ? 3 : 2);
            case 17: return 1;
            default: return 0;
        }
    }

    public String toString() {
        return super.toString()+"\n"+
                "BDS 0,5 v1:\n"+
                "\tFormat:\t\t\t\t\t"+(isOddFormat()?"odd":"even")+
                "\n\tSurveillance status:\t"+getSurveillanceStatusDescription()+
                "\n\tHas position:\t"+(hasPosition()?"yes":"no")+
                "\n\tSingle antenna flag:\t"+(hasSingleAntenna()?"yes":"no")+
                "\n\tNIC supplement A:\t"+(hasNICSupplementA()?"yes":"no")+
                "\n\tNIC supplement B:\t"+(hasNICSupplementB()?"yes":"no")+
                "\n\tNIC:\t\t"+getNIC()+
                "\n\tHCR:\t\t"+getHorizontalContainmentRadiusLimit()+
                "\n\tAltitude:\t"+(hasAltitude()?getAltitude():"unkown");
    }

}
