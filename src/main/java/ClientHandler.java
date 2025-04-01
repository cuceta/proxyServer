import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ClientHandler implements Runnable {
    private static final Map<String, byte[]> cache = new HashMap<>();
    private Socket clientSocket;
    private boolean simulateDrop;
    private int windowSize;

    int clientRandom;
    int serverRandom;

    // Updated constructor to accept the windowSize parameter.
    public ClientHandler(Socket clientSocket, boolean simulateDrop, int windowSize) {
        this.clientSocket = clientSocket;
        this.simulateDrop = simulateDrop;
        this.windowSize = windowSize;
    }

    @Override
    public void run() {
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            // --- Encryption Key Exchange ---
            int clientID = in.readInt();
            clientRandom = in.readInt();

            int serverID = new Random().nextInt(1000);
            serverRandom = new Random().nextInt(1000);
            out.writeInt(serverID);
            out.writeInt(serverRandom);
            out.flush();

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
                saveFileToTmp(fileData, url);
            }

            // --- Send File Using TFTP-like Protocol (Sliding Window, RTO, Packet Drop Simulation) ---
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

    private void saveFileToTmp(byte[] fileData, String url) {
        String fileName = sanitizeFileName(url);
        File file = new File("/tmp", fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(fileData);
            System.out.println("Saved file to " + file.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("Error saving file: " + e.getMessage());
        }
    }

    private static String sanitizeFileName(String url) {
        String sanitized = url.replaceAll("https?://", "");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
        return sanitized;
    }

    private void sendFileWithSlidingWindow(byte[] fileData, DataOutputStream out, DataInputStream in) throws IOException {
        final int CHUNK_SIZE = 1024;
        final int WINDOW_SIZE = this.windowSize;
        final int TIMEOUT = 2000; // milliseconds

        int totalPackets = (int) Math.ceil(fileData.length / (double) CHUNK_SIZE);
        boolean[] acked = new boolean[totalPackets];
        int base = 0;

        long key = (long) (clientRandom ^ serverRandom);

        clientSocket.setSoTimeout(TIMEOUT);

        while (base < totalPackets) {
            int windowEnd = Math.min(base + WINDOW_SIZE, totalPackets);

            // Send all packets in the window that haven't been acknowledged.
            for (int seq = base; seq < windowEnd; seq++) {
                if (!acked[seq]) {
                    // Optional: simulate packet drop
                    if (simulateDrop && Math.random() < 0.01) {
                        System.out.println("Simulating drop of packet: " + seq);
                        continue;
                    }
                    int start = seq * CHUNK_SIZE;
                    int length = Math.min(CHUNK_SIZE, fileData.length - start);
                    // Get the current chunk
                    byte[] chunk = new byte[length];
                    System.arraycopy(fileData, start, chunk, 0, length);
                    // Encrypt the chunk
                    byte[] encryptedChunk = Encryption.encryptDecrypt(chunk, key);
                    // Send packet header: sequence number and length, then the encrypted data.
                    out.writeInt(seq);
                    out.writeInt(encryptedChunk.length);
                    out.write(encryptedChunk);
                    out.flush();
                    System.out.println("Sent packet: " + seq);
                }
            }

            // Wait for acknowledgments in the current window.
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < TIMEOUT) {
                try {
                    int ackSeq = in.readInt();
                    System.out.println("Received ACK for packet: " + ackSeq);
                    if (ackSeq >= base && ackSeq < windowEnd) {
                        acked[ackSeq] = true;
                    }
                } catch (SocketTimeoutException e) {
                    break;  // Timeout: retransmit unacknowledged packets in this window.
                }
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
            // Slide the window forward.
            while (base < totalPackets && acked[base]) {
                base++;
            }
        }
        // Send termination packet.
        out.writeInt(-1);
        out.flush();
        System.out.println("File transmission complete.");
    }

}
