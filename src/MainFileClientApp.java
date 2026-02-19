import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Aplicación cliente para la gestión remota de ficheros mediante TCP.
 *
 * <p>Este cliente se conecta a un servidor TCP, permite al usuario introducir
 * comandos por consola y muestra las respuestas del servidor siguiendo el
 * protocolo establecido.</p>
 *
 * <p>Soporta: list, show, delete, upload, download, mkdir, rename, exists, info,
 * pwd, cd, ping, help, touch, quit.</p>
 */
public class MainFileClientApp {

    public static void main(String[] args) {

        String host = "localhost";
        int puerto = 2121;

        if (args.length >= 1 && args[0] != null && !args[0].isBlank()) {
            host = args[0].trim();
        }
        if (args.length >= 2) {
            try {
                puerto = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException ignored) {
                puerto = 2121;
            }
        }

        try (
                Socket socket = new Socket(host, puerto)
        ) {
            InputStream rawIn = socket.getInputStream();
            OutputStream rawOut = socket.getOutputStream();

            BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
            BufferedReader entradaServidor = new BufferedReader(new InputStreamReader(rawIn));
            PrintWriter salidaServidor = new PrintWriter(new BufferedWriter(new OutputStreamWriter(rawOut)), true);

            DataInputStream binIn = new DataInputStream(rawIn);
            DataOutputStream binOut = new DataOutputStream(rawOut);

            System.out.println("Conectado a " + host + ":" + puerto);
            System.out.println("Comandos:");
            System.out.println("list <ruta> | show <ruta> | delete <ruta>");
            System.out.println("upload <ruta_local> | download <ruta_servidor>");
            System.out.println("mkdir <dir> | rename <src> <dst> | exists <ruta> | info <ruta>");
            System.out.println("pwd | cd <dir> | touch <archivo> | ping | help | quit");

            while (true) {

                System.out.print("> ");
                String comando = teclado.readLine();

                if (comando == null) break;

                comando = comando.trim();
                if (comando.isEmpty()) continue;

                //Si el comando es upload, valido localmente antes de mandarlo
                if (comando.startsWith("upload ")) {
                    String rutaLocal = comando.substring(7).trim();
                    if (rutaLocal.isBlank()) {
                        System.out.println("KO");
                        continue;
                    }
                    Path fichero = Paths.get(rutaLocal);
                    if (!Files.exists(fichero) || !Files.isRegularFile(fichero) || !Files.isReadable(fichero)) {
                        System.out.println("KO");
                        continue;
                    }

                    //Mando el comando al servidor
                    salidaServidor.println(comando);

                    //Leo el OK/KO del servidor
                    String primera = entradaServidor.readLine();
                    if (primera == null) break;

                    System.out.println(primera);

                    if (!primera.equals("OK")) continue;

                    long tamano = Files.size(fichero);

                    //Mando tamaño y bytes
                    binOut.writeLong(tamano);

                    try (InputStream fis = new BufferedInputStream(Files.newInputStream(fichero))) {
                        byte[] buffer = new byte[8192];
                        int leidos;
                        while ((leidos = fis.read(buffer)) != -1) {
                            binOut.write(buffer, 0, leidos);
                        }
                    }
                    binOut.flush();

                    continue;
                }

                //Mando el comando normal al servidor
                salidaServidor.println(comando);

                //Leo la primera línea (OK/KO)
                String primera = entradaServidor.readLine();
                if (primera == null) break;

                System.out.println(primera);

                if (primera.equals("KO")) {
                    continue;
                }

                if (comando.equals("quit")) {
                    break;
                }

                //list: leo hasta línea vacía
                if (comando.startsWith("list ")) {
                    while (true) {
                        String l = entradaServidor.readLine();
                        if (l == null) return;
                        if (l.isEmpty()) break;
                        System.out.println(l);
                    }
                    continue;
                }

                //show: OK + N + N líneas
                if (comando.startsWith("show ")) {
                    String nStr = entradaServidor.readLine();
                    if (nStr == null) return;

                    int n;
                    try {
                        n = Integer.parseInt(nStr.trim());
                    } catch (NumberFormatException e) {
                        System.out.println("KO");
                        continue;
                    }

                    System.out.println(n);

                    for (int i = 0; i < n; i++) {
                        String l = entradaServidor.readLine();
                        if (l == null) return;
                        System.out.println(l);
                    }
                    continue;
                }

                //download: OK + (binario) long tamaño + bytes
                if (comando.startsWith("download ")) {
                    long tamano = binIn.readLong();
                    if (tamano < 0) {
                        System.out.println("KO");
                        continue;
                    }

                    String rutaServidor = comando.substring(9).trim();
                    if (rutaServidor.isBlank()) {
                        System.out.println("KO");
                        continue;
                    }

                    //Me quedo con el nombre del fichero para guardarlo localmente
                    String nombre = Paths.get(rutaServidor).getFileName().toString();
                    Path destino = Paths.get(nombre).toAbsolutePath().normalize();

                    //Si ya existe, no sobreescribo por seguridad
                    if (Files.exists(destino)) {
                        System.out.println("KO");
                        //OJO: si no lo consumes, se queda desincronizado el stream
                        //Así que consumo igualmente los bytes del download antes de seguir
                        consumirBytes(binIn, tamano);
                        continue;
                    }

                    try (OutputStream fos = new BufferedOutputStream(Files.newOutputStream(destino))) {
                        byte[] buffer = new byte[8192];
                        long restante = tamano;

                        while (restante > 0) {
                            int paraLeer = (int) Math.min(buffer.length, restante);
                            int leidos = binIn.read(buffer, 0, paraLeer);
                            if (leidos == -1) throw new EOFException("Servidor cerró durante download");
                            fos.write(buffer, 0, leidos);
                            restante -= leidos;
                        }
                    }

                    System.out.println("Descargado: " + destino.getFileName() + " (" + tamano + " bytes)");
                    continue;
                }



            }

        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void consumirBytes(DataInputStream binIn, long tamano) throws IOException {
        byte[] buffer = new byte[8192];
        long restante = tamano;
        while (restante > 0) {
            int paraLeer = (int) Math.min(buffer.length, restante);
            int leidos = binIn.read(buffer, 0, paraLeer);
            if (leidos == -1) break;
            restante -= leidos;
        }
    }
}
