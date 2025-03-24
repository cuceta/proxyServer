package tryTwo;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    private static final Map<String, byte[]> cache = new HashMap<>();
    private Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream()) {

            // Read the requested URL from the client
            String url = in.readLine();
            System.out.println("Client requested: " + url);

            byte[] responseData;
            if (cache.containsKey(url)) {
                System.out.println("Cache hit for: " + url);
                responseData = cache.get(url);
            } else {
                System.out.println("Fetching data for: " + url);
                responseData = fetchFromServer(url);
                cache.put(url, responseData);
            }

            // Send the response to the client
            out.write(responseData);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] fetchFromServer(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        try (InputStream inputStream = connection.getInputStream();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            byte[] data = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            return buffer.toByteArray();
        }
    }
}
