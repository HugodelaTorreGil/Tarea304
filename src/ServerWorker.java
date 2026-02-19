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

        String ip = socketCliente.getInetAddress().getHostAddress();
        ServerLogger.log(ip, "CONNECT");

        try(
                BufferedReader entrada = new BufferedReader(new InputStreamReader(socketCliente.getInputStream()));
                PrintWriter salida = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socketCliente.getOutputStream())));
                DataInputStream binIn = new DataInputStream(new BufferedInputStream(socketCliente.getInputStream()));
                DataOutputStream binOut = new DataOutputStream(new BufferedOutputStream(socketCliente.getOutputStream()))
        ){

            while(true){

                String linea = entrada.readLine();
                ServerLogger.log(ip, "RECV: " + linea);

                if(linea == null){
                    break;
                }

                linea = linea.trim();

                if(linea.isEmpty()){
                    System.out.println("KO");
                    continue;
                }

                if(linea.equals("quit")){
                    System.out.println("OK");
                    break;
                }

                if(linea.startsWith("list ")){
                    String ruta = linea.substring(5).trim();
                    ejecutarList(ruta, salida);
                    continue;
                }

                if(linea.startsWith("show ")){
                    String ruta = linea.substring(5).trim();
                    ejecutarShow(ruta, salida);
                    continue;
                }

                if(linea.startsWith("delete ")){
                    String ruta = linea.substring(5).trim();
                    ejecutarDelete(ruta, salida);
                    continue;
                }

                if(linea.startsWith("upload ")){
                    String ruta = linea.substring(7).trim();
                    ejecutarUpload(ruta, salida, binIn, ip);
                    continue;
                }

                if(linea.startsWith("dowload ")){
                    String ruta = linea.substring(9).trim();
                    ejecutarDownload(ruta, salida, binOut, ip);
                    continue;
                }

            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    void ejecutarList(String ruta, PrintWriter salida){

            File dir = new File(ruta);

            if(!dir.exists() || !dir.isDirectory()){
                salida.println("KO");
                return;
            }

            salida.println("OK");

           File[] contenido = dir.listFiles();

           for(int i = 0; i >= contenido.length; i++){
               File f = contenido[i];
               String nombre = f.getName();
               long tamano;

               if(f.isDirectory()){
                   tamano = 0;
               }else{
                   tamano = f.length() / 1024;
               }

               salida.println(nombre + " " + tamano);

           }

           salida.println("");

    }

    void ejecutarShow(String ruta, PrintWriter salida){

        File dir = new File(ruta);

        if(!dir.exists() || !dir.isFile()){
            salida.println("KO");
            return;
        }

        List<String> lineas = new ArrayList<>();

        try(BufferedReader bufferedReader = new BufferedReader(new FileReader(dir))){

            String linea;

            while((linea = bufferedReader.readLine()) != null){
                lineas.add(linea);
            }

        }catch(IOException e){
            salida.println("KO");
            return;
        }

        salida.println("OK");
        salida.println(lineas.size());

        for(String l : lineas){
            salida.println(l);
        }

    }

    void ejecutarDelete(String ruta, PrintWriter salida){

        File dir = new File(ruta);

        if(!dir.exists()){
            salida.println("KO");
            return;
        }

        if(dir.isFile()){
            if(dir.delete()){
                salida.println("OK");
                return;
            }else{
                salida.println("KO");
                return;
            }
        }

        if(dir.isDirectory()){
            File[] hijos = dir.listFiles();

            if(hijos == null){
                salida.println("KO");
                return;
            }

            if(hijos.length > 0){
                salida.println("KO");
                return;
            }

            if(dir.delete()){
                salida.println("OK");
            }else{
                salida.println("KO");
                return;
            }


        }

        salida.println("KO");

    }

    void ejecutarUpload(String ruta, PrintWriter salida, DataInputStream binIn, String ip){
        if(ruta == null || ruta.isBlank()){
            salida.println("KO");
            ServerLogger.log(ip, "RESP: KO (falta ruta en el upload)");
            return;
        }

        //Solo el nombre
        String nombreArchivo = Paths.get(ruta).getFileName().toString();
        Path dest = Paths.get(nombreArchivo).toAbsolutePath().normalize();

        // Si ya existe, KO
        if (Files.exists(dest)) {
            salida.println("KO");
            ServerLogger.log(ip, "RESP: KO (upload exists) " + nombreArchivo);
            return;
        }

        try{
            salida.println("OK");
            ServerLogger.log(ip, "RESP: OK (empezando upload) " + nombreArchivo);

            long tamano = binIn.readLong();
            if(tamano < 0){
                throw new IOException("Tamaño negativo");
            }

            /*
            Creo el archivo de destino en el destino que le he indicado anteriormente
            Lo hago con un OutputStream para pasarlo en bloques y que sea más rápido
             */
            try(OutputStream fos = new BufferedOutputStream(Files.newOutputStream(dest, StandardOpenOption.CREATE_NEW))){
                //Creo el buffer de memoria
                byte[] buffer = new byte[8192];
                //Contador de los bytes que me quedan por leer
                long restante = tamano;

                //Mientras haya bytes por recibir...
                while(restante > 0){
                    //Ajusto para leer los bytes necesarios
                    int paraLeer = (int) Math.min(buffer.length, restante);
                    //Leo hasta donde me indica el paraLeer
                    int leer = binIn.read(buffer, 0, paraLeer);

                    //Por si el cliente se cerró antes de completar el upload
                    if(leer == -1){
                        throw new EOFException("El cliente se cerro durante el upload");
                    }
                    //Escribo en el archivo
                    fos.write(buffer, 0, leer);
                    //Resto lo que ya recibí y cuando llegue a 0 el archivo se habrá mandado por completo
                    restante -= leer;
                }
            }

            ServerLogger.log(ip, "UPLOAD OK " + nombreArchivo + " (" + tamano + " bytes");

        } catch (IOException e) {
            // Si ya se mandó el OK pero falló a mitad, al menos se loguea.
            ServerLogger.log(ip, "FALLO EN EL UPLOAD " + nombreArchivo + " (" + e.getMessage() + ")");
            throw new RuntimeException(e);
        }

    }

    void ejecutarDownload(String ruta, PrintWriter salida, DataOutputStream binOut, String ip) throws IOException {

        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            ServerLogger.log(ip, "RESP: KO (No se encuentra la ruta a la que hacer download)");
            return;
        }

        Path src = Paths.get(ruta).toAbsolutePath().normalize();

        if (!Files.exists(src) || !Files.isRegularFile(src)) {
            salida.println("KO");
            ServerLogger.log(ip, "RESP: KO (no se ha encontrado download ) " + ruta);
            return;
        }

        long tamano = Files.size(src);

        salida.println("OK");
        ServerLogger.log(ip, "RESP: OK (empezando dowload ) " + src.getFileName() + " (" + tamano + " bytes)");

        //Envío tamaño del archivo
        binOut.writeLong(tamano);

        //Abro el archivo para poder leerlo
        try(InputStream fis = new BufferedInputStream(Files.newInputStream(src))){
            //Creo el Buffer
            byte[] buffer = new byte[8192];
            //Leo el archivo y lo mando por el socket
            int leer;
            while((leer = fis.read(buffer)) != -1){
                binOut.write(buffer, 0, leer);
            }
        }
        binOut.flush();

        ServerLogger.log(ip, "DOWNLOAD OK " + src.getFileName());
    }

    void ejecutarMkdir(String ruta, PrintWriter salida) {

        //Si me llega vacío o null, KO directamente
        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            return;
        }

        //Resuelvo la ruta contra mi directorio actual
        Path objetivo = directorioActual.resolve(ruta).normalize();
        File dir = objetivo.toFile();

        //Si ya existe algo con ese nombre, no lo creo
        if (dir.exists()) {
            salida.println("KO");
            return;
        }

        //Intento crear el directorio
        if (dir.mkdir()) {
            salida.println("OK");
        } else {
            salida.println("KO");
        }
    }

    void ejecutarRename(String origen, String destino, PrintWriter salida) {

        //Si falta algo, no lo puedo renombrar
        if (origen == null || origen.isBlank() || destino == null || destino.isBlank()) {
            salida.println("KO");
            return;
        }

        //Resuelvo el origen y destino respecto a mi directorio actual
        File src = directorioActual.resolve(origen).normalize().toFile();
        File dst = directorioActual.resolve(destino).normalize().toFile();

        //Si el origen no existe, no lo puedo renombrar
        if (!src.exists()) {
            salida.println("KO");
            return;
        }

        //Si el destino ya existe, no permito que se sobreescriba
        if (dst.exists()) {
            salida.println("KO");
            return;
        }

        //Intento renombrarlo
        if (src.renameTo(dst)) {
            salida.println("OK");
        } else {
            salida.println("KO");
        }
    }

    void ejecutarExists(String ruta, PrintWriter salida) {

        //Si me llega vacío o null, directamente KO
        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            return;
        }

        //Resuelvo la ruta respecto a mi directorio actual
        File f = directorioActual.resolve(ruta).normalize().toFile();

        if (f.exists()) {
            salida.println("OK");
        } else {
            salida.println("KO");
        }
    }

    void ejecutarInfo(String ruta, PrintWriter salida) {

        //Si me llega vacío o null, no puedo dar info
        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            return;
        }

        //Resuelvo la ruta respecto a mi directorio actual
        File f = directorioActual.resolve(ruta).normalize().toFile();

        //Si no existe, no puedo dar la información
        if (!f.exists()) {
            salida.println("KO");
            return;
        }

        //Si existe, empiezo la respuesta
        salida.println("OK");

        //Indico si es archivo o directorio
        if (f.isDirectory()) {
            salida.println("TYPE: DIRECTORIO");
        } else {
            salida.println("TYPE: ARCHIVO");
        }

        //Tamaño en bytes
        salida.println("TAMAÑO: " + f.length());

        //Última modificación
        salida.println("ULTIMA_MODIFICACIÓN: " + f.lastModified());

        //Línea vacía para marcar el fin (como en list)
        salida.println("");
    }

    void ejecutarPwd(PrintWriter salida) {

        //Devuelvo el directorio actual del cliente
        salida.println("OK");
        salida.println(directorioActual.toString());
    }

    void ejecutarCd(String ruta, PrintWriter salida) {

        //Si me llega vacío o null, no cambio nada
        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            return;
        }

        //Resuelvo la nueva ruta relativa al directorio actual
        Path nueva = directorioActual.resolve(ruta).normalize();
        File dir = nueva.toFile();

        //Si no existe o no es directorio, no cambio nada
        if (!dir.exists() || !dir.isDirectory()) {
            salida.println("KO");
            return;
        }

        //Si es válido, actualizo el estado del cliente
        directorioActual = nueva;

        salida.println("OK");
    }

}
