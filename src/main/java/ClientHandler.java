import java.net.*;
import java.io.*;
import java.util.HashMap;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private static HashMap<String, byte[]> cache = new HashMap<>();

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (InputStream in = clientSocket.getInputStream();
             OutputStream out = clientSocket.getOutputStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String url = reader.readLine(); // Read URL from client

            byte[] fileData;
            if (cache.containsKey(url)) {
                fileData = cache.get(url); // Serve from cache
            } else {
                fileData = fetchFileFromServer(url); // Fetch from target server
                cache.put(url, fileData); // Cache the response
            }

            out.write(fileData); // Send file to client
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] fetchFileFromServer(String url) throws IOException {
        URL targetUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
        connection.setRequestMethod("GET");

        InputStream inputStream = connection.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }
}