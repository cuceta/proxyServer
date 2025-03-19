import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ProxyServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8080); // Proxy listens on port 8080
        System.out.println("Proxy Server started on port 8080");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected: " + clientSocket.getInetAddress());
            new Thread(new ClientHandler(clientSocket)).start();
        }
    }
}