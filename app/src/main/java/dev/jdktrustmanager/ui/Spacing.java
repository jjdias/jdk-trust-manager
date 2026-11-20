package dev.jdktrustmanager.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;

/** Shared padding and gaps so every screen has the same breathing room. */
final class Spacing {

    /** Gap between neighbouring controls and between stacked rows. */
    static final double GAP = 12;

    /** Space between a page's edge and its content. */
    static final Insets PAGE = new Insets(16);

    private Spacing() {
    }

    /** Pads a page and separates its top and side blocks from the centre. */
    static <T extends Region> T page(T region) {
        region.setPadding(PAGE);
        if (region instanceof BorderPane border) {
            margin(border.getTop(), new Insets(0, 0, GAP, 0));
            margin(border.getRight(), new Insets(0, 0, 0, GAP));
            margin(border.getBottom(), new Insets(GAP, 0, 0, 0));
        }
        return region;
    }

    private static void margin(Node node, Insets insets) {
        if (node != null) {
            BorderPane.setMargin(node, insets);
        }
    }
}
