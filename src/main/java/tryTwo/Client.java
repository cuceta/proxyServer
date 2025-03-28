package tryTwo;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Random;

public class Client {
    private static final String PROXY_HOST = "localhost";
    private static final int PROXY_PORT = 8080;

    // CHOW CHOW IMAGE
//    private static final String URL = "https://blogs.biomedcentral.com/bmcseriesblog/wp-content/uploads/sites/9/2017/03/ChowChow2Szczecin.jpg";

    // FLOWERS IMAGE
    private static final String URL = "https://images.contentstack.io/v3/assets/bltcedd8dbd5891265b/blt682575f35cf2888d/6668cee126c2a688bd1aa8b1/Birthday-Flowers-Colors.jpg?q=70&width=1200&auto=webp.jpg";

    //BEACH
//    private static final String URL = "https://www.celebritycruises.com/blog/content/uploads/2021/07/best-beaches-in-dominican-republic-playa-la-ensenada-1600x890.jpg";

    public static void main(String[] args) {
        try (Socket socket = new Socket(PROXY_HOST, PROXY_PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            // --- Encryption Key Exchange ---
            int clientID = 123;  // Arbitrary client ID
            int clientRandom = new Random().nextInt(1000);
            out.writeInt(clientID);
            out.writeInt(clientRandom);
            out.flush();

            int serverID = in.readInt();
            int serverRandom = in.readInt();
            int encryptionKey = clientRandom ^ serverRandom;
            System.out.println("Encryption Key Established: " + encryptionKey);

            // --- Send the URL ---
            out.writeUTF(URL);
            out.flush();

            // Determine file name based on URL
            String fileName = sanitizeFileName(URL);
            System.out.println("Saving downloaded file as: " + fileName);

            // --- Receive the File Using the Sliding Window Protocol ---
            receiveFileWithSlidingWindow(in, out, fileName);

            System.out.println("File downloaded successfully.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void receiveFileWithSlidingWindow(DataInputStream in, DataOutputStream out, String fileName) throws IOException {
        // Use relative "temp" directory instead of "/temp"
        File tempDir = new File("temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        File outputFile = new File(tempDir, fileName);
        try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
            while (true) {
                int seq;
                try {
                    seq = in.readInt();
                } catch (EOFException e) {
                    System.out.println("End of stream reached.");
                    break;
                }
                if (seq == -1) {
                    // End of transmission indicator
                    break;
                }
                int length = in.readInt();
                byte[] buffer = new byte[length];
                in.readFully(buffer);
                fileOut.write(buffer);
                fileOut.flush();
                // Send acknowledgment for the received packet
                out.writeInt(seq);
                out.flush();
                System.out.println("Sent ACK for packet: " + seq);
            }
        }
    }

    private static String sanitizeFileName(String url) {
        // Remove protocol part and replace non-alphanumeric characters with underscores
        String sanitized = url.replaceAll("https?://", "");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
        return sanitized;
    }
}