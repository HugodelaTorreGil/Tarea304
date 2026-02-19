import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ServerLogger {

    private static final Object LOCK = new Object();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ServerLogger() {}

    public static void log(String clientIp, String msg) {
        String line = "[" + LocalDateTime.now().format(FMT) + "] [" + clientIp + "] " + msg;

        synchronized (LOCK) {
            System.out.println(line);
            // Si quieres fichero:
            try (BufferedWriter bw = new BufferedWriter(new FileWriter("server.log", true))) {
                bw.write(line);
                bw.newLine();
            } catch (IOException ignored) {
            }
        }
    }
}
