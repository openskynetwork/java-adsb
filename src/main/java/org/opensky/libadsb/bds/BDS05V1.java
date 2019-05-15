package org.opensky.libadsb.bds;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.msgs.AirborneOperationalStatusV1Msg;

/**
 * Parser for BDS 0,5 registers (extended squitter airborne position version 1)
 * <br><br>
 * Note: most of the code was copied from the ADS-B decoders!
 */
public class BDS05V1 extends BDS05V0 {

    private boolean nic_suppl_a;

    public BDS05V1(byte[] payload) throws BadFormatException {
        super(payload);
    }

    /**
     * @param nic_suppl Navigation Integrity Category (NIC) supplement from operational status message.
     *        Otherwise worst case is assumed for containment radius limit and NIC. ADS-B version 1+ only!
     */
    public void setNICSupplementA(boolean nic_suppl) {
        this.nic_suppl_a = nic_suppl;
    }

    /**
     * @return NIC supplement that was set before
     */
    public boolean hasNICSupplementA() {
        return nic_suppl_a;
    }

    /**
     * The position error, i.e., 95% accuracy for the horizontal position. For the navigation accuracy category
     * (NACp) see {@link AirborneOperationalStatusV1Msg}. Values according to DO-260B Table N-11.
     *
     * The horizontal containment radius is also known as "horizontal protection level".
     *
     * @return horizontal containment radius limit in meters. A return value of -1 means "unknown".
     *         If aircraft uses ADS-B version 1+, set NIC supplement A from Operational Status Message
     *         for better precision.
     */
    public double getHorizontalContainmentRadiusLimit() {
        switch (getTypeCode()) {
            case 0: case 18: case 22: return -1;
            case 9: case 20: return 7.5;
            case 10: case 21: return 25;
            case 11: return nic_suppl_a ? 75.0 : 185.2;
            case 12: return 370.4;
            case 13: return nic_suppl_a ? 1111.2 : 926;
            case 14: return 1852;
            case 15: return 3704;
            case 16: return nic_suppl_a ? 7408 : 14816;
            case 17: return 37040;
            default: return -1;
        }
    }

    /**
     * Values according to DO-260B Table N-11
     * @return Navigation integrity category. A NIC of 0 means "unkown".
     */
    public byte getNIC() {
        switch (getTypeCode()) {
            case 0: case 18: case 22: return 0;
            case 9: case 20: return 11;
            case 10: case 21: return 10;
            case 11: return (byte) (nic_suppl_a ? 9 : 8);
            case 12: return 7;
            case 13: return 6;
            case 14: return 5;
            case 15: return 4;
            case 16: return (byte) (nic_suppl_a ? 3 : 2);
            case 17: return 1;
            default: return 0;
        }
    }

    public String toString() {
        return super.toString()+"\n"+
                "BDS 0,5 v1:\n"+
                "\tFormat:\t\t\t\t\t"+(isOddFormat()?"odd":"even")+
                "\n\tSurveillance status:\t"+getSurveillanceStatusDescription()+
                "\n\tHas position:\t\t\t"+(hasPosition()?"yes":"no")+
                "\n\tSingle antenna flag:\t"+(hasSingleAntenna()?"yes":"no")+
                "\n\tNIC supplement A:\t"+(hasNICSupplementA()?"yes":"no")+
                "\n\tNIC:\t\t"+getNIC()+
                "\n\tHCR:\t\t"+getHorizontalContainmentRadiusLimit()+
                "\n\tAltitude:\t\t\t\t"+(hasAltitude()?getAltitude():"unkown");
    }

}
