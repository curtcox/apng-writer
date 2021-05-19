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
		bb.put(type);
		bb.put(Consts.ZERO); //compression
		bb.put(Consts.ZERO); //filter
		bb.put(Consts.ZERO); //interlace

		addChunkCRC(bb);

		bb.flip();

		return bb;
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

	static Dimension dimsFromImage(BufferedImage bi) {
		return new Dimension(bi.getWidth(), bi.getHeight());
	}

	private void writeImage(BufferedImage img, int fpsNum, int fpsDen) throws IOException {
		ensureOpen();
		Dimension dim = dimsFromImage(img);
		Rectangle key = new Rectangle(dim);
		ByteBuffer buffer = getPixelBytes(img, dim);

		if (frameCount == 0) {
			writeImageHeader(key,img);
		}

		out.write(makeFCTL(key, fpsNum, fpsDen, frameCount != 0));
		out.write(makeDAT(frameCount == 0 ? Consts.IDAT_SIG : Consts.fdAT_SIG, buffer));
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