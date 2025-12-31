import java.util.*;

class Config {
    enum Endian { BIG, LITTLE }

    public final String httpHost;
    public final int httpPort;
    public final String tcpHost;
    public final int tcpPort;
    public final int readTimeoutMs;
    public final int initialBackoffMs;
    public final int maxBackoffMs;
    public final int maxEvents;

    public final int headerLenBytes;
    public final int deviceIdOffsetBytes;
    public final int deviceIdLenBytes;
    public final int msgTypeOffsetBytes;
    public final int msgTypeLenBytes;
    public final int dataLenOffsetBytes;
    public final int dataLenLenBytes;
    public final Endian dataLenEndian;
    public final int crcLengthBytes;
    public final Endian crcTailEndian;
    public final String headerMagicHex;
    public final boolean crcEnable;

    public final Set<String> typeOnlineValues;
    public final Set<String> typeHeartbeatValues;
    public final Set<String> typeBusinessValues;

    private Config(
            String httpHost,
            int httpPort,
            String tcpHost,
            int tcpPort,
            int readTimeoutMs,
            int initialBackoffMs,
            int maxBackoffMs,
            int maxEvents,
            int headerLenBytes,
            int deviceIdOffsetBytes,
            int deviceIdLenBytes,
            int msgTypeOffsetBytes,
            int msgTypeLenBytes,
            int dataLenOffsetBytes,
            int dataLenLenBytes,
            Endian dataLenEndian,
            int crcLengthBytes,
            Endian crcTailEndian,
            String headerMagicHex,
            boolean crcEnable,
            Set<String> typeOnlineValues,
            Set<String> typeHeartbeatValues,
            Set<String> typeBusinessValues
    ) {
        this.httpHost = httpHost;
        this.httpPort = httpPort;
        this.tcpHost = tcpHost;
        this.tcpPort = tcpPort;
        this.readTimeoutMs = readTimeoutMs;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.maxEvents = maxEvents;
        this.headerLenBytes = headerLenBytes;
        this.deviceIdOffsetBytes = deviceIdOffsetBytes;
        this.deviceIdLenBytes = deviceIdLenBytes;
        this.msgTypeOffsetBytes = msgTypeOffsetBytes;
        this.msgTypeLenBytes = msgTypeLenBytes;
        this.dataLenOffsetBytes = dataLenOffsetBytes;
        this.dataLenLenBytes = dataLenLenBytes;
        this.dataLenEndian = dataLenEndian;
        this.crcLengthBytes = crcLengthBytes;
        this.crcTailEndian = crcTailEndian;
        this.headerMagicHex = headerMagicHex.toUpperCase();
        this.crcEnable = crcEnable;
        this.typeOnlineValues = typeOnlineValues;
        this.typeHeartbeatValues = typeHeartbeatValues;
        this.typeBusinessValues = typeBusinessValues;
    }

    public static Config fromEnv() {
        Map<String,String> env = System.getenv();
        String httpHost = requiredString(env, "HTTP_HOST");
        int httpPort = requiredInt(env, "HTTP_PORT");
        String tcpHost = requiredString(env, "TCP_HOST");
        int tcpPort = requiredInt(env, "TCP_PORT");
        int readTimeoutMs = requiredInt(env, "READ_TIMEOUT_MS");
        int initialBackoffMs = requiredInt(env, "INITIAL_BACKOFF_MS");
        int maxBackoffMs = requiredInt(env, "MAX_BACKOFF_MS");
        int maxEvents = requiredInt(env, "MAX_EVENTS");

        int headerLenBytes = requiredInt(env, "HEADER_LEN_BYTES");
        int deviceIdOffsetBytes = requiredInt(env, "DEVICE_ID_OFFSET_BYTES");
        int deviceIdLenBytes = requiredInt(env, "DEVICE_ID_LENGTH_BYTES");
        int msgTypeOffsetBytes = requiredInt(env, "MSG_TYPE_OFFSET_BYTES");
        int msgTypeLenBytes = requiredInt(env, "MSG_TYPE_LENGTH_BYTES");
        int dataLenOffsetBytes = requiredInt(env, "DATA_LEN_OFFSET_BYTES");
        int dataLenLenBytes = requiredInt(env, "DATA_LEN_LENGTH_BYTES");
        Endian dataLenEndian = parseEndian(requiredString(env, "DATA_LEN_ENDIAN"));
        int crcLengthBytes = requiredInt(env, "CRC_LENGTH_BYTES");
        Endian crcTailEndian = parseEndian(requiredString(env, "CRC_TAIL_ENDIAN"));
        String headerMagicHex = requiredString(env, "HEADER_MAGIC_HEX");
        boolean crcEnable = Boolean.parseBoolean(requiredString(env, "CRC_ENABLE"));

        Set<String> typeOnlineValues = parseHexSet(requiredString(env, "TYPE_ONLINE_VALUES"));
        Set<String> typeHeartbeatValues = parseHexSet(requiredString(env, "TYPE_HEARTBEAT_VALUES"));
        Set<String> typeBusinessValues = parseHexSet(requiredString(env, "TYPE_BUSINESS_VALUES"));

        return new Config(
                httpHost, httpPort, tcpHost, tcpPort, readTimeoutMs, initialBackoffMs, maxBackoffMs, maxEvents,
                headerLenBytes, deviceIdOffsetBytes, deviceIdLenBytes, msgTypeOffsetBytes, msgTypeLenBytes,
                dataLenOffsetBytes, dataLenLenBytes, dataLenEndian, crcLengthBytes, crcTailEndian,
                headerMagicHex, crcEnable, typeOnlineValues, typeHeartbeatValues, typeBusinessValues
        );
    }

    static String requiredString(Map<String,String> env, String key) {
        String v = env.get(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required environment variable: " + key);
        }
        return v.trim();
    }

    static int requiredInt(Map<String,String> env, String key) {
        String v = requiredString(env, key);
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for env " + key + ": " + v);
        }
    }

    static Endian parseEndian(String v) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "big": return Endian.BIG;
            case "little": return Endian.LITTLE;
            default: throw new IllegalArgumentException("Invalid endian: " + v + " (use 'big' or 'little')");
        }
    }

    static Set<String> parseHexSet(String v) {
        Set<String> s = new HashSet<>();
        for (String p : v.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) s.add(t.toUpperCase(Locale.ROOT));
        }
        return s;
    }
}
