package ork.sevenstates.apng.filter;

import java.nio.ByteBuffer;

public class None extends AbstractFilter {

	private static final byte INDEX = 0;

	public None(int width, int height, int bpp) {
		super(width, height, bpp);
	}

	@Override
	public void encodeRow(ByteBuffer in, int srcOffset, ByteBuffer out, int len, int destOffset) {
		out.put(destOffset++, INDEX);
		int bpl = width * getBpp();
		ByteBuffer tmp = in.duplicate();
		tmp.position(srcOffset).limit(srcOffset + bpl);
		out.position(destOffset);
		out.put(tmp);
	}

}
