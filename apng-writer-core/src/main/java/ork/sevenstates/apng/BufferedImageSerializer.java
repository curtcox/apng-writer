package ork.sevenstates.apng;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

final class BufferedImageSerializer {

    private final BufferedImage image;
    private final Dimension dim;
    private final Encoder filter;

    BufferedImageSerializer(BufferedImage image, Dimension dim, Encoder filter) {
        this.image = image;
        this.dim = dim;
        this.filter = filter;
    }

    ByteBuffer getPixelBytes() {
        final WritableRaster raster = image.getRaster();
        final int          numBands = raster.getNumBands();
        final int[]    dataElements = (int[]) raster.getDataElements(0, 0, dim.width, dim.height, null);
        final int            length = Array.getLength(dataElements);
        ByteBuffer           buffer = writeToBuffer(dataElements,numBands,length);
        return encoded(buffer,numBands,length);
    }

    private ByteBuffer writeToBuffer(int[] dataElements, int numBands, int length) {
        ByteBuffer out = ByteBuffer.allocate(length * numBands + 1);

        if (numBands == 4) {
            IntBuffer intBuffer = out.asIntBuffer();
            for (int i = 0; i < length; i++) {
                final int e = dataElements[i];
                final int a = (e & 0xff000000) >>> 24;
                intBuffer.put(e << 8 | a);
            }
        } else {
            int index = 0;
            for (int i = 0; i < length; i++) {
                final int e = dataElements[i];
                out.putInt(index, e << 8);
                index += 3;
            }
        }

        out.position(0);
        out.limit(out.limit() - 1);
        return out;
    }

    private ByteBuffer encoded(ByteBuffer tmp,int numBands,int length) {
        ByteBuffer result = ByteBuffer.allocate(length * numBands + dim.height);
        filter.encode(tmp, result);
        result.position(0);
        return result;
    }

}
