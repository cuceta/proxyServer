import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyServer {
    private static final int PORT = 26896;
    private static final int THREAD_POOL_SIZE = 10;

    public static void main(String[] args) {
        // Require a window size argument.
        if (args.length < 1) {
            System.out.println("Usage: java ProxyServer <window_size> [drop]");
            System.exit(1);
        }

        int windowSize = 0;
        try {
            windowSize = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid window size. Must be an integer.");
            System.exit(1);
        }

        // Check for an optional second argument to simulate packet drop.
        boolean simulateDrop = false;
        if (args.length > 1 && args[1].equalsIgnoreCase("drop")) {
            simulateDrop = true;
            System.out.println("Simulating 1% packet loss enabled.");
        }

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Proxy Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Pass the simulateDrop flag and windowSize to each new ClientHandler.
                executorService.execute(new ClientHandler(clientSocket, simulateDrop, windowSize));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
