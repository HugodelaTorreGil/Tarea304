import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ServerWorker implements Runnable{

    private final Socket socketCliente;

    public ServerWorker(Socket socketCliente) {
        this.socketCliente = socketCliente;
    }

    @Override
    public void run() {

        try(
                BufferedReader entrada = new BufferedReader(new InputStreamReader(socketCliente.getInputStream()));
                PrintWriter salida = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socketCliente.getOutputStream())))
        ){

            while(true){

                String linea = entrada.readLine();

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
}
