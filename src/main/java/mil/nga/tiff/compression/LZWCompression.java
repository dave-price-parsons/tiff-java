package mil.nga.tiff.compression;

import java.io.ByteArrayOutputStream;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

import mil.nga.tiff.io.ByteReader;
import mil.nga.tiff.util.TiffException;

/**
 * LZW Compression
 * 
 * @author osbornb
 */
public class LZWCompression implements CompressionDecoder, CompressionEncoder {

	/**
	 * Logger
	 */
	private static final Logger logger = Logger.getLogger(LZWCompression.class
			.getName());

	/**
	 * Clear code
	 */
	private static final int CLEAR_CODE = 256;

	/**
	 * End of information code
	 */
	private static final int EOI_CODE = 257;

	/**
	 * Min bits
	 */
	private static final int MIN_BITS = 9;

	/**
	 * Max bits
	 */
	private static final int MAX_BITS = 12;

	/**
	 * Table entries
	 */
	private byte[][] table;

	/**
	 * Current max table code
	 */
	private int maxCode;

	/**
	 * Current byte length
	 */
	private int byteLength;

	/**
	 * Current byte compression position
	 */
	private int position;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] decode(byte[] bytes, ByteOrder byteOrder) {

		// Create the byte reader and decoded stream to write to
		ByteReader reader = new ByteReader(bytes, byteOrder);
		ByteArrayOutputStream decodedStream = new ByteArrayOutputStream();

		// Initialize the table, starting position, and old code
		initializeTable();
		position = 0;
		int oldCode = 0;

		// Read codes until end of input
		int code = getNextCode(reader);
		while (code != EOI_CODE) {

			// If a clear code
			if (code == CLEAR_CODE) {

				// Reset the table
				initializeTable();

				// Read past clear codes
				code = getNextCode(reader);
				while (code == CLEAR_CODE) {
					code = getNextCode(reader);
				}
				if (code == EOI_CODE) {
					break;
				}
				if (code > CLEAR_CODE) {
					throw new TiffException("Corrupted code at scan line: "
							+ code);
				}

				// Write the code value
				byte[] value = table[code];
				decodedStream.writeBytes(value);
				oldCode = code;

			} else {

				// If already in the table
			    byte[] value = table[code];
				if (value != null) {

					// Write the code value
				    decodedStream.writeBytes(value);

					// Create new value and add to table
				    byte[] newValue = combine(table[oldCode], value);
					addToTable(newValue);
					oldCode = code;

				} else {

					// Create and write new value from old value
					byte[] oldValue = table[oldCode];
					byte[] newValue = combine(oldValue, oldValue);
					decodedStream.writeBytes(newValue);

					// Write value to the table
					addToTable(newValue);
					oldCode = code;
				}
			}

			// Get the next code
			code = getNextCode(reader);
		}

		byte[] decoded = decodedStream.toByteArray();

		return decoded;
	}

	/**
	 * Initialize the table and byte length
	 */
	private void initializeTable() {
        table = new byte[ 2<<(MAX_BITS-1) ][];//size is 4096
		for (int i = 0; i < 256; i++) {
			table[i] = new byte[]{ (byte)i };
		}
		maxCode = 257;
		byteLength = MIN_BITS;
	}

	/**
	 * Check the byte length and increase if needed
	 */
	private void checkByteLength() {
		if (byteLength < MAX_BITS && maxCode >= Math.pow(2, byteLength) - 2) {
			byteLength++;
		}
	}

	/**
	 * Add the value to the table
	 * 
	 * @param value
	 *            value
	 */
	private void addToTable(byte[] value) {
	    table[++maxCode] = value;
		checkByteLength();
	}

	/**
	 * Combines the two values such that the result is the concatenation of the first value and the first element in the second value.
	 * 
	 * @param first
	 *            first value
	 * @param second
	 *            second value
	 * @return concatenated value
	 */
	private byte[] combine(byte[] first, byte[] second) {
	    byte[] combined = new byte[first.length + 1];
		System.arraycopy(first, 0, combined, 0, first.length);
		combined[first.length] = second[0];
		return combined;
	}

	/**
	 * Get the next code
	 * 
	 * @param reader
	 *            byte reader
	 * @return code
	 */
	private int getNextCode(ByteReader reader) {
		int nextByte = getByte(reader);
		position += byteLength;
		return nextByte;
	}

	/**
	 * Get the next byte
	 * 
	 * @param reader
	 *            byte reader
	 * @return byte
	 */
	private int getByte(ByteReader reader) {

		int d = position % 8;
		int a = (int) Math.floor(position / 8.0);
		int de = 8 - d;
		int ef = (position + byteLength) - ((a + 1) * 8);
		int fg = 8 * (a + 2) - (position + byteLength);
		int dg = (a + 2) * 8 - position;
		fg = Math.max(0, fg);
		if (a >= reader.byteLength()) {
			logger.log(Level.WARNING,
					"End of data reached without an end of input code");
			return EOI_CODE;
		}
		int chunk1 = ((int) reader.readUnsignedByte(a))
				& ((int) (Math.pow(2, 8 - d) - 1));
		chunk1 = chunk1 << (byteLength - de);
		int chunks = chunk1;
		if (a + 1 < reader.byteLength()) {
			int chunk2 = reader.readUnsignedByte(a + 1) >>> fg;
			chunk2 = chunk2 << Math.max(0, byteLength - dg);
			chunks += chunk2;
		}
		if (ef > 8 && a + 2 < reader.byteLength()) {
			int hi = (a + 3) * 8 - (position + byteLength);
			int chunk3 = reader.readUnsignedByte(a + 2) >>> hi;
			chunks += chunk3;
		}
		return chunks;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean rowEncoding() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] encode(byte[] bytes, ByteOrder byteOrder) {
		throw new TiffException("LZW encoder is not yet implemented");
	}

}
