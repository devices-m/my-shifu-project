import java.nio.charset.StandardCharsets;

class HexUtil {
    static byte[] hexToBytes(String s) {
        String str = s.trim();
        if ((str.length() & 1) != 0) throw new IllegalArgumentException("Odd hex length: " + str.length());
        int len = str.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            int hi = Character.digit(str.charAt(i*2), 16);
            int lo = Character.digit(str.charAt(i*2+1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("Invalid hex at position " + i);
            out[i] = (byte)((hi << 4) + lo);
        }
        return out;
    }

    static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte v : b) {
            sb.append(Character.forDigit((v >> 4) & 0xF, 16));
            sb.append(Character.forDigit(v & 0xF, 16));
        }
        return sb.toString().toUpperCase();
    }

    static String sanitizeToHexUpper(String ascii) {
        StringBuilder sb = new StringBuilder(ascii.length());
        for (int i = 0; i < ascii.length(); i++) {
            char c = ascii.charAt(i);
            if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f')) {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }
}
