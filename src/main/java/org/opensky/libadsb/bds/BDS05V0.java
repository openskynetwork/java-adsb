package org.opensky.libadsb.bds;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.msgs.AirborneOperationalStatusV1Msg;
import org.opensky.libadsb.msgs.AirborneOperationalStatusV2Msg;

/**
 * Parser for BDS 0,5 registers (extended squitter airborne position version 0)
 * <br><br>
 * Note: most of the code was copied from the ADS-B decoders!
 */
public class BDS05V0 extends BinaryDataStore {

    private boolean horizontal_position_available;
    private boolean altitude_available;
    private byte surveillance_status;
    private boolean nic_suppl_b;
    private short altitude_encoded;
    private boolean time_flag;
    private boolean cpr_format;
    private int cpr_encoded_lat;
    private int cpr_encoded_lon;
    private short typecode;

    /**
     * Parses BDS format 0,5
     * @param payload the MV/ME field
     * @throws BadFormatException if type code does not match
     */
    public BDS05V0(byte[] payload) throws BadFormatException {
        super(payload);

        setBDSSelector((byte) 0x05);

        typecode = (short) ((payload[0]>>3) & 0x1f);

        if (!(typecode == 0 || (typecode >= 9 && typecode <= 18) || (typecode >= 20 && typecode <= 22)))
            throw new BadFormatException("Format is not BDS 0,5! Wrong format type code ("+typecode+").");

        horizontal_position_available = typecode != 0;

        surveillance_status = (byte) ((payload[0]>>>1)&0x3);
        nic_suppl_b = (payload[0]&0x1) == 1;

        altitude_encoded = (short) (((payload[1]<<4)|((payload[2]>>>4)&0xF))&0xFFF);
        altitude_available = altitude_encoded != 0;

        time_flag = ((payload[2]>>>3)&0x1) == 1;
        cpr_format = ((payload[2]>>>2)&0x1) == 1;
        cpr_encoded_lat = (((payload[2]&0x3)<<15) | ((payload[3]&0xFF)<<7) | ((payload[4]>>>1)&0x7F)) & 0x1FFFF;
        cpr_encoded_lon = (((payload[4]&0x1)<<16) | ((payload[5]&0xFF)<<8) | (payload[6]&0xFF)) & 0x1FFFF;
    }

    /**
     * @return format typecode of BDS register
     */
    public short getTypeCode() {
        return typecode;
    }

    /**
     * The position error, i.e., 95% accuracy for the horizontal position. Values according to DO-260B Table N-4.
     *
     *  The horizontal containment radius is also known as "horizontal protection level".
     *
     * @return horizontal containment radius limit in meters. A return value of -1 means "unkown".
     */
    public double getHorizontalContainmentRadiusLimit() {
        switch (typecode) {
            case 0: case 18: case 22: return -1;
            case 9: case 20: return 7.5;
            case 10: case 21: return 25;
            case 11: return 185.2;
            case 12: return 370.4;
            case 13: return 926;
            case 14: return 1852;
            case 15: return 3704;
            case 16: return 18520;
            case 17: return 37040;
            default: return -1;
        }
    }

    /**
     * Navigation accuracy category according to DO-260B Table N-7. In ADS-B version 1+ this information is contained
     * in the operational status message. For version 0 it is derived from the format type code.
     *
     * For a value in meters, use {@link #getPositionUncertainty()}.
     *
     * @return NACp according value (no unit), comparable to NACp in {@link AirborneOperationalStatusV2Msg} and
     * {@link AirborneOperationalStatusV1Msg}.
     */
    public byte getNACp() {
        switch (typecode) {
            case 0: case 18: case 22: return 0;
            case 9: case 20: return 11;
            case 10: case 21: return 10;
            case 11: return 8;
            case 12: return 7;
            case 13: return 6;
            case 14: return 5;
            case 15: return 4;
            case 16: case 17: return 1;
            default: return 0;
        }
    }

    /**
     * Get the 95% horizontal accuracy bounds (EPU) derived from NACp value in meter, see table N-7 in RCTA DO-260B.
     *
     * The concept of NACp has been introduced in ADS-B version 1. For version 0 transmitters, a mapping exists which
     * is reflected by this method.
     * Values are comparable to those of {@link AirborneOperationalStatusV1Msg}'s and
     * {@link AirborneOperationalStatusV2Msg}'s getPositionUncertainty method for aircraft supporting ADS-B
     * version 1 and 2.
     *
     * @return the estimated position uncertainty according to the position NAC in meters (-1 for unknown)
     */
    public double getPositionUncertainty() {
        switch (typecode) {
            case 0: case 18: case 22: return -1;
            case 9: return 3;
            case 10: return 10;
            case 11: return 92.6;
            case 12: return 185.2;
            case 13: return 463;
            case 14: return 926;
            case 15: return 1852;
            case 16: return 9260;
            case 17: return 18520;
            default: return -1;
        }
    }

    /**
     * @return Navigation integrity category. A NIC of 0 means "unkown".
     */
    public byte getNIC() {
        switch (typecode) {
            case 0: case 18: case 22: return 0;
            case 9: case 20: return 11;
            case 10: case 21: return 10;
            case 11: return 9;
            case 12: return 7;
            case 13: return 6;
            case 14: return 5;
            case 15: return 4;
            case 16: return 3;
            case 17: return 1;
            default: return 0;
        }
    }

    /**
     * Source/Surveillance Integrity Level (SIL) according to DO-260B Table N-8.
     *
     * The concept of SIL has been introduced in ADS-B version 1. For version 0 transmitters, a mapping exists which
     * is reflected by this method.
     * Values are comparable to those of {@link AirborneOperationalStatusV1Msg}'s and
     * {@link AirborneOperationalStatusV2Msg}'s getSIL method for aircraft supporting ADS-B
     * version 1 and 2.
     *
     * @return the source integrity level (SIL) which indicates the propability of exceeding
     *         the NIC containment radius.
     */
    public byte getSIL() {
        switch (typecode) {
            case 0: case 18: case 22: return 0;
            default: return 2;
        }
    }

    /**
     * @return whether horizontal position information is available
     */
    public boolean hasPosition() {
        return horizontal_position_available;
    }

    /**
     * @return whether altitude information is available
     */
    public boolean hasAltitude() {
        return altitude_available;
    }

    /**
     * @see #getSurveillanceStatusDescription()
     * @return the surveillance status
     */
    public byte getSurveillanceStatus() {
        return surveillance_status;
    }

    /**
     * This is a function of the surveillance status field in the position
     * message.
     *
     * @return surveillance status description as defines in DO-260B
     */
    public String getSurveillanceStatusDescription() {
        String[] desc = {
                "No condition information",
                "Permanent alert (emergency condition)",
                "Temporary alert (change in Mode A identity code oter than emergency condition)",
                "SPI condition"
        };

        return desc[surveillance_status];
    }

    /**
     * @return for ADS-B version 0 and 1 messages true, iff transmitting system uses only one antenna.
     */
    public boolean hasSingleAntenna() {
        return nic_suppl_b;
    }

    /**
     * @return flag which will indicate whether or not the Time of Applicability of the message
     *         is synchronized with UTC time. False will denote that the time is not synchronized
     *         to UTC. True will denote that Time of Applicability is synchronized to UTC time.
     */
    public boolean hasTimeFlag() {
        return time_flag;
    }

    /**
     * @return the CPR encoded binary latitude
     */
    public int getCPREncodedLatitude() {
        return cpr_encoded_lat;
    }

    /**
     * @return the CPR encoded binary longitude
     */
    public int getCPREncodedLongitude() {
        return cpr_encoded_lon;
    }

    /**
     * @return whether message is odd format. Returns false if message is even format. This is
     *         needed for position decoding as the CPR algorithm uses both formats.
     */
    public boolean isOddFormat() {
        return cpr_format;
    }

    /**
     * @return true, if barometric altitude. False if GNSS is used to determine altitude
     */
    public boolean isBarometricAltitude() {
        return typecode < 20;
    }

    /**
     * This method converts a gray code encoded int to a standard decimal int
     * @param gray gray code encoded int of length bitlength
     *        bitlength bitlength of gray code
     * @return radix 2 encoded integer
     */
    private static int grayToBin(int gray, int bitlength) {
        int result = 0;
        for (int i = bitlength-1; i >= 0; --i)
            result = result|((((0x1<<(i+1))&result)>>>1)^((1<<i)&gray));
        return result;
    }

    /**
     * @return the decoded altitude in feet or null if altitude is not available. The latter can be checked with
     * {@link #hasAltitude()}.
     */
    public Integer getAltitude() {
        if (!altitude_available) return null;

        boolean Qbit = (altitude_encoded&0x10)!=0;
        int N;
        if (Qbit) { // altitude reported in 25ft increments
            N = (altitude_encoded&0xF) | ((altitude_encoded&0xFE0)>>>1);
            return 25*N-1000;
        }
        else { // altitude is above 50175ft, so we use 100ft increments

            // it's decoded using the Gillham code
            int C1 = (0x800&altitude_encoded)>>>11;
            int A1 = (0x400&altitude_encoded)>>>10;
            int C2 = (0x200&altitude_encoded)>>>9;
            int A2 = (0x100&altitude_encoded)>>>8;
            int C4 = (0x080&altitude_encoded)>>>7;
            int A4 = (0x040&altitude_encoded)>>>6;
            int B1 = (0x020&altitude_encoded)>>>5;
            int B2 = (0x008&altitude_encoded)>>>3;
            int D2 = (0x004&altitude_encoded)>>>2;
            int B4 = (0x002&altitude_encoded)>>>1;
            int D4 = (0x001&altitude_encoded);

            // this is standard gray code
            int N500 = grayToBin(D2<<7|D4<<6|A1<<5|A2<<4|A4<<3|B1<<2|B2<<1|B4, 8);

            // 100-ft steps must be converted
            int N100 = grayToBin(C1<<2|C2<<1|C4, 3)-1;
            if (N100 == 6) N100=4;
            if (N500%2 != 0) N100=4-N100; // invert it

            return -1200+N500*500+N100*100;
        }
    }

    public String toString() {
        return super.toString()+"\n"+
                "BDS 0,5 v0:\n"+
                "\tFormat:\t\t\t\t\t"+(isOddFormat()?"odd":"even")+
                "\n\tSurveillance status:\t"+getSurveillanceStatusDescription()+
                "\n\tHas position:\t\t\t"+(hasPosition()?"yes":"no")+
                "\n\tSingle antenna flag:\t"+(hasSingleAntenna()?"yes":"no")+
                "\n\tAltitude:\t\t\t\t"+(hasAltitude()?getAltitude():"unkown");
    }

}
