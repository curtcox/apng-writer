package ork.sevenstates.apng.filter;

import java.nio.ByteBuffer;

public abstract class AbstractFilter implements Filter {

	final int width;
	private final int height;
	private final int bpp;

	public AbstractFilter(int width, int height, int bpp) {
		this.width = width;
		this.height = height;
		this.bpp = bpp;
	}

	public int getBpp() {
		return bpp;
	}

	public void close() {}

	public void encode(ByteBuffer in, ByteBuffer out) {
		checkSize(in, out);
		int byteRowLen = width * bpp;
		for (int y = 0; y < height; y++) {
			int yoffset = y * byteRowLen;
			encodeRow(in, yoffset, out, byteRowLen, yoffset + y);
		}
	}

	public abstract void encodeRow(ByteBuffer in, int srcOffset, ByteBuffer out, int len, int destOffset);

	public void checkSize(ByteBuffer in, ByteBuffer out) {
		if (out.capacity() != (width * bpp + 1) * height) {
			throw new IllegalArgumentException("Invalid output buffer capacity: capacity != (width*bpp+1)*height, "
																+ out.capacity() + "!=" + (width * bpp + 1) * height);
		}
		if (in.remaining() != width * height * bpp) {
			throw new IllegalArgumentException("Invalid input buffer capacity: capacity != width*bpp*height, "
					+ in.capacity() + "!=" + width * bpp * height);
		}
	}

}
