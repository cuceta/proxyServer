package tryTwo;
import java.io.*;
import java.net.Socket;

public class Client {
    private static final String PROXY_HOST = "localhost";
    private static final int PROXY_PORT = 8080;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Client <URL>");
            return;
        }

        String url = args[0];

        try (Socket socket = new Socket(PROXY_HOST, PROXY_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             InputStream in = socket.getInputStream();
             FileOutputStream fileOut = new FileOutputStream("downloaded_file")) {

            out.println(url);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
            }
            System.out.println("File downloaded successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
