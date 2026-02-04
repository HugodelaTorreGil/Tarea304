import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

public class MainFileServerApp {

    public static void main(String[] args){

        int puerto = 2121;
        String ruta = "server.properties";
        Properties properties = new Properties();

        try(FileInputStream fileInputStream = new FileInputStream(ruta)){

            properties.load(fileInputStream);

            String fila = properties.getProperty("puerto");

            if(fila != null){
                puerto = Integer.parseInt(fila.trim());
            }

        } catch (IOException | NumberFormatException e) {
        }

        System.out.println("Servidor arrancando en el puerto: " + puerto);

       try(ServerSocket serverSocket = new ServerSocket(puerto)){
           while(true){
               Socket socketCliente = serverSocket.accept();
               new Thread(new ServerWorker(socketCliente)).start();
           }
       } catch (IOException e) {
           e.printStackTrace();
       }
    }

}
