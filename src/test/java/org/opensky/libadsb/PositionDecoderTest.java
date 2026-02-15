package org.opensky.libadsb;

import org.junit.Test;
import org.opensky.libadsb.msgs.*;

import static org.junit.Assert.*;

/**
 * Tests for the stateful PositionDecoder: CPR global/local decode, speed thresholds.
 *
 * Bug #9: After a failed decode (e.g., PositionStraddleError in global decode),
 *         last_pos is set to null, preventing subsequent local decodes from working.
 *         The fix: don't reset last_pos on decode failure.
 */
public class PositionDecoderTest {

    @Test
    public void testStatefulGlobalDecode() throws Exception {
        PositionDecoder decoder = new PositionDecoder();

        // Feed even frame first
        AirbornePositionV0Msg even = (AirbornePositionV0Msg) Decoder.genericDecoder("8D40058B58C901375147EFD09357");
        Position pos1 = decoder.decodePosition(0.0, even);
        // First message alone may or may not produce a position

        // Feed odd frame
        AirbornePositionV0Msg odd = (AirbornePositionV0Msg) Decoder.genericDecoder("8D40058B58C904A87F402D3B8C59");
        Position pos2 = decoder.decodePosition(1.0, odd);

        // After receiving both even and odd frames, should have a valid position
        assertNotNull("Should decode position after receiving even+odd pair", pos2);
        assertEquals(49.82, pos2.getLatitude(), 0.02);
        assertEquals(6.08, pos2.getLongitude(), 0.02);
    }

    @Test
    public void testStatefulLocalDecodeAfterGlobal() throws Exception {
        PositionDecoder decoder = new PositionDecoder();

        // Establish position with even+odd pair
        AirbornePositionV0Msg even = (AirbornePositionV0Msg) Decoder.genericDecoder("8D40058B58C901375147EFD09357");
        decoder.decodePosition(0.0, even);

        AirbornePositionV0Msg odd = (AirbornePositionV0Msg) Decoder.genericDecoder("8D40058B58C904A87F402D3B8C59");
        Position globalPos = decoder.decodePosition(1.0, odd);
        assertNotNull(globalPos);

        // Feed another even frame - should use local decode
        Position localPos = decoder.decodePosition(2.0, even);
        assertNotNull("Local decode should succeed after global decode established position", localPos);
        // Position should be close to the global decode result
        assertEquals(globalPos.getLatitude(), localPos.getLatitude(), 0.05);
        assertEquals(globalPos.getLongitude(), localPos.getLongitude(), 0.05);
    }

    @Test
    public void testDecodeWithReceiverPosition() throws Exception {
        PositionDecoder decoder = new PositionDecoder();
        Position receiver = new Position(6.0, 49.0, 0.0);

        AirbornePositionV0Msg even = (AirbornePositionV0Msg) Decoder.genericDecoder("8D40058B58C901375147EFD09357");
        decoder.decodePosition(0.0, receiver, even);

        AirbornePositionV0Msg odd = (AirbornePositionV0Msg) Decoder.genericDecoder("8D40058B58C904A87F402D3B8C59");
        Position pos = decoder.decodePosition(1.0, receiver, odd);

        assertNotNull(pos);
        assertEquals(49.82, pos.getLatitude(), 0.02);
    }

    @Test
    public void testSpeedThreshold() {
        // withinThreshold checks if distance is realistic for time difference
        // At aircraft speed (~250 m/s = 900 km/h), 10 seconds = 2500m max

        // 1000m in 10s = 100 m/s - should be within threshold for airborne
        assertTrue(PositionDecoder.withinThreshold(10.0, 1000.0, false));

        // 100000m in 10s = 10000 m/s - way too fast
        assertFalse(PositionDecoder.withinThreshold(10.0, 100000.0, false));
    }

    @Test
    public void testWithinReasonableRange() {
        // Receiver at (0, 0), sender at (6, 49) - well within 700km
        Position receiver = new Position(0.0, 0.0, 0.0);
        Position sender = new Position(6.0, 49.0, 39000.0);
        // This is about 5500 km - should be OUT of range
        assertFalse(PositionDecoder.withinReasonableRange(receiver, sender));

        // Receiver at (6, 49), sender at (6.08, 49.82) - very close
        Position nearReceiver = new Position(6.0, 49.0, 0.0);
        Position nearSender = new Position(6.08, 49.82, 39000.0);
        assertTrue(PositionDecoder.withinReasonableRange(nearReceiver, nearSender));
    }

    @Test
    public void testSecondPairGlobalDecode() throws Exception {
        // Test the second CPR pair using direct global decode
        // pyModeS: lat=42.34736, lon=0.434982
        AirbornePositionV0Msg odd = (AirbornePositionV0Msg) Decoder.genericDecoder("8d4d224f58bf07c2d41a9a353d70");
        AirbornePositionV0Msg even = (AirbornePositionV0Msg) Decoder.genericDecoder("8d4d224f58bf003b221b34aa5b8d");
        assertTrue(odd.isOddFormat());
        assertFalse(even.isOddFormat());

        // Use direct global decode instead of stateful decoder
        try {
            Position pos = even.getGlobalPosition(odd);
            assertNotNull("Global position decode should succeed", pos);
            assertEquals(42.35, pos.getLatitude(), 0.02);
            assertEquals(0.43, pos.getLongitude(), 0.06);
        } catch (Exception e) {
            // May fail due to Bug #4 (NL function) or Bug #5 (longitude normalization)
            // This is an expected failure until those bugs are fixed
            fail("Global decode failed (may be Bug #4/#5): " + e.getMessage());
        }
    }
}
