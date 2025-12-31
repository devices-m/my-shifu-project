import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

class HttpApi {
    private final Config cfg;
    private final DataStore store;
    private HttpServer server;

    HttpApi(Config cfg, DataStore store) {
        this.cfg = cfg;
        this.store = store;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(cfg.httpHost, cfg.httpPort), 0);
        server.createContext("/devices", new DevicesHandler(store));
        server.createContext("/events", new EventsHandler(store));
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println(now() + " [INFO] HTTP server started on " + cfg.httpHost + ":" + cfg.httpPort);
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            System.out.println(now() + " [INFO] HTTP server stopped");
        }
    }

    static String now() { return java.time.ZonedDateTime.now().toString(); }

    static class DevicesHandler implements HttpHandler {
        private final DataStore store;
        DevicesHandler(DataStore store) { this.store = store; }
        @Override public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            if (!"GET".equalsIgnoreCase(method)) { sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}"); return; }

            if (path.matches("^/devices/[^/]+/status$")) {
                String deviceId = path.substring("/devices/".length(), path.length() - "/status".length());
                DeviceStatus ds = store.getOrCreateDevice(deviceId);
                if (ds == null) { sendJson(exchange, 404, "{\"error\":\"device not found\"}"); return; }
                String json = "{" +
                        "\"deviceId\":\"" + JsonUtil.escape(ds.deviceId) + "\"," +
                        "\"online\":" + ds.online + "," +
                        "\"lastOnlineTime\":" + (ds.lastOnline == null ? "null" : "\"" + JsonUtil.iso(ds.lastOnline) + "\"") + "," +
                        "\"lastHeartbeatTime\":" + (ds.lastHeartbeat == null ? "null" : "\"" + JsonUtil.iso(ds.lastHeartbeat) + "\"") + "," +
                        "\"heartbeatIntervalMs\":" + (ds.heartbeatIntervalMs == null ? "null" : ds.heartbeatIntervalMs) + "," +
                        "\"lastMessageTime\":" + (ds.lastMessage == null ? "null" : "\"" + JsonUtil.iso(ds.lastMessage) + "\"") + "," +
                        "\"lastDisconnectTime\":" + (ds.lastDisconnect == null ? "null" : "\"" + JsonUtil.iso(ds.lastDisconnect) + "\"") +
                        "}";
                sendJson(exchange, 200, json);
                return;
            }

            // /devices list
            Map<String, String> q = parseQuery(uri.getRawQuery());
            String status = q.getOrDefault("status", "all");
            Instant since = parseInstant(q.get("since"));
            int limit = parseIntOrDefault(q.get("limit"), 0);
            long cursor = parseLongOrDefault(q.get("cursor"), 0L);
            List<DeviceStatus> devices = store.listDevices(status, since, limit, cursor);
            StringBuilder sb = new StringBuilder(2048);
            sb.append("[");
            for (int i = 0; i < devices.size(); i++) {
                DeviceStatus ds = devices.get(i);
                if (i > 0) sb.append(',');
                sb.append("{");
                sb.append("\"deviceId\":\"" + JsonUtil.escape(ds.deviceId) + "\",");
                sb.append("\"online\":" + ds.online + ",");
                sb.append("\"lastOnlineTime\":" + (ds.lastOnline == null ? "null" : "\"" + JsonUtil.iso(ds.lastOnline) + "\"") + ",");
                sb.append("\"lastHeartbeatTime\":" + (ds.lastHeartbeat == null ? "null" : "\"" + JsonUtil.iso(ds.lastHeartbeat) + "\"") + ",");
                sb.append("\"lastMessageTime\":" + (ds.lastMessage == null ? "null" : "\"" + JsonUtil.iso(ds.lastMessage) + "\""));
                sb.append("}");
            }
            sb.append("]");
            sendJson(exchange, 200, sb.toString());
        }
    }

    static class EventsHandler implements HttpHandler {
        private final DataStore store;
        EventsHandler(DataStore store) { this.store = store; }
        @Override public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            URI uri = exchange.getRequestURI();
            if (!"GET".equalsIgnoreCase(method)) { sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}"); return; }
            Map<String, String> q = parseQuery(uri.getRawQuery());
            String typeStr = q.get("type");
            EventType type = null;
            if (typeStr != null) {
                switch (typeStr.toLowerCase(Locale.ROOT)) {
                    case "online": type = EventType.ONLINE; break;
                    case "heartbeat": type = EventType.HEARTBEAT; break;
                    case "business": type = EventType.BUSINESS; break;
                    default: type = null; break;
                }
            }
            String deviceId = q.get("deviceId");
            Instant since = parseInstant(q.get("since"));
            Instant until = parseInstant(q.get("until"));
            int limit = parseIntOrDefault(q.get("limit"), 0);
            long cursor = parseLongOrDefault(q.get("cursor"), 0L);
            boolean asc = "asc".equalsIgnoreCase(q.getOrDefault("order", "asc"));
            List<Event> events = store.queryEvents(type, deviceId, since, until, limit, cursor, asc);
            StringBuilder sb = new StringBuilder(4096);
            sb.append("[");
            for (int i = 0; i < events.size(); i++) {
                Event e = events.get(i);
                if (i > 0) sb.append(',');
                sb.append("{");
                sb.append("\"id\":" + e.id + ",");
                sb.append("\"deviceId\":\"" + JsonUtil.escape(e.deviceId) + "\", ");
                sb.append("\"type\":\"" + e.type.name().toLowerCase(Locale.ROOT) + "\", ");
                sb.append("\"timestamp\":\"" + JsonUtil.iso(e.timestamp) + "\", ");
                sb.append("\"parsedData\":" + e.parsedDataJson + ",");
                sb.append("\"crcValid\":" + e.crcValid);
                sb.append("}");
            }
            sb.append("]");
            sendJson(exchange, 200, sb.toString());
        }
    }

    static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    static Map<String,String> parseQuery(String raw) {
        Map<String,String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String p : raw.split("&")) {
            int i = p.indexOf('=');
            if (i < 0) {
                map.put(decode(p), "");
            } else {
                map.put(decode(p.substring(0,i)), decode(p.substring(i+1)));
            }
        }
        return map;
    }

    static String decode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) { return s; }
    }

    static Instant parseInstant(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return java.time.Instant.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static int parseIntOrDefault(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
    static long parseLongOrDefault(String s, long def) {
        if (s == null || s.isEmpty()) return def;
        try { return Long.parseLong(s); } catch (Exception e) { return def; }
    }
}
