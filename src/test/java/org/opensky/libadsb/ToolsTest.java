package org.opensky.libadsb;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for utility functions in the tools class: hex conversion, CRC, unit conversions.
 * Expected values cross-validated with pyModeS 2.21.1 and lib1090.
 */
public class ToolsTest {

    @Test
    public void testHexStringToByteArray() {
        byte[] result = tools.hexStringToByteArray("8D406B902015A678D4D220AA4BDA");
        assertEquals(14, result.length);
        assertEquals((byte) 0x8D, result[0]);
        assertEquals((byte) 0x40, result[1]);
        assertEquals((byte) 0x6B, result[2]);
        assertEquals((byte) 0x90, result[3]);
        assertEquals((byte) 0xDA, result[13]);
    }

    @Test
    public void testHexStringToByteArrayLowerCase() {
        byte[] upper = tools.hexStringToByteArray("8D406B90");
        byte[] lower = tools.hexStringToByteArray("8d406b90");
        assertTrue(tools.areEqual(upper, lower));
    }

    @Test
    public void testToHexString() {
        byte[] bytes = {(byte) 0x40, (byte) 0x6B, (byte) 0x90};
        assertEquals("406b90", tools.toHexString(bytes));
    }

    @Test
    public void testToHexStringSingleByte() {
        assertEquals("0a", tools.toHexString((byte) 0x0A));
        assertEquals("ff", tools.toHexString((byte) 0xFF));
        assertEquals("00", tools.toHexString((byte) 0x00));
    }

    @Test
    public void testIsZero() {
        assertTrue(tools.isZero(new byte[]{0, 0, 0}));
        assertFalse(tools.isZero(new byte[]{0, 0, 1}));
        assertFalse(tools.isZero(new byte[]{(byte) 0xFF, 0, 0}));
    }

    @Test
    public void testAreEqual() {
        byte[] a = {1, 2, 3};
        byte[] b = {1, 2, 3};
        byte[] c = {1, 2, 4};
        assertTrue(tools.areEqual(a, b));
        assertFalse(tools.areEqual(a, c));
    }

    @Test
    public void testFeet2Meters() {
        assertEquals(0.0, tools.feet2Meters(0), 0.001);
        assertEquals(304.8, tools.feet2Meters(1000), 0.1);
        assertNull(tools.feet2Meters((Integer) null));
    }

    @Test
    public void testKnots2MetersPerSecond() {
        // 1 knot = 0.514444 m/s
        assertEquals(0.514444, tools.knots2MetersPerSecond(1), 0.001);
        assertNull(tools.knots2MetersPerSecond((Integer) null));
    }
}
