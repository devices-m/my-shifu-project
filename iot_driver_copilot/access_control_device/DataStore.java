import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

class DataStore {
    private final Config cfg;
    private final Map<String, DeviceStatus> devices = new ConcurrentHashMap<>();

    private final Event[] ring;
    private final int ringSize;
    private final AtomicLong eventSeq = new AtomicLong(0);
    private volatile long writePos = 0; // monotonically increasing

    DataStore(Config cfg) {
        this.cfg = cfg;
        this.ringSize = cfg.maxEvents;
        this.ring = new Event[ringSize];
    }

    public DeviceStatus getOrCreateDevice(String deviceId) {
        if (deviceId == null) return null;
        return devices.computeIfAbsent(deviceId, DeviceStatus::new);
    }

    public void markDeviceConnected(String deviceId) {
        DeviceStatus ds = getOrCreateDevice(deviceId);
        if (ds != null) {
            ds.online = true;
            if (ds.lastOnline == null) ds.lastOnline = Instant.now();
        }
    }

    public void markDeviceDisconnected(String deviceId) {
        if (deviceId == null) return;
        DeviceStatus ds = devices.get(deviceId);
        if (ds != null) {
            ds.online = false;
            ds.lastDisconnect = Instant.now();
        }
    }

    public void addEvent(ParsedMessage pm) {
        Instant now = Instant.now();
        DeviceStatus ds = getOrCreateDevice(pm.deviceId);
        if (ds != null) {
            ds.totalMessages.incrementAndGet();
            ds.lastMessage = now;
            if (pm.eventType == EventType.ONLINE) {
                ds.online = true;
                ds.lastOnline = now;
            } else if (pm.eventType == EventType.HEARTBEAT) {
                if (ds.lastHeartbeat != null) {
                    ds.heartbeatIntervalMs = java.time.Duration.between(ds.lastHeartbeat, now).toMillis();
                }
                ds.lastHeartbeat = now;
                ds.online = true;
            } else if (pm.eventType == EventType.BUSINESS) {
                ds.online = true;
            }
        }
        long id = eventSeq.incrementAndGet();
        String parsedDataJson = "{\"rawDataHex\":\"" + JsonUtil.escape(pm.dataHex) + "\",\"messageTypeHex\":\"" + JsonUtil.escape(pm.messageTypeHex) + "\"}";
        Event e = new Event(id, pm.deviceId, pm.eventType, now, parsedDataJson, pm.crcValid);
        int idx = (int)(id % ringSize);
        ring[idx] = e;
        writePos = id;
    }

    public List<DeviceStatus> listDevices(String status, Instant since, int limit, long cursor) {
        List<DeviceStatus> list = new ArrayList<>(devices.values());
        list.sort((a,b) -> {
            Instant am = a.lastMessage, bm = b.lastMessage;
            long aval = am == null ? 0L : am.toEpochMilli();
            long bval = bm == null ? 0L : bm.toEpochMilli();
            return Long.compare(bval, aval);
        });
        List<DeviceStatus> out = new ArrayList<>();
        int start = (int)Math.max(0, cursor);
        for (int i = start; i < list.size(); i++) {
            DeviceStatus ds = list.get(i);
            if (since != null && ds.lastMessage != null && ds.lastMessage.isBefore(since)) {
                continue;
            }
            if ("online".equalsIgnoreCase(status) && !ds.online) continue;
            if ("offline".equalsIgnoreCase(status) && ds.online) continue;
            out.add(ds);
            if (limit > 0 && out.size() >= limit) break;
        }
        return out;
    }

    public List<Event> queryEvents(EventType type, String deviceId, Instant since, Instant until, int limit, long cursor, boolean asc) {
        List<Event> res = new ArrayList<>();
        long latest = writePos;
        long startId = cursor > 0 ? cursor + 1 : (asc ? Math.max(1, latest - ringSize + 1) : latest);
        if (asc) {
            for (long id = startId; id <= latest; id++) {
                Event e = ring[(int)(id % ringSize)];
                if (e == null || e.id != id) continue; // overwritten
                if (type != null && e.type != type) continue;
                if (deviceId != null && !deviceId.equals(e.deviceId)) continue;
                if (since != null && e.timestamp.isBefore(since)) continue;
                if (until != null && e.timestamp.isAfter(until)) continue;
                res.add(e);
                if (limit > 0 && res.size() >= limit) break;
            }
        } else {
            for (long id = startId; id >= Math.max(1, latest - ringSize + 1); id--) {
                Event e = ring[(int)(id % ringSize)];
                if (e == null || e.id != id) continue; // overwritten
                if (type != null && e.type != type) continue;
                if (deviceId != null && !deviceId.equals(e.deviceId)) continue;
                if (since != null && e.timestamp.isBefore(since)) continue;
                if (until != null && e.timestamp.isAfter(until)) continue;
                res.add(e);
                if (limit > 0 && res.size() >= limit) break;
            }
        }
        return res;
    }
}
