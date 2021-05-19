package ork.sevenstates.apng;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.lang.reflect.Array;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.zip.CRC32;

public final class APNGSeqWriter
		implements Closeable
{

	private final Filter filter;

	private int frameCount = 0;
	private int sequenceNumber;
	private boolean closed = false;

	private final FileChannel out;
	private long actlBlockOffset = 0;

	public APNGSeqWriter(File f, Filter filter) throws FileNotFoundException {
		this.filter = filter;
		out = new RandomAccessFile(f, "rw").getChannel();
	}

	private void ensureOpen() throws IOException {
		if (closed) {
			throw new IOException("Stream closed");
		}
	}

	void writeImage(BufferedImage img) throws IOException {
		ensureOpen();
		if (img == null) {
			throw new IOException("Image is null");
		}

		int fpsNum = 1;
		int fpsDen = 10;
		writeImage(img, fpsNum, fpsDen);
	}

	//http://www.w3.org/TR/PNG/#11IHDR
	private ByteBuffer makeIHDRChunk(Dimension d, byte numPlanes, byte bitsPerPlane) {
		ByteBuffer bb = ByteBuffer.allocate(Consts.IHDR_TOTAL_LEN);
		bb.putInt(Consts.IHDR_DATA_LEN);
		bb.putInt(Consts.IHDR_SIG);
		bb.putInt(d.width);
		bb.putInt(d.height);
		bb.put(bitsPerPlane);
		bb.put(type(numPlanes));
		bb.put(Consts.ZERO); //compression
		bb.put(Consts.ZERO); //filter
		bb.put(Consts.ZERO); //interlace

		addChunkCRC(bb);

		bb.flip();

		return bb;
	}

	private byte type(byte numPlanes) {
		byte type = 0;
		switch (numPlanes) {      //rgb = 0x2, alpha = 0x4
			case 4:
				type |= 0x4;//falls
			case 3:
				type |= 0x2;
				break;
			case 2:
				type |= 0x4;
			default:
				break;
		}
		return type;
	}

	public void close() throws IOException {
		//IEND
		out.write(ByteBuffer.wrap(Consts.getIENDArr()));

		long point = out.position();

		//frame count
		out.position(actlBlockOffset);
		out.write(make_acTLChunk(frameCount, 0));

		closed = true;
		frameCount = 0;
		out.truncate(point);
		out.close();
	}

	private ByteBuffer make_acTLChunk(int frameCount, int loopCount) {
		ByteBuffer bb = ByteBuffer.wrap(Consts.getacTLArr());
		bb.position(8);
		bb.putInt(frameCount);
		bb.putInt(loopCount);
		addChunkCRC(bb);
		bb.flip();
		return bb;
	}

	private int crc(byte[] buf) {
		return crc(buf, 0, buf.length);
	}

	private int crc(byte[] buf, int off, int len) {
		CRC32 crc = new CRC32();
		crc.update(buf, off, len);
		return (int) crc.getValue();
	}

	private ByteBuffer makeFCTL(Rectangle r, int fpsNum, int fpsDen, boolean succ) {
		ByteBuffer bb = ByteBuffer.allocate(Consts.fcTL_TOTAL_LEN);

		bb.putInt(Consts.fcTL_DATA_LEN);
		bb.putInt(Consts.fcTL_SIG);

		byte one = 0x1;
		
		bb.putInt(sequenceNumber++);
		bb.putInt(r.width);
		bb.putInt(r.height);
		bb.putInt(r.x);
		bb.putInt(r.y);
		bb.putShort((short) fpsNum);
		bb.putShort((short) fpsDen);
		bb.put(Consts.ZERO);    	        //dispose 1:clear, 0: do nothing, 2: revert
		bb.put(succ ? one : Consts.ZERO);	//blend   1:blend, 0: source

		addChunkCRC(bb);

		bb.flip();
		
		return bb;
	}

	private ByteBuffer makeDAT(int sig, ByteBuffer buffer) {
		ByteBuffer compressed = ByteBufferCompressor.compress(buffer);

		boolean needSeqNum = sig == Consts.fdAT_SIG;

		int size = compressed.remaining();

		if (needSeqNum)
			size +=4;

		ByteBuffer bb = ByteBuffer.allocate(size + Consts.CHUNK_DELTA);

		bb.putInt(size);
		bb.putInt(sig);
		if (needSeqNum) {
			bb.putInt(sequenceNumber++);
		}
		bb.put(compressed);

		addChunkCRC(bb);

		bb.flip();
		return bb;
	}

	private void addChunkCRC(ByteBuffer chunkBuffer) {
		if (chunkBuffer.remaining() != 4)			//CRC32 size 4
			throw new IllegalArgumentException();

		int size = chunkBuffer.position() - 4;

		if (size <= 0)
			throw new IllegalArgumentException();

		chunkBuffer.position(4);			 //size not covered by CRC 
		byte[] bytes = new byte[size];     // CRC covers only this
		chunkBuffer.get(bytes);
		chunkBuffer.putInt(crc(bytes));
	}

	private ByteBuffer getPixelBytes(BufferedImage image, Dimension dim) {
		return new BufferedImageSerializer(filter).getPixelBytes(image,dim);
	}

	static Dimension dimsFromImage(BufferedImage bi) {
		return new Dimension(bi.getWidth(), bi.getHeight());
	}

	private void writeImage(BufferedImage img, int fpsNum, int fpsDen) throws IOException {
		ensureOpen();
		Dimension dim = dimsFromImage(img);
		Rectangle rect = new Rectangle(dim);

		if (frameCount == 0) {
			writeImageHeader(rect,img);
		}

		out.write(makeFCTL(rect, fpsNum, fpsDen, frameCount != 0));
		out.write(makeDAT(frameCount == 0 ? Consts.IDAT_SIG : Consts.fdAT_SIG, getPixelBytes(img, dim)));
		frameCount++;
	}

	private void writeImageHeader(Rectangle key, BufferedImage value) throws IOException {
		out.write(ByteBuffer.wrap(Consts.getPNGSIGArr()));
		byte bitsPerPlane = 8;
		out.write(makeIHDRChunk(key.getSize(), numPlanes(value), bitsPerPlane));

		actlBlockOffset = out.position();
		out.write(ByteBuffer.wrap(Consts.getacTLArr())); // empty here, filled later
	}

	private byte numPlanes(BufferedImage value) {
		return (byte) value.getRaster().getNumBands();
	}

}