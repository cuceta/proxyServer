import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Random;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private int key; // Encryption key
    private int windowSize; // Window size
    private static HashMap<String, byte[]> cache = new HashMap<>();

    public ClientHandler(Socket socket, int windowSize) {
        this.clientSocket = socket;
        this.windowSize = windowSize;
    }

    @Override
    public void run() {
        try (InputStream in = clientSocket.getInputStream();
             OutputStream out = clientSocket.getOutputStream()) {
            // Step 1: Perform encryption key exchange
            performKeyExchange(in, out);

            // Step 2: Receive URL from client
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String url = reader.readLine(); // Read URL from client

            // Step 3: Fetch file from target server or cache
            byte[] fileData;
            if (cache.containsKey(url)) {
                fileData = cache.get(url); // Serve from cache
            } else {
                try {
                    fileData = fetchFileFromServer(url); // Fetch from target server
                    cache.put(url, fileData); // Cache the response
                } catch (IOException e) {
                    System.err.println("Failed to fetch file from URL: " + url);
                    fileData = new byte[0]; // Send empty data to indicate failure
                }
            }

            // Step 4: Encrypt and send file to client using sliding windows and RTO
            sendFileWithSlidingWindow(out, fileData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void performKeyExchange(InputStream in, OutputStream out) throws IOException {
        // Step 1: Generate a random number for key exchange
        Random random = new Random();
        int randomNum = random.nextInt();

        // Step 2: Send random number to client
        DataOutputStream dataOut = new DataOutputStream(out);
        dataOut.writeInt(randomNum);

        // Step 3: Receive client's random number
        DataInputStream dataIn = new DataInputStream(in);
        int clientRandomNum = dataIn.readInt();

        // Step 4: Generate encryption key using XOR
        key = randomNum ^ clientRandomNum;
        System.out.println("Encryption key generated: " + key);
    }

    private byte[] fetchFileFromServer(String url) throws IOException {
        URL targetUrl = new URL(url);
        URLConnection connection = targetUrl.openConnection();

        InputStream inputStream;
        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
            httpsConnection.setRequestMethod("GET");
            inputStream = httpsConnection.getInputStream();
        } else if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            httpConnection.setRequestMethod("GET");
            inputStream = httpConnection.getInputStream();
        } else {
            throw new IOException("Unsupported URL protocol: " + url);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private void sendFileWithSlidingWindow(OutputStream out, byte[] fileData) throws IOException {
        int base = 0; // Start of the window
        int nextSeqNum = 0; // Next sequence number to send
        int totalPackets = (int) Math.ceil((double) fileData.length / 1024); // Total packets

        while (base < totalPackets) {
            // Send packets within the window
            while (nextSeqNum < base + windowSize && nextSeqNum < totalPackets) {
                byte[] packetData = getPacketData(fileData, nextSeqNum);
                byte[] encryptedPacket = encrypt(packetData, key); // Encrypt packet
                sendPacket(out, nextSeqNum, encryptedPacket);
                nextSeqNum++;
            }

            // Wait for ACKs for all packets in the window
            int packetsInWindow = Math.min(windowSize, totalPackets - base);
            boolean[] ackReceived = new boolean[packetsInWindow]; // Track ACKs for each packet

            while (true) {
                int ack = receiveAck(clientSocket);
                System.out.println("Received ACK: " + ack);

                if (ack >= base && ack < base + packetsInWindow) {
                    ackReceived[ack - base] = true; // Mark the packet as acknowledged
                }

                // Check if all packets in the window have been acknowledged
                boolean allAcked = true;
                for (boolean ackStatus : ackReceived) {
                    if (!ackStatus) {
                        allAcked = false;
                        break;
                    }
                }

                if (allAcked) {
                    base += packetsInWindow; // Slide the window forward
                    System.out.println("Window slid to base: " + base);
                    break;
                }
            }
        }
        System.out.println("File transmission complete.");
    }

    private byte[] getPacketData(byte[] fileData, int seqNum) {
        int packetSize = 1024;
        int start = seqNum * packetSize;
        int end = Math.min(start + packetSize, fileData.length); // Handle last packet
        byte[] packetData = new byte[end - start];
        System.arraycopy(fileData, start, packetData, 0, end - start);
        return packetData;
    }

    private void sendPacket(OutputStream out, int seqNum, byte[] data) throws IOException {
        DataOutputStream dataOut = new DataOutputStream(out);
        dataOut.writeInt(seqNum); // Send sequence number
        dataOut.writeInt(data.length); // Send packet length
        dataOut.write(data); // Send packet data
        System.out.println("Sent packet with seqNum: " + seqNum);
    }

    private int receiveAck(Socket socket) throws IOException {
        DataInputStream dataIn = new DataInputStream(socket.getInputStream());
        int ack = dataIn.readInt(); // Read ACK
        System.out.println("Received ACK: " + ack); // Log the ACK
        return ack;
    }

    private byte[] encrypt(byte[] data, int key) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key); // XOR encryption
        }
        return result;
    }
}