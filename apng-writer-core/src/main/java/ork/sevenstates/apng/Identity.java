package ork.sevenstates.apng;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;

public final class Identity {

    public BufferedImage createImage(Dimension d) {
        return new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
    }

    public Map.Entry<Rectangle, BufferedImage> processImage(BufferedImage from) {
        return Tools.formatResult(from, Tools.dimsFromImage(from));
    }
}
