package org.opensky.libadsb.bds;

import org.opensky.libadsb.exceptions.BadFormatException;

/**
 * Parser for BDS 1,0 registers (Data Link Capability Report)
 * <br><br>
 * Note: most of the code was copied from the ADS-B decoders!
 */
public class BDS10 extends BinaryDataStore {

    private boolean continuation;
    private boolean overlay_command_capability;
    private boolean tcas_operational;
    private byte modes_subnetwork_version;
    private boolean modes_enhanced_protocol;
    private boolean modes_specific_services;
    private byte elm_uplink_throughput;
    private byte elm_downlink_throughput;
    private boolean aircraft_identification_capability;
    private boolean squitter_capability_subfield;
    private boolean surveillance_identifier_code;
    private boolean common_usage_gicb_capability_report;
    private boolean hybrid_surveillance_capability;
    private boolean ta_only;
    private byte tcas_version;
    private int dte_subaddress_support_mask;

    public BDS10(byte[] payload) throws BadFormatException {
        super(payload);
        setBDSSelector((byte) 0x10);

        if (payload[0] != 0x10)
            throw new BadFormatException(String.format("Wrong BDS code (%02x), expected 1,0.", payload[0]));

        continuation = (payload[1] & 0x80) > 0;
        overlay_command_capability = (payload[1] & 0x02) > 0;
        tcas_operational = (payload[1] & 0x01) > 0;
        modes_subnetwork_version = (byte) ((payload[2] & 0xfe)>>1);
        modes_enhanced_protocol = (payload[2] & 0x01) > 0;
        modes_specific_services = (payload[3] & 0x80) > 0;
        elm_uplink_throughput = (byte) ((payload[3] & 0x70)>>4);
        elm_downlink_throughput = (byte) (payload[3] & 0x0f);
        aircraft_identification_capability = (payload[4] & 0x80) > 0;
        squitter_capability_subfield = (payload[4] & 0x40) > 0;
        surveillance_identifier_code = (payload[4] & 0x20) > 0;
        common_usage_gicb_capability_report = (payload[4] & 0x10) > 0;
        hybrid_surveillance_capability = (payload[4] & 0x08) > 0;
        ta_only = (payload[4] & 0x04) > 0;
        tcas_version = (byte) (payload[4] & 0x03);
        dte_subaddress_support_mask = ((payload[5] & 0xff) << 8) | (payload[6] & 0xff);

        // check for unassigned values
        if (elm_uplink_throughput == 7)
            throw new BadFormatException("ELM average throughput capability uses reserved code (7)!");

        if (elm_downlink_throughput >= 7)
            throw new BadFormatException(String.format("ELM average throughput capability uses reserved code (%d)!", elm_downlink_throughput));

        if (tcas_version == 3)
            throw new BadFormatException("TCAS version set to reserved value!");
    }

    /**
     * @return true if subsequent register 1,1 shall be extracted, too; false otherwise
     */
    public boolean isContinued() {
        return continuation;
    }

    /**
     * @return true if transponder has Overlay Command Capability (OCC); false otherwise
     */
    public boolean hasOverlayCapability() {
        return overlay_command_capability;
    }

    /**
     * @return true if the transponder TCAS interface is operational and the transponder
     * is receiving TCAS RI=2, 3 or 4; false otherwise
     */
    public boolean hasOperationalTCAS() {
        return tcas_operational;
    }

    /**
     * <table>
     *   <tr>
     *     <th>Version<br>Number</th>
     *     <th>ICAO</th>
     *     <th>RTCA</th>
     *     <th>EUROCAE</th>
     *   </tr>
     *   <tr>
     *     <td>0</td>
     *     <td colspan="3">Sub-network not available</td>
     *   </tr>
     *   <tr>
     *     <td>1</td>
     *     <td>Doc 9688 (1996)</td>
     *     <td></td>
     *     <td></td>
     *   </tr>
     *   <tr>
     *     <td>2</td>
     *     <td>Doc 9688 (1998)</td>
     *     <td></td>
     *     <td></td>
     *   </tr>
     *   <tr>
     *     <td>3</td>
     *     <td>Annex 10, Vol. III, Amendment 77</td>
     *     <td></td>
     *     <td></td>
     *   </tr>
     *   <tr>
     *     <td>4</td>
     *     <td>Doc 9871 Edition 1</td>
     *     <td>DO-181D</td>
     *     <td>ED-73C</td>
     *   </tr>
     *   <tr>
     *     <td>5</td>
     *     <td>Doc 9871 Edition 2</td>
     *     <td>DO-181E</td>
     *     <td>ED-73E</td>
     *   </tr>
     *   <tr>
     *     <td>6-127</td>
     *     <td colspan="3">Reserved</td>
     *   </tr>
     * </table>
     *
     * @return the Mode-S Subnetwork Version Number (see table above)
     */
    public byte getModeSSubnetworkVersion() {
        return modes_subnetwork_version;
    }

    /**
     * @return true if Level 5 transponder; false if Level 2-4 transponder
     */
    public boolean hasEnhancedProtocolCapability() {
        return modes_enhanced_protocol;
    }

    /**
     * @return true if at least one Mode-S specific service (other than GICB services
     * related to registers 02, 03, 04, 10, 17 to 1C, 20 and 30) is supported and the
     * particular capability reports shall be checked.
     */
    public boolean hasModeSSpecificServicesCapability() {
        return modes_specific_services;
    }

    /**
     * 0 = No UELM Capability<br>
     * 1 = 16 UELM segments in 1 second<br>
     * 2 = 16 UELM segments in 500 ms<br>
     * 3 = 16 UELM segments in 250 ms<br>
     * 4 = 16 UELM segments in 125 ms<br>
     * 5 = 16 UELM segments in 60 ms<br>
     * 6 = 16 UELM segments in 30 ms<br>
     * 7 = Unassigned
     * @return the ELM uplink average throughput capability code (see mapping above)
     */
    public byte getAverageELMUplinkThroughputCapability() {
        return elm_uplink_throughput;
    }

    /**
     * 0 = No DELM Capability<br>
     * 1 = One 4 segment DELM every second<br>
     * 2 = One 8 segment DELM every second<br>
     * 3 = One 16 segment DELM every second<br>
     * 4 = One 16 segment DELM every 500 ms<br>
     * 5 = One 16 segment DELM every 250 ms<br>
     * 6 = One 16 segment DELM every 125 ms<br>
     * 7-15 = Unassigned
     * @return the ELM downlink throughput capability code (see mapping above)
     */
    public byte getELMDownlinkThroughputCapability() {
        return elm_downlink_throughput;
    }

    /**
     * @return true if aircraft identification data is available, i.e., the data comes to the transponder
     * through an interface separate from ADLP
     */
    public boolean hasAircraftIdentificationData() {
        return aircraft_identification_capability;
    }

    /**
     * @return if both Registers 05 and 06 have been updated within the last ten, plus or minus one, seconds
     */
    public boolean hasSquitterCapability() {
        return squitter_capability_subfield;
    }

    /**
     * @return if transponder has surveillance identifier code capability
     */
    public boolean hasSurveillanceIdentifierCodeCapability() {
        return surveillance_identifier_code;
    }

    /**
     * Indicates whether the GICB capability report has changed by toggling this flag.
     * @return alternates between true and false in approximately 1 minute intervals
     */
    public boolean getGICBChangeIndicator() {
        return common_usage_gicb_capability_report;
    }

    /**
     * @return true if TCAS has hybrid surveillance capability; false otherwise
     */
    public boolean hasHybridSurveillanceCapability() {
        return hybrid_surveillance_capability;
    }

    /**
     * @return true if TCAS can generate both TAs and RAs; false if only TA generation available
     */
    public boolean hasRACapability() {
        return ta_only;
    }

    /**
     * 0 = DO-185 (6.04A)<br>
     * 1 = DO-185A<br>
     * 2 = DO-185B<br>
     * 3 = Reserved
     * @return TCAS version code according to the above mapping
     */
    public byte getTCASVersionCode() {
        return tcas_version;
    }

    /**
     * @return 16-bit mask indicating the status of DTA addresses 0 (0x8000) to 15 (0x0001)
     */
    public int getDTESubaddressSupportMask() {
        return dte_subaddress_support_mask;
    }

    public String toString() {
        return super.toString() +
                "\nBDS1,0:" +
                String.format("\n\tContinued in BDS 1,1: %b", isContinued()) +
                String.format("\n\tOverlay command capability: %b", hasOverlayCapability()) +
                String.format("\n\tHas operational TCAS: %b", hasOperationalTCAS()) +
                String.format("\n\tMode S Subnetwork Verison: %d", getModeSSubnetworkVersion()) +
                String.format("\n\tSupports enhanced protocol: %b", hasEnhancedProtocolCapability()) +
                String.format("\n\tMode S specific services capability: %b", hasModeSSpecificServicesCapability()) +
                String.format("\n\tELM Uplink throughput capability: %d", getAverageELMUplinkThroughputCapability()) +
                String.format("\n\tELM Downlink throughput capability: %d", getELMDownlinkThroughputCapability()) +
                String.format("\n\tAircraft identification data available: %b", hasAircraftIdentificationData()) +
                String.format("\n\tSquitter capability: %b", hasSquitterCapability()) +
                String.format("\n\tSurveillance Identifier Code Capability: %b", hasSurveillanceIdentifierCodeCapability()) +
                String.format("\n\tGICB Change Indicator: %b", getGICBChangeIndicator()) +
                String.format("\n\tHas RA capability: %b", hasRACapability()) +
                String.format("\n\tTCAS Version Code: %d", getTCASVersionCode()) +
                String.format("\n\tDTE sub-address support mask: %04x", getDTESubaddressSupportMask());
    }

}
