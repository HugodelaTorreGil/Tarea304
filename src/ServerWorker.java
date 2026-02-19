import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class ServerWorker implements Runnable{

    private final Socket socketCliente;
    //Este es el directorio actual por cada cliente
    //Lo inicializo desde donde arranco el servidor
    private Path directorioActual = Paths.get(".").toAbsolutePath().normalize();

    public ServerWorker(Socket socketCliente) {
        this.socketCliente = socketCliente;
    }

    @Override
    public void run() {

        //Saco la ip del cliente para los logs
        String ip = socketCliente.getInetAddress().getHostAddress();
        //Dejo registrado que el/la cliente se ha conectado
        ServerLogger.log(ip, "CONNECT");

        try {
            InputStream rawIn = socketCliente.getInputStream();
            OutputStream rawOut = socketCliente.getOutputStream();

            //Preparo el lector de texto para leer comandos línea a línea
            BufferedReader entrada = new BufferedReader(new InputStreamReader(rawIn));
            //Preparo el escritor de texto con autoflush para que el/la cliente reciba lo que mando al momento
            PrintWriter salida = new PrintWriter(new BufferedWriter(new OutputStreamWriter(rawOut)), true);

            //Preparo el lector binario para los uploads (tamaño + bytes)
            DataInputStream binIn = new DataInputStream(rawIn);
            //Preparo el escritor binario para los downloads (tamaño + bytes)
            DataOutputStream binOut = new DataOutputStream(rawOut);

            while (true) {

                //Leo la línea del cliente
                String linea = entrada.readLine();
                //Registro lo que me llega
                ServerLogger.log(ip, "RECIBIDO: " + linea);

                //Si me llega null es que el cliente ha cerrado la conexión
                if (linea == null) break;

                //Limpio los espacios para evitar fallos por escribir con espacios raros
                linea = linea.trim();

                //Si me llega una línea vacía lo considero comando inválido
                if (linea.isEmpty()) {
                    //Contesto KO al cliente porque no hay comando real
                    salida.println("KO");
                    continue;
                }

                //Si el cliente me pide salir, le contesto OK y cierro la sesión
                if (linea.equals("quit")) {
                    salida.println("OK");
                    break;
                }

                if (linea.startsWith("list ")) {
                    String ruta = linea.substring(5).trim();
                    ejecutarList(ruta, salida);
                    continue;
                }

                if (linea.startsWith("show ")) {
                    String ruta = linea.substring(5).trim();
                    ejecutarShow(ruta, salida);
                    continue;
                }

                if (linea.startsWith("delete ")) {
                    String ruta = linea.substring(7).trim();
                    ejecutarDelete(ruta, salida);
                    continue;
                }

                if (linea.startsWith("upload ")) {
                    String ruta = linea.substring(7).trim();
                    ejecutarUpload(ruta, salida, binIn, ip);
                    continue;
                }

                if (linea.startsWith("download ")) {
                    String ruta = linea.substring(9).trim();
                    ejecutarDownload(ruta, salida, binOut, ip);
                    continue;
                }

                //Si no reconozco el comando, le contesto KO al cliente
                salida.println("KO");
            }

        } catch (IOException e) {
            //Registro el error por si algo falla en la conexión o en el parsing
            ServerLogger.log(ip, "ERROR: " + e.getMessage());
        } finally {
            //Dejo registrado que el cliente se ha desconectado
            ServerLogger.log(ip, "DESCONECTADO");
            try { socketCliente.close(); } catch (IOException ignored) {}
        }
    }


    void ejecutarList(String ruta, PrintWriter salida){

        //Si me llega vacío o null, lo corto
        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            return;
        }

        //Resuelvo la ruta respecto al directorio actual del cliente
        File dir = directorioActual.resolve(ruta).normalize().toFile();

        //Valido que exista y sea directorio
        if(!dir.exists() || !dir.isDirectory()){
            salida.println("KO");
            return;
        }

        //Saco el contenido del directorio
        File[] contenido = dir.listFiles();

        //Si listFiles falla (permisos o error), devuelvo KO
        if (contenido == null) {
            salida.println("KO");
            return;
        }

        //Empiezo la respuesta correcta
        salida.println("OK");

        //Recorro todos los archivos y carpetas que hay dentro
        for(int i = 0; i < contenido.length; i++){
            File f = contenido[i];
            String nombre = f.getName();
            long tamano;

            //Si es directorio marco tamaño 0 como pide el protocolo
            if(f.isDirectory()){
                tamano = 0;
            }else{
                //Saco el tamaño en KB para que salga algo más razonable
                tamano = f.length() / 1024;
            }

            //Aquí mando "nombre tamaño"
            salida.println(nombre + " " + tamano);
        }

        //Aquí mando la línea vacía como delimitador de fin del list
        salida.println("");
    }

    void ejecutarShow(String ruta, PrintWriter salida){

        //Si me llega vacío o null, KO
        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            return;
        }

        //Resuelvo la ruta respecto al directorio actual del cliente
        File dir = directorioActual.resolve(ruta).normalize().toFile();

        //Valido que exista y sea archivo
        if(!dir.exists() || !dir.isFile()){
            salida.println("KO");
            return;
        }

        //Guardo las líneas para poder mandar primero el número de líneas
        List<String> lineas = new ArrayList<>();

        //Abro el archivo y lo leo línea a línea
        try(BufferedReader bufferedReader = new BufferedReader(new FileReader(dir))){

            String linea;

            while((linea = bufferedReader.readLine()) != null){
                lineas.add(linea);
            }

        }catch(IOException e){
            salida.println("KO");
            return;
        }

        //Empiezo respuesta correcta
        salida.println("OK");
        //Mando el total de líneas
        salida.println(lineas.size());

        //Mando el contenido del archivo
        for(String l : lineas){
            salida.println(l);
        }
    }

    void ejecutarDelete(String ruta, PrintWriter salida){

        //Si me llega vacío o null, KO
        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            return;
        }

        //Resuelvo la ruta respecto al directorio actual del cliente
        File dir = directorioActual.resolve(ruta).normalize().toFile();

        //Si no existe, KO
        if(!dir.exists()){
            salida.println("KO");
            return;
        }

        //Si es archivo, intento borrarlo
        if(dir.isFile()){
            if(dir.delete()){
                salida.println("OK");
            }else{
                salida.println("KO");
            }
            return;
        }

        //Si es directorio, solo dejo borrar si está vacío
        if(dir.isDirectory()){
            File[] hijos = dir.listFiles();

            //Aquí si no puedo listar, KO
            if(hijos == null){
                salida.println("KO");
                return;
            }

            //Si tiene contenido, KO
            if(hijos.length > 0){
                salida.println("KO");
                return;
            }

            //Si está vacío, intento borrarlo
            if(dir.delete()){
                salida.println("OK");
            }else{
                salida.println("KO");
            }
            return;
        }

        //Por si me llega algo rarísimo que no es ni archivo ni directorio
        salida.println("KO");
    }

    void ejecutarUpload(String ruta, PrintWriter salida, DataInputStream binIn, String ip){
        //Si falta la ruta, KO
        if(ruta == null || ruta.isBlank()){
            salida.println("KO");
            ServerLogger.log(ip, "RESPUESTA: KO (falta ruta en el upload)");
            return;
        }

        //Me quedo solo con el nombre para que no me cuelen rutas tipo ../..
        String nombreArchivo = Paths.get(ruta).getFileName().toString();

        //Preparo el destino respetando el directorio actual del cliente
        Path destino = directorioActual.resolve(nombreArchivo).normalize();

        //Si ya existe el archivo, KO
        if (Files.exists(destino)) {
            salida.println("KO");
            ServerLogger.log(ip, "RESPUESTA: KO (ya existe el archivo: ) " + nombreArchivo);
            return;
        }

        try{
            //Le digo al cliente que puede empezar a mandar el binario
            salida.println("OK");
            ServerLogger.log(ip, "RESPUESTA: OK (empezando upload: ) " + nombreArchivo);

            //Leo el tamaño total del archivo
            long tamano = binIn.readLong();
            if(tamano < 0){
                throw new IOException("Tamaño negativo");
            }

            //Creo el archivo de destino y voy escribiendo en bloques para que vaya rápido
            try(OutputStream fos = new BufferedOutputStream(Files.newOutputStream(destino, StandardOpenOption.CREATE_NEW))){
                //Creo el buffer de memoria
                byte[] buffer = new byte[8192];
                //Llevo la cuenta de lo que me falta por recibir
                long restante = tamano;

                //Mientras me queden bytes por recibir sigo leyendo
                while(restante > 0){
                    //Ajusto lo que voy a leer para no pasarme del final
                    int paraLeer = (int) Math.min(buffer.length, restante);
                    //Leo del socket justo los bytes que necesito
                    int leidos = binIn.read(buffer, 0, paraLeer);

                    //Si el cliente se corta a mitad, fallo
                    if(leidos == -1){
                        throw new EOFException("El cliente se cerro durante el upload");
                    }

                    //Escribo en el archivo los bytes que acabo de recibir
                    fos.write(buffer, 0, leidos);
                    //Resto lo recibido y cuando llegue a 0 ya está completo
                    restante -= leidos;
                }
            }

            //Dejo registrado que el upload ha terminado bien
            ServerLogger.log(ip, "UPLOAD OK " + nombreArchivo + " (" + tamano + " bytes)");

        } catch (IOException e) {
            //Logueo el fallo para saber qué ha pasado
            ServerLogger.log(ip, "FALLO EN EL UPLOAD " + nombreArchivo + " (" + e.getMessage() + ")");
            //Intento borrar el archivo parcial si se quedó a medias
            try { Files.deleteIfExists(destino); } catch (IOException ignored) {}
        }
    }

    void ejecutarDownload(String ruta, PrintWriter salida, DataOutputStream binOut, String ip) throws IOException {

        //Si falta la ruta, KO
        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            ServerLogger.log(ip, "RESPUESTA: KO (No se encuentra la ruta a la que hacer download)");
            return;
        }

        //Resuelvo el archivo a descargar respecto al directorio actual del/la cliente
        Path origen = directorioActual.resolve(ruta).normalize();

        //Valido que exista y sea archivo normal
        if (!Files.exists(origen) || !Files.isRegularFile(origen)) {
            salida.println("KO");
            ServerLogger.log(ip, "RESPUESTA: KO (no se ha encontrado download) " + ruta);
            return;
        }

        //Saco el tamaño total para mandarlo antes del binario
        long tamano = Files.size(origen);

        //Le digo al/la cliente que el download va a empezar
        salida.println("OK");
        ServerLogger.log(ip, "RESPUESTA: OK (empezando download) " + origen.getFileName() + " (" + tamano + " bytes)");

        //Envío el tamaño del archivo (8 bytes)
        binOut.writeLong(tamano);

        //Abro el archivo y lo mando por el socket en bloques
        try(InputStream fis = new BufferedInputStream(Files.newInputStream(origen))){
            //Creo el buffer
            byte[] buffer = new byte[8192];
            int leidos;
            //Leo del archivo y lo escribo al socket hasta terminar
            while((leidos = fis.read(buffer)) != -1){
                binOut.write(buffer, 0, leidos);
            }
        }

        //Fuerzo el envío real de lo que quede en el buffer
        binOut.flush();

        //Dejo registrado que el download terminó bien
        ServerLogger.log(ip, "DOWNLOAD OK " + origen.getFileName());
    }


}
