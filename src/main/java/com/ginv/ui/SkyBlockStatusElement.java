package com.ginv.ui;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UIElementRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import dev.vfyjxf.taffy.style.TaffyPosition;

/**
 * Top-bar chip reporting the live Hypixel SkyBlock verdict.
 *
 * <p>The whole look is stylesheet-driven (office.lss): the chip surface comes
 * from {@code .ginv-skyblock} and the state carries as the {@code .ginv-sky-on}
 * / {@code .ginv-sky-off} class pair, which also recolors the internal label.
 * The label child is the fallback view — if no renderer is registered, the
 * default renderer still draws the chip background plus that label.
 *
 * <p>The state dot is an out-of-flow {@code .ginv-dot} child that exists only
 * as a style carrier: office.lss makes it {@code position:absolute} at 0×0 so
 * it never consumes the chip's {@code gap}, paints it {@code .ginv-dot-on} /
 * {@code .ginv-dot-off}, and the registered {@link Renderer} reads that
 * sheet-resolved background and draws it in the chip's left padding gutter,
 * centered between the element's left edge and the label. No color or size
 * constant lives in Java.
 */
public class SkyBlockStatusElement extends UIElement {
    public static final String ID = "ginv_skyblock_status";
    public static final String CLASS_BASE = "ginv-skyblock";
    public static final String CLASS_ON = "ginv-sky-on";
    public static final String CLASS_OFF = "ginv-sky-off";
    public static final String CLASS_DOT = "ginv-dot";
    public static final String CLASS_DOT_ON = "ginv-dot-on";
    public static final String CLASS_DOT_OFF = "ginv-dot-off";

    private final Label stateLabel;
    private final UIElement dot;

    public SkyBlockStatusElement() {
        setId(ID);
        addClass(CLASS_BASE);
        addClass(CLASS_OFF);
        // Zero-size style carrier — the renderer draws it (see class doc).
        // Kept laid out (not display:off) so its sheet style always resolves.
        dot = new UIElement();
        dot.addClass(CLASS_DOT);
        dot.addClass(CLASS_DOT_OFF);
        dot.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(0);
            layout.width(0);
            layout.height(0);
        });
        stateLabel = new Label();
        stateLabel.setText("SkyBlock OFF", false);
        // Measure the label: a Label's default width is 0 (adaptive-width off),
        // so the pill collapsed to its left padding and the text painted out
        // under the View button. Same setup Button gives its own text.
        stateLabel.textStyle(style -> style.adaptiveWidth(true));
        addChildren(dot, stateLabel);
    }

    /** The internal fallback label; the screen writes the ON/OFF text into it. */
    public Label getStateLabel() {
        return stateLabel;
    }

    /** The hidden style carrier for the state dot (office.lss paints it). */
    public UIElement getDot() {
        return dot;
    }

    /** True when the {@code .ginv-sky-on} state class is applied. */
    public boolean isSkyOn() {
        return hasClass(CLASS_ON);
    }

    /**
     * Flips the state class pair and the label text. The screen may also flip
     * the classes itself on tick; both paths stay in sync through this call.
     */
    public void setSkyOn(boolean skyOn) {
        if (skyOn == isSkyOn()) {
            return;
        }
        if (skyOn) {
            addClass(CLASS_ON);
            removeClass(CLASS_OFF);
        } else {
            addClass(CLASS_OFF);
            removeClass(CLASS_ON);
        }
        dot.removeClasses(CLASS_DOT_ON, CLASS_DOT_OFF);
        dot.addClass(skyOn ? CLASS_DOT_ON : CLASS_DOT_OFF);
        stateLabel.setText(skyOn ? "SkyBlock ON" : "SkyBlock OFF", false);
    }

    /** Draws the sheet-painted state dot in the chip's left gutter. */
    public static final class Renderer implements UIElementRenderer<SkyBlockStatusElement> {
        @Override
        public void drawBackgroundAdditional(SkyBlockStatusElement element, IGUIContext context) {
            float left = element.getPositionX();
            float height = element.getSizeHeight();
            float gutter = element.getStateLabel().getPositionX() - left;
            if (gutter <= 1f || height <= 1f) {
                return;
            }
            float size = Math.min(height * 0.45f, gutter * 0.6f);
            float dotX = left + (gutter - size) * 0.5f;
            float dotY = element.getPositionY() + (height - size) * 0.5f;
            IGuiTexture dotTexture = element.getDot().getStyle().backgroundTexture();
            context.drawTexture(dotTexture, dotX, dotY, size, size);
        }
    }
}
