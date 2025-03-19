import java.net.*;
import java.io.*;

public class Client {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("localhost", 8080); // Connect to proxy server
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        String url = "http://example.com/image.jpg"; // Example URL
        out.write((url + "\n").getBytes()); // Send URL to proxy

        FileOutputStream fileOutputStream = new FileOutputStream("/tmp/received_image.jpg");
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            fileOutputStream.write(buffer, 0, bytesRead);
        }
        fileOutputStream.close();
        System.out.println("File received and saved to /tmp/received_image.jpg");
    }
}