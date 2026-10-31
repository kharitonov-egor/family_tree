package com.egakh.familytree.client.screen;

import com.egakh.familytree.client.screen.PanelPlacement.Box;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PanelPlacementTest {
    private static final Box CANVAS = new Box(0, 106, 800, 468);

    @Test
    void panelStaysBottomLeftWhenNothingIsUnderIt() {
        assertEquals(new Box(6, 402, 206, 462), PanelPlacement.place(CANVAS, 200, 60, 6, List.of(new Box(300, 150, 500, 220))));
    }

    @Test
    void panelMovesOffTheSelectedFoalsCard() {
        Box foal = new Box(155, 317, 360, 410);
        Box panel = PanelPlacement.place(CANVAS, 245, 60, 6, List.of(foal));
        assertEquals(0, panel.overlap(foal));
        assertEquals(new Box(549, 402, 794, 462), panel);
    }

    @Test
    void panelGoesToTheTopWhenBothBottomCornersHoldComparedCards() {
        Box selected = new Box(20, 380, 220, 440), compared = new Box(600, 380, 790, 440);
        Box panel = PanelPlacement.place(CANVAS, 200, 60, 6, List.of(selected, compared));
        assertEquals(new Box(6, 112, 206, 172), panel);
    }

    @Test
    void aCardCoveringTheWholeCanvasGetsTheLeastCoveredCorner() {
        Box huge = new Box(100, 106, 800, 468);
        Box panel = PanelPlacement.place(CANVAS, 200, 60, 6, List.of(huge));
        assertEquals(6, panel.left());
        assertTrue(panel.overlap(huge) < 200 * 60);
    }

    @Test
    void tallPanelsStayInsideTheCanvas() {
        Box panel = PanelPlacement.place(CANVAS, 200, 900, 6, List.of());
        assertEquals(112, panel.top());
        assertEquals(462, panel.bottom());
    }
}
