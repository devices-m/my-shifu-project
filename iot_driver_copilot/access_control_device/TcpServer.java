import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class TcpServer {
    private final Config cfg;
    private final DataStore store;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private final List<DeviceHandler> handlers = new ArrayList<>();

    TcpServer(Config cfg, DataStore store) {
        this.cfg = cfg;
        this.store = store;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(cfg.tcpHost, cfg.tcpPort));
        running = true;
        acceptThread = new Thread(this::acceptLoop, "tcp-accept");
        acceptThread.start();
        System.out.println(now() + " [INFO] TCP server listening on " + cfg.tcpHost + ":" + cfg.tcpPort);
    }

    public void stop() throws IOException {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (Exception ignored) {}
        }
        synchronized (handlers) {
            for (DeviceHandler h : handlers) h.stopRunning();
        }
        System.out.println(now() + " [INFO] TCP server stopped");
    }

    private void acceptLoop() {
        int backoff = cfg.initialBackoffMs;
        while (running) {
            try {
                Socket s = serverSocket.accept();
                s.setSoTimeout(cfg.readTimeoutMs);
                DeviceHandler h = new DeviceHandler(s, cfg, store);
                synchronized (handlers) { handlers.add(h); }
                h.start();
                backoff = cfg.initialBackoffMs; // reset backoff on success
            } catch (IOException e) {
                if (!running) break;
                System.err.println(now() + " [ERROR] Accept failed: " + e.getMessage() + ". Backing off " + backoff + "ms");
                try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
                backoff = Math.min(cfg.maxBackoffMs, backoff * 2);
            }
        }
    }

    static String now() { return ZonedDateTime.now().toString(); }
}

class DeviceHandler extends Thread {
    private final Socket socket;
    private final Config cfg;
    private final DataStore store;
    private volatile boolean running = true;
    private volatile String sessionDeviceId = null;

    DeviceHandler(Socket socket, Config cfg, DataStore store) {
        super("device-handler-" + socket.getRemoteSocketAddress());
        this.socket = socket;
        this.cfg = cfg;
        this.store = store;
    }

    public void stopRunning() {
        running = false;
        try { socket.close(); } catch (Exception ignored) {}
    }

    @Override public void run() {
        System.out.println(now() + " [INFO] Device connected: " + socket.getRemoteSocketAddress());
        StringBuilder hexBuf = new StringBuilder(8192);
        try (InputStream in = socket.getInputStream()) {
            byte[] buf = new byte[4096];
            while (running && !socket.isClosed()) {
                try {
                    int n = in.read(buf);
                    if (n < 0) break;
                    if (n == 0) continue;
                    String ascii = new String(buf, 0, n, StandardCharsets.US_ASCII);
                    String sanitized = HexUtil.sanitizeToHexUpper(ascii);
                    if (sanitized.isEmpty()) continue;
                    hexBuf.append(sanitized);
                    parseBuffer(hexBuf);
                } catch (java.net.SocketTimeoutException ste) {
                    // read timeout, continue (heartbeat gap)
                }
            }
        } catch (IOException e) {
            System.err.println(now() + " [ERROR] Read error from " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
            if (sessionDeviceId != null) {
                store.markDeviceDisconnected(sessionDeviceId);
                System.out.println(now() + " [INFO] Device disconnected: " + sessionDeviceId);
            } else {
                System.out.println(now() + " [INFO] Device disconnected: " + socket.getRemoteSocketAddress());
            }
        }
    }

    private void parseBuffer(StringBuilder hexBuf) {
        int headerHexLen = cfg.headerLenBytes * 2;
        while (true) {
            int idx = indexOf(hexBuf, cfg.headerMagicHex);
            if (idx < 0) {
                // if buffer grows too large, trim it to avoid memory bloat
                if (hexBuf.length() > 1024 * 1024) {
                    hexBuf.delete(0, hexBuf.length() - 1024);
                }
                return;
            }
            if (idx > 0) hexBuf.delete(0, idx);
            if (hexBuf.length() < headerHexLen) return;

            // compute total length using header
            String headerHex = hexBuf.substring(0, headerHexLen);
            int totalBytes;
            try {
                totalBytes = Parser.computeTotalMessageBytes(hexBuf.substring(0, Math.min(hexBuf.length(), headerHexLen)), cfg);
                // compute again with full header available
                totalBytes = Parser.computeTotalMessageBytes(hexBuf.substring(0, headerHexLen), cfg);
            } catch (Exception e) {
                // malformed header; drop one nibble and retry
                hexBuf.deleteCharAt(0);
                continue;
            }
            if (totalBytes <= 0) {
                // cannot determine; drop one nibble
                hexBuf.deleteCharAt(0);
                continue;
            }
            int totalHexLen = totalBytes * 2;
            if (hexBuf.length() < totalHexLen) return; // wait for more data

            String msgHex = hexBuf.substring(0, totalHexLen);
            hexBuf.delete(0, totalHexLen);
            try {
                ParsedMessage pm = Parser.parseMessage(msgHex, cfg);
                if (pm.deviceId != null) {
                    sessionDeviceId = pm.deviceId;
                    store.markDeviceConnected(sessionDeviceId);
                }
                store.addEvent(pm);
            } catch (Exception e) {
                System.err.println(now() + " [WARN] Failed to parse message: " + e.getMessage());
                // attempt resync by discarding one nibble
                if (hexBuf.length() > 0) hexBuf.deleteCharAt(0);
            }
        }
    }

    private static int indexOf(StringBuilder sb, String needle) {
        // naive search; for small buffers adequate
        String hay = sb.toString();
        return hay.indexOf(needle);
    }

    static String now() { return ZonedDateTime.now().toString(); }
}
