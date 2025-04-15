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

            // --- Send File Using TFTP-like Protocol with TCP-Like RTO ---
            sendFileWithSlidingWindow(fileData, out, in, encryptionKey);

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

    /**
     * Sends the file using a sliding window protocol with dynamic retransmission timeout (RTO)
     * that is updated according to a TCP-like RTT measurement scheme.
     *
     * @param fileData      The file data to send.
     * @param out           The output stream to send packets.
     * @param in            The input stream to receive ACKs.
     * @param encryptionKey The key used for encrypting packets.
     * @throws IOException If an I/O error occurs.
     */
    private void sendFileWithSlidingWindow(byte[] fileData, DataOutputStream out, DataInputStream in, int encryptionKey) throws IOException {
        final int CHUNK_SIZE = 1024;
        final int WINDOW_SIZE = this.windowSize;

        // TCP-like RTT estimation constants.
        final double INITIAL_R = 1000.0; // starting RTT in ms (1sec)
        final double ALPHA = 0.125;      // SRTT weight factor
        final double BETA  = 0.25;       // RTTVAR weight factor
        final double K = 4.0;            // multiplier for RTTVAR in RTO
        final double G = 17.0;           // minimum timeout granularity in ms

        // Initialize RTT estimators.
        double SRTT = INITIAL_R;
        double RTTVAR = INITIAL_R / 2.0;
        double RTO = SRTT + Math.max(K * RTTVAR, G);  // initial RTO

        int totalPackets = (int) Math.ceil(fileData.length / (double) CHUNK_SIZE);
        boolean[] acked = new boolean[totalPackets];
        boolean[] firstTransmission = new boolean[totalPackets];
        long[] sentTimes = new long[totalPackets];
        // Mark all packets as pending first transmission.
        for (int i = 0; i < totalPackets; i++) {
            firstTransmission[i] = true;
        }
        int base = 0;

        // Set an initial socket timeout.
        clientSocket.setSoTimeout((int) RTO);

        // Loop until all packets have been acknowledged.
        while (base < totalPackets) {
            int windowEnd = Math.min(base + WINDOW_SIZE, totalPackets);

            // Send all packets in the current window that have not been acknowledged.
            for (int seq = base; seq < windowEnd; seq++) {
                if (!acked[seq]) {
                    // Simulate packet drop if enabled.
                    if (simulateDrop && Math.random() < 0.01) {
                        System.out.println("Simulating drop of packet: " + seq);
                        continue;
                    }
                    int start = seq * CHUNK_SIZE;
                    int length = Math.min(CHUNK_SIZE, fileData.length - start);
                    byte[] chunk = new byte[length];
                    System.arraycopy(fileData, start, chunk, 0, length);

                    // Encrypt the chunk.
                    byte[] encryptedChunk = Encryption.encryptDecrypt(chunk, encryptionKey);

                    // Send packet header: sequence number and length, then the encrypted data.
                    out.writeInt(seq);
                    out.writeInt(encryptedChunk.length);
                    out.write(encryptedChunk);
                    out.flush();
                    System.out.println("Sent packet: " + seq);

                    // Record send time for RTT measurement if this is the first transmission.
                    if (firstTransmission[seq]) {
                        sentTimes[seq] = System.currentTimeMillis();
                    }
                }
            }

            // Wait for ACKs in the current window using the current dynamic RTO.
            long windowStartTime = System.currentTimeMillis();
            while (true) {
                long now = System.currentTimeMillis();
                long elapsed = now - windowStartTime;
                if (elapsed >= RTO) {
                    System.out.println("Timeout reached for current window (RTO = " + RTO + " ms). Retransmitting unacked packets.");
                    break;
                }
                // Adjust socket timeout for the remaining time in this window.
                int remainingTimeout = (int) (RTO - elapsed);
                clientSocket.setSoTimeout(remainingTimeout);
                try {
                    int ackSeq = in.readInt();
                    System.out.println("Received ACK for packet: " + ackSeq);
                    if (ackSeq >= base && ackSeq < windowEnd && !acked[ackSeq]) {
                        acked[ackSeq] = true;
                        // Update RTT estimates only if this ACK is for a packet's first transmission.
                        if (firstTransmission[ackSeq]) {
                            long measuredRTT = now - sentTimes[ackSeq];
                            SRTT = ALPHA * measuredRTT + (1 - ALPHA) * SRTT; // Update SRTT.
                            RTTVAR = BETA * Math.abs(measuredRTT - SRTT) + (1 - BETA) * RTTVAR;  // Update RTTVAR (using absolute error).
                            RTO = SRTT + Math.max(K * RTTVAR, G); // Recalculate  RTO.
                            System.out.println("Updated RTT metrics -- measured RTT: " + measuredRTT +
                                    " ms, SRTT: " + SRTT + " ms, RTTVAR: " + RTTVAR +
                                    " ms, new RTO: " + RTO + " ms");
                            // Mark that we do not update RTT for retransmitted packets.
                            firstTransmission[ackSeq] = false;
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // No ACK received within the remaining timeout.
                    System.out.println("Socket timed out waiting for ACKs in the current window.");
                    break;
                }
                // If all packets in the window are acknowledged, exit the waiting loop.
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

            // Slide the window forward past all acknowledged packets.
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
