package tryTwo;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Random;

public class Client {
    private static final String PROXY_HOST = "localhost";
    private static final int PROXY_PORT = 8080;
    // Hard-coded URL to request
    private static final String URL = "https://blogs.biomedcentral.com/bmcseriesblog/wp-content/uploads/sites/9/2017/03/ChowChow2Szczecin.jpg";

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

            // --- Receive the File Using the Sliding Window Protocol ---
            receiveFileWithSlidingWindow(in, out);

            System.out.println("File downloaded successfully.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void receiveFileWithSlidingWindow(DataInputStream in, DataOutputStream out) throws IOException {
        try (FileOutputStream fileOut = new FileOutputStream("downloaded_file")) {
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
}

