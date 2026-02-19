import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class ComandosParaPracticar {

    //Aquí implemento el comando ping para comprobar que el/la cliente está conectado/a
    if (linea.equals("ping")) {
        ejecutarPing(salida);
        continue;
    }

    //Aquí implemento el comando help para devolver la lista de comandos disponibles
    if (linea.equals("help")) {
        ejecutarHelp(salida);
        continue;
    }

    //Aquí implemento pwd para devolver el directorio actual del/la cliente
    if (linea.equals("pwd")) {
        ejecutarPwd(salida);
        continue;
    }

    //Aquí implemento cd para cambiar el directorio actual del/la cliente
    if (linea.startsWith("cd ")) {
        //Aquí saco la ruta después del comando
        String ruta = linea.substring(3).trim();
        ejecutarCd(ruta, salida);
        continue;
    }

    //Aquí implemento mkdir para crear un directorio en el cwd del/la cliente
    if (linea.startsWith("mkdir ")) {
        //Aquí saco la ruta del directorio a crear
        String ruta = linea.substring(6).trim();
        ejecutarMkdir(ruta, salida);
        continue;
    }

    //Aquí implemento rename para renombrar un archivo o directorio
    if (linea.startsWith("rename ")) {
        //Aquí saco lo que viene después de rename
        String params = linea.substring(7).trim();
        //Aquí separo origen y destino por espacios
        String[] partes = params.split("\\s+");
        //Aquí valido que vengan exactamente 2 cosas
        if (partes.length != 2) {
            salida.println("KO");
            continue;
        }
        ejecutarRename(partes[0], partes[1], salida);
        continue;
    }

    //Aquí implemento exists para comprobar si existe una ruta
    if (linea.startsWith("exists ")) {
        //Aquí saco la ruta a comprobar
        String ruta = linea.substring(7).trim();
        ejecutarExists(ruta, salida);
        continue;
    }

    //Aquí implemento info para sacar metadatos básicos
    if (linea.startsWith("info ")) {
        //Aquí saco la ruta a inspeccionar
        String ruta = linea.substring(5).trim();
        ejecutarInfo(ruta, salida);
        continue;
    }

    //Aquí implemento touch para crear un archivo vacío
    if (linea.startsWith("touch ")) {
        //Aquí saco la ruta del archivo a crear
        String ruta = linea.substring(6).trim();
        try {
            ejecutarTouch(ruta, salida);
        } catch (IOException e) {
            salida.println("KO");
        }
        continue;
    }

    void ejecutarMkdir(String ruta, PrintWriter salida) {

        //Aquí si me llega vacío o null, KO directamente
        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            return;
        }

        //Aquí resuelvo la ruta contra mi directorio actual
        Path objetivo = directorioActual.resolve(ruta).normalize();
        File dir = objetivo.toFile();

        //Aquí si ya existe algo con ese nombre, no lo creo
        if (dir.exists()) {
            salida.println("KO");
            return;
        }

        //Aquí intento crear el directorio
        if (dir.mkdir()) {
            salida.println("OK");
        } else {
            salida.println("KO");
        }
    }

    void ejecutarRename(String origen, String destino, PrintWriter salida) {

        //Aquí si falta algo, no lo puedo renombrar
        if (origen == null || origen.isBlank() || destino == null || destino.isBlank()) {
            salida.println("KO");
            return;
        }

        //Aquí resuelvo el origen y el destino respecto a mi directorio actual
        File src = directorioActual.resolve(origen).normalize().toFile();
        File dst = directorioActual.resolve(destino).normalize().toFile();

        //Aquí si el origen no existe, KO
        if (!src.exists()) {
            salida.println("KO");
            return;
        }

        //Aquí si el destino ya existe, KO porque no sobreescribo
        if (dst.exists()) {
            salida.println("KO");
            return;
        }

        //Aquí intento renombrarlo
        if (src.renameTo(dst)) {
            salida.println("OK");
        } else {
            salida.println("KO");
        }
    }

    void ejecutarExists(String ruta, PrintWriter salida) {

        //Aquí si me llega vacío o null, KO
        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            return;
        }

        //Aquí resuelvo la ruta respecto a mi directorio actual
        File f = directorioActual.resolve(ruta).normalize().toFile();

        //Aquí si existe digo OK, si no existe digo KO
        if (f.exists()) {
            salida.println("OK");
        } else {
            salida.println("KO");
        }
    }

    void ejecutarInfo(String ruta, PrintWriter salida) {

        //Aquí si me llega vacío o null, KO
        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            return;
        }

        //Aquí resuelvo la ruta respecto a mi directorio actual
        File f = directorioActual.resolve(ruta).normalize().toFile();

        //Aquí si no existe, KO
        if (!f.exists()) {
            salida.println("KO");
            return;
        }

        //Aquí empiezo la respuesta
        salida.println("OK");

        //Aquí indico el tipo
        if (f.isDirectory()) {
            salida.println("TYPE: DIRECTORIO");
        } else {
            salida.println("TYPE: ARCHIVO");
        }

        //Aquí mando el tamaño en bytes
        salida.println("TAMAÑO: " + f.length());

        //Aquí mando la última modificación (epoch millis)
        salida.println("ULTIMA_MODIFICACIÓN: " + f.lastModified());

        //Aquí marco el fin con una línea vacía
        salida.println("");
    }

    void ejecutarPwd(PrintWriter salida) {

        //Aquí devuelvo el directorio actual del/la cliente
        salida.println("OK");
        salida.println(directorioActual.toString());
    }

    void ejecutarCd(String ruta, PrintWriter salida) {

        //Aquí si me llega vacío o null, no cambio nada
        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            return;
        }

        //Aquí calculo la nueva ruta relativa al directorio actual
        Path nueva = directorioActual.resolve(ruta).normalize();
        File dir = nueva.toFile();

        //Aquí si no existe o no es directorio, KO
        if (!dir.exists() || !dir.isDirectory()) {
            salida.println("KO");
            return;
        }

        //Aquí si es válido, actualizo el directorio actual del/la cliente
        directorioActual = nueva;

        salida.println("OK");
    }

    void ejecutarPing(PrintWriter salida) {
        //Aquí contesto OK para que el/la cliente sepa que estoy vivo
        salida.println("OK");
    }

    void ejecutarHelp(PrintWriter salida) {
        //Aquí empiezo la lista de comandos
        salida.println("OK");
        salida.println("list <ruta>");
        salida.println("show <ruta>");
        salida.println("delete <ruta>");
        salida.println("upload <ruta_local_cliente>");
        salida.println("download <ruta_servidor>");
        salida.println("mkdir <dir>");
        salida.println("rename <src> <dst>");
        salida.println("exists <ruta>");
        salida.println("info <ruta>");
        salida.println("pwd");
        salida.println("cd <dir>");
        salida.println("ping");
        salida.println("touch <archivo>");
        salida.println("help");
        salida.println("quit");
        //Aquí cierro la respuesta con una línea vacía para que el/la cliente sepa que terminó
        salida.println("");
    }

    void ejecutarTouch(String ruta, PrintWriter salida) throws IOException {
        //Aquí si me llega vacío o null, KO
        if (ruta == null || ruta.isBlank()) {
            salida.println("KO");
            return;
        }

        //Aquí resuelvo la ruta respecto al directorio actual
        Path p = directorioActual.resolve(ruta).normalize();

        //Aquí si ya existe, KO porque no quiero sobreescribir
        if (Files.exists(p)) {
            salida.println("KO");
            return;
        }

        //Aquí creo el archivo vacío
        Files.createFile(p);
        salida.println("OK");
    }
}
