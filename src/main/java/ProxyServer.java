import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ProxyServer {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java ProxyServer <windowSize>");
            System.exit(1);
        }

        int windowSize = Integer.parseInt(args[0]); // Read window size from command line
        if (windowSize != 1 && windowSize != 8 && windowSize != 64) {
            System.err.println("Invalid window size. Allowed values: 1, 8, 64");
            System.exit(1);
        }

        ServerSocket serverSocket = new ServerSocket(8080); // Proxy listens on port 8080
        System.out.println("Proxy Server started on port 8080 with window size: " + windowSize);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected: " + clientSocket.getInetAddress());
            new Thread(new ClientHandler(clientSocket, windowSize)).start();
        }
    }
}