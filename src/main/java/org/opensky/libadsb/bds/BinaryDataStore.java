package org.opensky.libadsb.bds;

/*
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

import org.opensky.libadsb.exceptions.BadFormatException;
import org.opensky.libadsb.tools;

import java.io.Serializable;
import java.util.*;

/**
 * Super class for parser BDS registers
 *
 * @author Matthias Schäfer (schaefer@opensky-network.org)
 */
public class BinaryDataStore implements Serializable {

    // Comm-B data selector code
    private Byte BDS = null;
    private byte[] payload;

    // has this Comm-B data selector code been identified with high confidence?
    private boolean high_confidence;

    BinaryDataStore(byte[] payload) {
        this.payload = payload;
    }

    /**
     * @return the raw message field
     */
    public byte[] getPayload() {
        return payload;
    }

    /**
     * @return BDS selector or null if unknown
     */
    public Byte getBDSSelector() {
        return BDS;
    }

    /**
     * Use this for setting the BDS register selector internally.
     * @param BDS the register selector
     */
    void setBDSSelector(Byte BDS) {
        this.BDS = BDS;
    }

    public void setHighConfidence (boolean high_confidence) {
        this.high_confidence = high_confidence;
    }

    public boolean hasHighConfidence () {
        return high_confidence;
    }

    @Override
    public String toString() {
        return "Binary Data Store:\n" +
                String.format("\tComm-B data selector code: %02x\n", BDS) +
                "\tPayload: "+ tools.toHexString(payload);
    }

    /**
     * Updates the map of BDS regsiters and their confirmed bits
     * @param reg the respective register
     * @param match true if positively confirmed; false if it did not match static pattern
     * @param bits number of bits tested
     * @param regs all registers
     */
    private static void updateConfirmedBits (short reg, boolean match, int bits, HashMap<Short, Integer> regs) {
        if (regs.containsKey(reg)) {
            if (match) {
                regs.put(reg, regs.get(reg) + bits);
            } else {
                regs.remove(reg);
            }
        }
    }

    /**
     * This method checks all constant fields in the definitions of BDS registers
     * and provides a list of possible BDS registers given a certain payload. This
     * list can be used to further refine plausibility tests.
     * @param payload the MV/MB field of long Mode S replies
     * @return a map where key is BDS code (decimal) of possible candidate, value is number of confirmed bits
     */
    private static HashMap<Short, Integer> getPossibleRegisters (byte[] payload) {
        HashMap<Short, Integer> regs = new HashMap<Short, Integer>(50);

        // initialize with all BDS regs
        for (short reg : new Short[]{
                // Linked Comm-B segments 2-4
                // 0x02, 0x03, 0x04, // NOTE: excluded since probably unused
                // extended squitters
                0x05, 0x06, 0x07, 0x08, 0x09, // NOTE: removed 0x0a since it's only intended for ES
                // air/air information (state/intend)
                0x0b, 0x0c,
                // capability reports
                0x10, 0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f,
                // aircraft info
                0x20, 0x21, 0x22, 0x25,
                // ACAS active resolution advisory
                0x30,
                // intend
                0x40, 0x41, 0x42, 0x43,
                // meteorological reports
                0x44, 0x45,
                // VHF channel report
                0x48,
                // aircraft state (track, turn, position, waypoints)
                0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56,
                // quasi static parameters
                0x5f,
                // heading and speed, emergency priority, trajectory, operational info
                0x60, 0x61, 0x62, 0x65,
                // transponder + TCAS part number and version
                0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xea,
                // military applications
                0xf1, 0xf2
        }) regs.put(reg, 0);

        /////////// confirm or rule out registers based on static fields

        // Note: I rate non-zero static fields (like typecodes) with double weight

        int typecode = (payload[0]>>3) & 0x1f;

        updateConfirmedBits((short) 0x05, typecode >= 9 && typecode <= 22 && typecode != 19, 5 * 2, regs);

        updateConfirmedBits((short) 0x06, typecode >= 5 && typecode <= 8, 5 * 2, regs);

        updateConfirmedBits((short) 0x07, (payload[0] & 0x1f) == 0,5, regs);
        updateConfirmedBits((short) 0x07, (payload[1] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x07, (payload[2] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x07, (payload[3] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x07, (payload[4] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x07, (payload[5] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x07, (payload[6] & 0xff) == 0, 8, regs);

        // Note: type code 1 (set D) is reserved
        updateConfirmedBits((short) 0x08, typecode > 1 && typecode <= 4, 5 * 2, regs);

        updateConfirmedBits((short) 0x09, typecode == 19, 5 * 2, regs);

        updateConfirmedBits((short) 0x0b, (payload[6] & 0x01) == 0, 1, regs);

        updateConfirmedBits((short) 0x0c, (payload[6] & 0x07) == 0, 3, regs);

        updateConfirmedBits((short) 0x10, (payload[0] & 0xff) == 0x10, 8 * 2, regs);
        updateConfirmedBits((short) 0x10, (payload[1] & 0x7e) == 0, 5, regs);

        updateConfirmedBits((short) 0x17, (payload[3] & 0x07) == 0, 3, regs);
        updateConfirmedBits((short) 0x17, (payload[4] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x17, (payload[5] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x17, (payload[6] & 0xff) == 0, 8, regs);

        updateConfirmedBits((short) 0x1c, (payload[0] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x1c, (payload[1] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x1c, (payload[2] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x1c, (payload[3] & 0x80) == 0, 1, regs);

        updateConfirmedBits((short) 0x1f, (payload[0] & 0x01) == 0, 1, regs);
        updateConfirmedBits((short) 0x1f, (payload[1] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x1f, (payload[2] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x1f, (payload[3] & 0xf0) == 0, 4, regs);
        updateConfirmedBits((short) 0x1f, (payload[4] & 0x1f) == 0, 5, regs);
        updateConfirmedBits((short) 0x1f, (payload[5] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x1f, (payload[6] & 0xff) == 0, 8, regs);

        updateConfirmedBits((short) 0x20, (payload[0] & 0xff) == 0x20, 8 * 2, regs);

        updateConfirmedBits((short) 0x22, (payload[0] & 0x80) == 0, 1, regs);
        updateConfirmedBits((short) 0x22, (payload[1] & 0x02) == 0, 1, regs);
        updateConfirmedBits((short) 0x22, (payload[3] & 0x08) == 0, 1, regs);
        updateConfirmedBits((short) 0x22, (payload[5] & 0x20) == 0, 1, regs);

        updateConfirmedBits((short) 0x25, (payload[6] & 0x1f) == 0, 1, regs);

        updateConfirmedBits((short) 0x30, (payload[0] & 0xff) == 0x30, 8 * 2, regs);

        updateConfirmedBits((short) 0x40, (payload[4] & 0x01) == 0, 1, regs);
        updateConfirmedBits((short) 0x40, (payload[5] & 0xfe) == 0, 7, regs);
        updateConfirmedBits((short) 0x40, (payload[6] & 0x18) == 0, 2, regs);

        updateConfirmedBits((short) 0x41, (payload[6] & 0x01) == 0, 1, regs);

        updateConfirmedBits((short) 0x43, (payload[5] & 0x3f) == 0, 6, regs);
        updateConfirmedBits((short) 0x43, (payload[6] & 0xff) == 0, 8, regs);

        updateConfirmedBits((short) 0x44, (payload[0] & 0x80) == 0, 1, regs);

        updateConfirmedBits((short) 0x45, (payload[6] & 0x1f) == 0, 5, regs);

        updateConfirmedBits((short) 0x54, (payload[6] & 0x01) == 0, 1, regs);
        updateConfirmedBits((short) 0x55, (payload[6] & 0x01) == 0, 1, regs);
        updateConfirmedBits((short) 0x56, (payload[6] & 0x01) == 0, 1, regs);

        updateConfirmedBits((short) 0x5f, (payload[0] & 0x3f) == 0, 6, regs);
        updateConfirmedBits((short) 0x5f, (payload[1] & 0xf3) == 0, 6, regs);
        updateConfirmedBits((short) 0x5f, (payload[3] & 0x3d) == 0, 6, regs);
        updateConfirmedBits((short) 0x5f, (payload[4] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x5f, (payload[5] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0x5f, (payload[6] & 0xff) == 0, 8, regs);

        updateConfirmedBits((short) 0x61, typecode == 28, 5 * 2, regs);
        updateConfirmedBits((short) 0x61, (payload[0] & 0x04) == 0, 1, regs);

        updateConfirmedBits((short) 0x65, typecode == 31, 5 * 2, regs);
        updateConfirmedBits((short) 0x65, (payload[0] & 0x06) == 0, 2, regs);
        updateConfirmedBits((short) 0x65, (payload[6] & 0x03) == 0, 2, regs);

        updateConfirmedBits((short) 0xe3, (payload[0] & 0x40) == 0, 1, regs);
        updateConfirmedBits((short) 0xe3, (payload[6] & 0x1f) == 0, 5, regs);

        updateConfirmedBits((short) 0xe4, (payload[0] & 0x40) == 0, 1, regs);
        updateConfirmedBits((short) 0xe4, (payload[6] & 0x1f) == 0, 5, regs);

        updateConfirmedBits((short) 0xe5, (payload[0] & 0x40) == 0, 1, regs);
        updateConfirmedBits((short) 0xe5, (payload[6] & 0x1f) == 0, 5, regs);

        updateConfirmedBits((short) 0xe6, (payload[0] & 0x40) == 0, 1, regs);
        updateConfirmedBits((short) 0xe6, (payload[6] & 0x1f) == 0, 5, regs);

        updateConfirmedBits((short) 0xe7, (payload[0] & 0xff) == 0xe7, 8 * 2, regs);

        updateConfirmedBits((short) 0xea, (payload[0] & 0xff) == 0xea, 8 * 2, regs);

        updateConfirmedBits((short) 0xf1, (payload[3] & 0x07) == 0, 3, regs);
        updateConfirmedBits((short) 0xf1, (payload[4] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0xf1, (payload[5] & 0xff) == 0, 8, regs);
        updateConfirmedBits((short) 0xf1, (payload[6] & 0xff) == 0, 8, regs);

        updateConfirmedBits((short) 0xf2, typecode == 1, 5 * 2, regs);
        updateConfirmedBits((short) 0xf2, (payload[6] & 0xff) == 0, 8, regs);

        return regs;
    }

    public static BinaryDataStore parseRegister (byte[] payload) throws BadFormatException {
        return parseRegister(payload, null);
    }

    public static BinaryDataStore parseRegister (byte[] payload, Integer altitude) throws BadFormatException {
        if (payload.length != 7)
            throw new BadFormatException("BDS payload has an invalid length of "+payload.length);

        if (tools.isZero(payload))
            return new BDS00();

        // start with all possible given reserved fields and values
        HashMap<Short, Integer> regs = getPossibleRegisters(payload);

        // now check if one of the matches can be confirmed by cross-checking values
        for (Map.Entry<Short, Integer> e : new HashMap<Short, Integer>(regs).entrySet()) {
            try {
                switch (e.getKey()) {
                    case 0x05:
                        // TODO: actually I need the ADS-B version here
                        BDS05V0 tmp05 = new BDS05V0(payload);

//                        if (altitude != null && tmp05.hasAltitude() &&
//                                Math.abs(tmp05.getAltitude() - altitude) > 50)
//                            System.out.println(String.format("Warning, diff over 50ft: %d (Barometric: %b, Typecode: %d)",
//                                    tmp05.getAltitude(),
//                                    tmp05.isBarometricAltitude(),
//                                    tmp05.getTypeCode()));

                        if (altitude != null && tmp05.hasAltitude() && Math.abs(tmp05.getAltitude() - altitude) <= 50)
                            updateConfirmedBits(e.getKey(), true, 12, regs);

                        break;
                    case 0x10:
                        // FIXME Exceptions should only be used for exceptional events such as programming errors. Bit errors are common
                        new BDS10(payload);

                        // just to acknowledge some additional checks in the constructor
                        updateConfirmedBits(e.getKey(), true, 3, regs);
                        break;
                    case 0x08:
                    case 0x20:
                        BDS08 tmp08 = new BDS08(payload);

                        // test whether callsign makes sense
                        boolean plausible = true;
                        boolean space = false;
                        for (char c : tmp08.getIdentity())
                            if (space && c != ' ') {
                                plausible = false;
                                break;
                            } else if (c == ' ') {
                                space = true;
                            } else if (c == '#') {
                                plausible = false;
                                break;
                            }

                        updateConfirmedBits(e.getKey(), plausible, 48, regs);
                        break;
                    case 0x30:
                        BDS30 tmp30 = new BDS30(payload);

                        if (tmp30.getThreatICAO24() == null && !tmp30.hasThreatBRA())
                            updateConfirmedBits(e.getKey(), true, 28, regs);

                        if (tmp30.hasThreatBRA() && tmp30.getThreatAltitude() != null && altitude != null)
                            updateConfirmedBits(e.getKey(),
                                    Math.abs(altitude-tmp30.getThreatAltitude()) <= 1000,
                                    15, regs);

                        break;
                    case 0xf1:
                        // FIXME Exceptions should only be used for exceptional events such as programming errors. Bit errors are common
                        // simply try do decode (will raise exception if format is bad)
                        new BDSF1(payload);
                    default:
                }
            } catch (BadFormatException reason) {
                if (e.getKey() == (short) 0x10)
                    System.out.println(String.format("Skipping BDS %02x. Reason: %s ", e.getKey(), reason.getMessage()));
                updateConfirmedBits(e.getKey(), false, 0, regs);
            }
        }

        // get list sorted by likelihood
        ArrayList<Map.Entry<Short, Integer>> regs_sorted = new ArrayList<Map.Entry<Short, Integer>>(regs.entrySet());
        Collections.sort(regs_sorted, new Comparator<Map.Entry<Short, Integer>>() {
            @Override
            public int compare(Map.Entry<Short, Integer> e1, Map.Entry<Short, Integer> e2) {
                return e2.getValue() - e1.getValue();
            }
        });

        if (regs_sorted.get(0).getKey() != 0x05) {
            System.out.print(regs_sorted.size() + " possible registers: ");
            for (Map.Entry<Short, Integer> e : regs_sorted)
                System.out.print(String.format("%02x (%d) ", e.getKey(), e.getValue()));
            System.out.println();
        }

        // not conclusive
        // XXX how can happen?
        if (regs_sorted.size() > 1 && regs_sorted.get(0).getValue().equals(regs_sorted.get(1).getValue()))
            return new BinaryDataStore(payload);

        switch (regs_sorted.get(0).getKey()) {
            case 0x05:
                return new BDS05V0(payload);
            case 0x08:
                return new BDS08(payload);
            case 0x10:
                return new BDS10(payload);
            case 0x20:
                return new BDS20(payload);
            case 0x30:
                return new BDS30(payload);
            default:
                return new BinaryDataStore(payload);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BinaryDataStore that = (BinaryDataStore) o;

        return Arrays.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(payload);
    }
}
