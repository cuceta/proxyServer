package tryTwo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer {
    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 10;

    public static void main(String[] args) {
        // Check for command-line argument to simulate packet drop.
        boolean simulateDrop = false;
        if (args.length > 0 && args[0].equalsIgnoreCase("drop")) {
            simulateDrop = true;
            System.out.println("Simulating 1% packet loss enabled.");
        }

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Proxy Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Pass the simulateDrop flag to each new ClientHandler.
                executorService.execute(new ClientHandler(clientSocket, simulateDrop));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}