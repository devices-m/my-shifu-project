import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class Driver {
    public static void main(String[] args) throws Exception {
        Config cfg = Config.fromEnv();
        DataStore store = new DataStore(cfg);

        HttpApi httpApi = new HttpApi(cfg, store);
        TcpServer tcpServer = new TcpServer(cfg, store);

        CountDownLatch shutdownLatch = new CountDownLatch(2);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(now() + " [INFO] Shutdown requested");
            try {
                tcpServer.stop();
            } catch (Exception e) {
                System.err.println(now() + " [ERROR] Error stopping TCP server: " + e.getMessage());
            }
            try {
                httpApi.stop();
            } catch (Exception e) {
                System.err.println(now() + " [ERROR] Error stopping HTTP server: " + e.getMessage());
            }
        }));

        httpApi.start();
        tcpServer.start();

        System.out.println(now() + " [INFO] Driver started. HTTP on " + cfg.httpHost + ":" + cfg.httpPort + ", TCP on " + cfg.tcpHost + ":" + cfg.tcpPort);
    }

    static String now() { return java.time.ZonedDateTime.now().toString(); }
}
