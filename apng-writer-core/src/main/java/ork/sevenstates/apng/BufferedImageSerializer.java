package ork.sevenstates.apng;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

final class BufferedImageSerializer {

    private final Filter filter;

    BufferedImageSerializer(Filter filter) {
        this.filter = filter;
    }

    ByteBuffer getPixelBytes(BufferedImage image, Dimension dim) {
        WritableRaster raster = image.getRaster();
        int numBands = raster.getNumBands();
        Object dataElements = raster.getDataElements(0, 0, dim.width, dim.height, null);
        int length = Array.getLength(dataElements);

        ByteBuffer tmp = ByteBuffer.allocate(length * numBands + 1);

        int[] ints = (int[]) dataElements;
        if (numBands == 4) {
            IntBuffer intBuffer = tmp.asIntBuffer();
            for (int i = 0; i < length; i++) {
                int e = ints[i];
                int a = (e & 0xff000000) >>> 24;
                intBuffer.put(e << 8 | a);
            }
        } else {
            int index = 0;
            for (int i = 0; i < length; i++) {
                int e = ints[i];
                tmp.putInt(index, e << 8);
                index += 3;
            }
        }

        tmp.position(0);
        tmp.limit(tmp.limit() - 1);
        ByteBuffer result = ByteBuffer.allocate(length * numBands + dim.height);

        if (dim.width == 1 && dim.height == 1) {
            result.put(Consts.ZERO);
            result.put(tmp);
            result.flip();
            return result;
        }

        filter.encode(tmp, result);

        result.position(0);

        return result;
    }

}
