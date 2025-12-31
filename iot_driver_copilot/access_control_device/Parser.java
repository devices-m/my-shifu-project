import java.util.*;

class Parser {
    static ParsedMessage parseMessage(String fullMsgHex, Config cfg) {
        byte[] allBytes = HexUtil.hexToBytes(fullMsgHex);
        int headerLen = cfg.headerLenBytes;
        byte[] header = Arrays.copyOfRange(allBytes, 0, headerLen);
        byte[] data = Arrays.copyOfRange(allBytes, headerLen, allBytes.length - cfg.crcLengthBytes);
        byte[] crcTail = Arrays.copyOfRange(allBytes, allBytes.length - cfg.crcLengthBytes, allBytes.length);

        String deviceId = extractFieldHex(header, cfg.deviceIdOffsetBytes, cfg.deviceIdLenBytes);
        String msgTypeHex = extractFieldHex(header, cfg.msgTypeOffsetBytes, cfg.msgTypeLenBytes);

        boolean crcValid = true;
        if (cfg.crcEnable) {
            int expected = toInt(crcTail, cfg.crcTailEndian);
            int calc = crc16Modbus(allBytes, 0, allBytes.length - cfg.crcLengthBytes);
            crcValid = (expected & 0xFFFF) == (calc & 0xFFFF);
        }

        EventType type = classify(msgTypeHex, cfg);

        String parsedDataJson = "{\"rawDataHex\":\"" + JsonUtil.escape(data.length == 0 ? "" : HexUtil.bytesToHex(data)) + "\",\"messageTypeHex\":\"" + JsonUtil.escape(msgTypeHex) + "\"}";

        return new ParsedMessage(deviceId, msgTypeHex, HexUtil.bytesToHex(data), crcValid, type);
    }

    static int computeTotalMessageBytes(String bufHex, Config cfg) {
        // bufHex starts at the beginning of a message. Must have at least header.
        String headerHex = bufHex.substring(0, cfg.headerLenBytes * 2);
        byte[] headerBytes = HexUtil.hexToBytes(headerHex);
        int dataLen = readDataLen(headerBytes, cfg);
        if (dataLen < 0) return -1;
        return cfg.headerLenBytes + dataLen + cfg.crcLengthBytes;
    }

    private static String extractFieldHex(byte[] header, int offsetBytes, int lenBytes) {
        byte[] field = Arrays.copyOfRange(header, offsetBytes, offsetBytes + lenBytes);
        return HexUtil.bytesToHex(field);
    }

    private static int readDataLen(byte[] header, Config cfg) {
        byte[] field = Arrays.copyOfRange(header, cfg.dataLenOffsetBytes, cfg.dataLenOffsetBytes + cfg.dataLenLenBytes);
        return toInt(field, cfg.dataLenEndian);
    }

    private static int toInt(byte[] bytes, Config.Endian endian) {
        int val = 0;
        if (endian == Config.Endian.BIG) {
            for (byte b : bytes) {
                val = (val << 8) | (b & 0xFF);
            }
        } else {
            for (int i = 0; i < bytes.length; i++) {
                val |= (bytes[i] & 0xFF) << (8 * i);
            }
        }
        return val;
    }

    private static EventType classify(String msgTypeHex, Config cfg) {
        String t = msgTypeHex.toUpperCase(Locale.ROOT);
        if (cfg.typeOnlineValues.contains(t)) return EventType.ONLINE;
        if (cfg.typeHeartbeatValues.contains(t)) return EventType.HEARTBEAT;
        if (cfg.typeBusinessValues.contains(t)) return EventType.BUSINESS;
        return EventType.UNKNOWN;
    }

    static int crc16Modbus(byte[] data, int off, int len) {
        int crc = 0xFFFF;
        for (int i = off; i < off + len; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc = (crc >>> 1);
                }
            }
        }
        return crc & 0xFFFF;
    }
}
