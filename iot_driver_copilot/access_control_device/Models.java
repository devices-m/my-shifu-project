import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

enum EventType { ONLINE, HEARTBEAT, BUSINESS, UNKNOWN }

class Event {
    public final long id;
    public final String deviceId;
    public final EventType type;
    public final Instant timestamp;
    public final String parsedDataJson;
    public final boolean crcValid;

    public Event(long id, String deviceId, EventType type, Instant timestamp, String parsedDataJson, boolean crcValid) {
        this.id = id;
        this.deviceId = deviceId;
        this.type = type;
        this.timestamp = timestamp;
        this.parsedDataJson = parsedDataJson;
        this.crcValid = crcValid;
    }
}

class DeviceStatus {
    public final String deviceId;
    public volatile boolean online = false;
    public volatile Instant lastOnline = null;
    public volatile Instant lastHeartbeat = null;
    public volatile Long heartbeatIntervalMs = null;
    public volatile Instant lastMessage = null;
    public volatile Instant lastDisconnect = null;
    public final AtomicLong totalMessages = new AtomicLong(0);

    public DeviceStatus(String deviceId) {
        this.deviceId = deviceId;
    }
}

class ParsedMessage {
    public final String deviceId;
    public final String messageTypeHex;
    public final String dataHex;
    public final boolean crcValid;
    public final EventType eventType;

    public ParsedMessage(String deviceId, String messageTypeHex, String dataHex, boolean crcValid, EventType eventType) {
        this.deviceId = deviceId;
        this.messageTypeHex = messageTypeHex;
        this.dataHex = dataHex;
        this.crcValid = crcValid;
        this.eventType = eventType;
    }
}
