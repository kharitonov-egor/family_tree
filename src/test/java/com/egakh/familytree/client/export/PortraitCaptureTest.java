package com.egakh.familytree.client.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PortraitCaptureTest {
    @Test void backgroundBecomesTransparentAndOpaqueModelPixelsKeepTheirColor() {
        assertEquals(0, PortraitCapture.transparentPixel(0xFF000000, 0xFFFFFFFF));
        assertEquals(0xFF336699, PortraitCapture.transparentPixel(0xFF336699, 0xFF336699));
    }

    @Test void translucentPixelsSuchAsBeeWingsKeepPartialAlpha() {
        // A white wing at half opacity reads 0x80 over black and 0xFF over white.
        int wing = PortraitCapture.transparentPixel(0xFF808080, 0xFFFFFFFF);
        assertEquals(128, wing >>> 24, 1);
        assertEquals(0xFF, wing & 0xFF, 1);
        assertEquals(0xFF, (wing >> 16) & 0xFF, 1);
    }
}
