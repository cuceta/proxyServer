package tryTwo;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ClientHandler implements Runnable {
    private static final Map<String, byte[]> cache = new HashMap<>();
    private Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            // --- Encryption Key Exchange ---
            // Client sends its ID and a random number
            int clientID = in.readInt();
            int clientRandom = in.readInt();

            // Server generates its own ID and random number and sends them
            int serverID = new Random().nextInt(1000);
            int serverRandom = new Random().nextInt(1000);
            out.writeInt(serverID);
            out.writeInt(serverRandom);
            out.flush();

            // Both sides compute the encryption key (here, simply XOR the random numbers)
            int encryptionKey = clientRandom ^ serverRandom;
            System.out.println("Encryption Key Established: " + encryptionKey);

            // --- Read the URL ---
            String url = in.readUTF();
            System.out.println("Client requested: " + url);

            byte[] fileData;
            if (cache.containsKey(url)) {
                System.out.println("Cache hit for: " + url);
                fileData = cache.get(url);
            } else {
                System.out.println("Fetching data for: " + url);
                fileData = fetchFromServer(url);
                cache.put(url, fileData);
            }

            // --- Send File Using TFTP-like Protocol (Sliding Window, RTO) ---
            sendFileWithSlidingWindow(fileData, out, in);

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
            while ((bytesRead = inputStream.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
            return buffer.toByteArray();
        }
    }

    private void sendFileWithSlidingWindow(byte[] fileData, DataOutputStream out, DataInputStream in) throws IOException {
        final int CHUNK_SIZE = 1024;  // bytes per packet
        final int WINDOW_SIZE = 8;      // number of packets in a window
        final int TIMEOUT = 2000;       // timeout in milliseconds

        int totalPackets = (int) Math.ceil(fileData.length / (double) CHUNK_SIZE);
        boolean[] acked = new boolean[totalPackets];
        int base = 0;

        // Set a socket timeout for reading acknowledgments
        clientSocket.setSoTimeout(TIMEOUT);

        while (base < totalPackets) {
            // Determine the current window range
            int windowEnd = Math.min(base + WINDOW_SIZE, totalPackets);

            // Send all packets in the window that have not yet been acknowledged
            for (int seq = base; seq < windowEnd; seq++) {
                if (!acked[seq]) {
                    int start = seq * CHUNK_SIZE;
                    int length = Math.min(CHUNK_SIZE, fileData.length - start);
                    // Packet header: sequence number and length of data
                    out.writeInt(seq);
                    out.writeInt(length);
                    out.write(fileData, start, length);
                    out.flush();
                    System.out.println("Sent packet: " + seq);
                }
            }

            // Wait for acknowledgments for packets in the window
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < TIMEOUT) {
                try {
                    int ackSeq = in.readInt();
                    System.out.println("Received ACK for packet: " + ackSeq);
                    if (ackSeq >= base && ackSeq < windowEnd) {
                        acked[ackSeq] = true;
                    }
                } catch (SocketTimeoutException e) {
                    // If timeout occurs, break to resend any unacknowledged packets in this window
                    break;
                }
                // If all packets in the current window are acknowledged, we can move the window forward
                boolean allAcked = true;
                for (int seq = base; seq < windowEnd; seq++) {
                    if (!acked[seq]) {
                        allAcked = false;
                        break;
                    }
                }
                if (allAcked) {
                    break;
                }
            }

            // Slide the window forward past any acknowledged packets
            while (base < totalPackets && acked[base]) {
                base++;
            }
        }

        // Indicate end of transmission with a special packet (sequence number -1)
        out.writeInt(-1);
        out.flush();
        System.out.println("File transmission complete.");
    }
}
