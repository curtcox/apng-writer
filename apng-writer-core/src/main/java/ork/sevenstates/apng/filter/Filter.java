package ork.sevenstates.apng.filter;

import java.nio.ByteBuffer;

public interface Filter {

	void close();

	void encode(ByteBuffer in, ByteBuffer out);
	void encodeRow(ByteBuffer in, int srcOffset, ByteBuffer out, int len, int destOffset);

}
