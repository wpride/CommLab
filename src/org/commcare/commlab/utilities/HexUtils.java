package org.commcare.commlab.utilities;

public class HexUtils {

	// TODO(damonkohler): Possibly move this to some common place?
	public static String byteArrayToHexString(byte[] data) {
		if (data == null) {
			return "null";
		}
		if (data.length == 0) {
			return "empty";
		}
		StringBuilder out = new StringBuilder(data.length * 5);
		for (byte b : data) {
			out.append(String.format("%02x", b));
		}
		return out.toString();
	}

}
