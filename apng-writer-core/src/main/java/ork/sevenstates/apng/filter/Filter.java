package ork.sevenstates.apng.filter;

import java.nio.ByteBuffer;

public final class Filter {

	private final int width;
	private final int height;
	private final int bpp;
	private static final byte INDEX = 0;

	public Filter(int width, int height, int bpp) {
		this.width = width;
		this.height = height;
		this.bpp = bpp;
	}

	public void encode(ByteBuffer in, ByteBuffer out) {
		checkSize(in, out);
		int byteRowLen = width * bpp;
		for (int y = 0; y < height; y++) {
			int yoffset = y * byteRowLen;
			encodeRow(in, yoffset, out, byteRowLen, yoffset + y);
		}
	}

	private void encodeRow(ByteBuffer in, int srcOffset, ByteBuffer out, int len, int destOffset) {
		out.put(destOffset++, INDEX);
		int bpl = width * bpp;
		ByteBuffer tmp = in.duplicate();
		tmp.position(srcOffset).limit(srcOffset + bpl);
		out.position(destOffset);
		out.put(tmp);
	}

	private void checkSize(ByteBuffer in, ByteBuffer out) {
		if (out.capacity() != (width * bpp + 1) * height) {
			String message = "Invalid output buffer capacity: capacity != (width*bpp+1)*height, " +
					out.capacity() + "!=" + (width * bpp + 1) * height;
			throw new IllegalArgumentException(message);
		}
		if (in.remaining() != width * height * bpp) {
			String message = "Invalid input buffer capacity: capacity != width*bpp*height, "
					+ in.capacity() + "!=" + width * bpp * height;
			throw new IllegalArgumentException(message);
		}
	}

}
