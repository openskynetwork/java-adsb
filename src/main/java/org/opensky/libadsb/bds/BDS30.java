package org.opensky.libadsb.bds;

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.msgs.AltitudeReply;

import java.util.ArrayList;

/**
 * Parser for BDS 3,0 registers (ACAS active resolution advisory)
 * <br><br>
 * Note: most of the code was copied from the ADS-B decoders!
 */
public class BDS30 extends BinaryDataStore {

    private byte threat_id_type;
    private int threat_id;
    private boolean ra_terminated;
    private boolean multiple_threats;

    private boolean b41;
    private boolean b42;
    private boolean b43;
    private boolean b44;
    private boolean b45;
    private boolean b46;
    private boolean b47;

    private boolean noPassBelow;
    private boolean noPassAbove;
    private boolean noTurnLeft;
    private boolean noTurnRight;

    public BDS30(byte[] payload) throws BadFormatException {
        super(payload);
        setBDSSelector((byte) 0x30);

        if (payload[0] != 0x30)
            throw new BadFormatException(String.format("Wrong BDS code (%02x), expected 3,0.", payload[0]));

        int active_ras = (short) (((payload[1] & 0xff) << 6) | ((payload[2] & 0xfc) >> 2));

        // bits 48-54
        if ((active_ras & 0x7f) > 0)
            throw new BadFormatException("ACAS III reserved fields are not zero!");

        // the bits according to Annex 10 V4 (4.3.8.4.2.2.)
        b41 = (active_ras & 0x2000) > 0;
        b42 = (active_ras & 0x1000) > 0;
        b43 = (active_ras & 0x0800) > 0;
        b44 = (active_ras & 0x0400) > 0;
        b45 = (active_ras & 0x0200) > 0;
        b46 = (active_ras & 0x0100) > 0;
        b47 = (active_ras & 0x0080) > 0;

        byte racs_record = (byte) (((payload[2] & 0x03) << 2) | ((payload[3] & 0xc0) >> 6));
        noPassBelow = (racs_record & 0x8) > 0;
        noPassAbove = (racs_record & 0x4) > 0;
        noTurnLeft = (racs_record & 0x2) > 0;
        noTurnRight = (racs_record & 0x1) > 0;

        ra_terminated = (payload[3] & 0x20) > 0;
        multiple_threats = (payload[3] & 0x10) > 0;

        threat_id_type = (byte) ((payload[3] & 0x0c) >> 2);
        threat_id = ((payload[3] & 0x3) << 24) |
                ((payload[4] & 0xff) << 16) |
                ((payload[5] & 0xff) << 8) |
                (payload[6] & 0xff);

        if (threat_id_type == 2 && (threat_id & 0x80000) > 0)
            throw new BadFormatException("X bit in altitude code must be zero.");

        if (threat_id_type == 2 && (threat_id & 0x3f) > 60)
            throw new BadFormatException("Invalid bearing!");

        if (threat_id_type == 0 && threat_id != 0)
            throw new BadFormatException("Thread ID is not zero, although indicated by ID.");

        if (threat_id_type == 3)
            throw new BadFormatException("Unassigned thread ID was used.");
    }

    public boolean hasRA() {
        return multiple_threats || b41;
    }

    /**
     * @return ICAO 24-bit address of threat's Mode S transponder or null if address not available
     */
    public Integer getThreatICAO24 () {
        if (threat_id_type != 1)
            return null;
        else
            return threat_id>>2;
    }

    /**
     * @return true iff bearing, range, and altitude are available in this RA
     */
    public boolean hasThreatBRA() {
        return threat_id_type == 2;
    }

    /**
     * @return the altitude in feet if available, null otherwise
     */
    public Integer getThreatAltitude() {
        if (threat_id_type == 2)
            return AltitudeReply.decodeAltitude((short) (threat_id>>13));
        else return null;
    }

    /**
     * @return most recent threat range in NM according to ICAO Annex 10 V4 §4.3.8.4.2.2.1.6.2; null if not available
     */
    public Double getThreatRange() {
        byte n = (byte) ((threat_id & 0x1fc0) >> 6);

        if (threat_id_type != 2 || n == 0)
            return null;
        else if (n == 1)
            return 0.05;
        else if (n < 127)
            return (n-1)/10.;
        else return 12.55;
    }

    /**
     * @return bearing in degrees or null if not available
     */
    public Double getThreatBearing() {
        byte n = (byte) (threat_id & 0x3f);

        if (threat_id_type == 2 && n > 0 && n < 61)
            return 6.*n - 0.5;
        else return null;
    }

    private String getThreadIDTypeString () {
        switch (threat_id_type) {
            case 0:
                return "No data";
            case 1:
                return "Mode S address";
            case 2:
                return "Bearing, Range, Altitude";
            default:
                return "Unassigned";
        }
    }

    public String toString() {
        StringBuilder ret = new StringBuilder();

        ret.append(super.toString());
        ret.append("\nBDS3,0:\n");
        ret.append(String.format("\tThread ID Type:\t%s\n", getThreadIDTypeString()));
        if (threat_id_type == 1)
            ret.append(String.format("\tThreat's transponder address:\t%06x\n", getThreatICAO24()));
        ret.append(String.format("\tHas threat BRA:\t%b\n", hasThreatBRA()));
        if (hasThreatBRA()) {
            ret.append(String.format("\tAltitude:\t%s ft\n", getThreatAltitude()));
            ret.append(String.format("\tRange:\t%s NM\n", getThreatRange()));
            ret.append(String.format("\tBearing:\t%s degrees\n", getThreatBearing()));
        }
        ret.append(String.format("\tNumber of threats:\t%s\n", multiple_threats ? "> 1" : (b41 ? "1" : "0")));

        // decode RA info
        if (!multiple_threats && !b41)
            ret.append("\tNo RA has been generated.");
        else if (ra_terminated)
            ret.append("\tThe following RA has been terminated:");
        else ret.append("\tThe following RA is currently being generated:");

        if (b41) {
            ret.append(String.format("\n\t\tRA is %s", b42 ? "corrective" : "preventive"));
            ret.append(String.format("\n\t\t%s sense RA has been generated", b43 ? "Downward" : "Upward"));
            ret.append(String.format("\n\t\tRA is %sincreased rate", b44 ? "" : "not "));
            ret.append(String.format("\n\t\tRA is %sa sense reversal", b45 ? "" : "not "));
            ret.append(String.format("\n\t\tRA is %saltitude crossing", b46 ? "" : "not "));
            ret.append(String.format("\n\t\tRA is %s", b47 ? "positive" : "vertical speed limit"));
        } else if (multiple_threats) {
            ret.append(String.format("\n\t\tRA %s a correction in the upward sense", b42 ? "requires" : "does not require"));
            ret.append(String.format("\n\t\tRA %s a positive climb", b43 ? "requires" : "does not require"));
            ret.append(String.format("\n\t\tRA %s a correction in the downward sense", b44 ? "requires" : "does not require"));
            ret.append(String.format("\n\t\tRA %s a positive descend", b45 ? "requires" : "does not require"));
            ret.append(String.format("\n\t\tRA %s a crossing", b46 ? "requires" : "does not require"));
            ret.append(String.format("\n\t\tRA is %sa sense reversal", b47 ? "" : "not "));
        }

        ArrayList<String> racs = new ArrayList<String>(4);
        if (noPassBelow) racs.add("no pass below");
        if (noPassAbove) racs.add("no pass above");
        if (noTurnLeft) racs.add("no turn left");
        if (noTurnRight) racs.add("no turn right");
        if (!noPassBelow && !noPassAbove && !noTurnLeft && !noTurnRight) racs.add("none");
        ret.append("\n\tActive RA complements: ").append(racs.toString());

        return ret.toString();
    }

}
