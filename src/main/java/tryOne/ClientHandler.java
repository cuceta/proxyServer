package tryOne;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private static ConcurrentHashMap<String, byte[]> cache = new ConcurrentHashMap<>();
    private final int windowSize = 5;
    private final long timeout = 500; // RTO in ms
    private int key;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (InputStream in = clientSocket.getInputStream();
             OutputStream out = clientSocket.getOutputStream()) {

            key = exchangeKey(in, out); // Ensure key exchange happens correctly

            String request = readRequest(in);
            String fileName = parseFileName(request);

            byte[] fileData = cache.computeIfAbsent(fileName, this::fetchFile);

            if (fileData.length == 0) {
                out.write("HTTP/1.1 404 Not Found\r\n".getBytes());
                return;
            }

            sendFileWithSlidingWindow(out, fileData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int exchangeKey(InputStream in, OutputStream out) throws IOException {
        int clientRand = in.read();
        int serverRand = (int) (Math.random() * 256);
        out.write(serverRand);
        return clientRand ^ serverRand;
    }

    private String readRequest(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        return reader.readLine();
    }

    private String parseFileName(String request) {
        return request.split(" ")[1].substring(1); // Extract filename from GET /file HTTP/1.1
    }

    private byte[] fetchFile(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) return new byte[0];
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private void sendFileWithSlidingWindow(OutputStream out, byte[] fileData) throws IOException {
        int base = 0, nextSeqNum = 0;
        int totalPackets = (int) Math.ceil((double) fileData.length / 1024);
        DataOutputStream dataOut = new DataOutputStream(out);
        DataInputStream dataIn = new DataInputStream(clientSocket.getInputStream());

        while (base < totalPackets) {
            while (nextSeqNum < base + windowSize && nextSeqNum < totalPackets) {
                byte[] packetData = getPacketData(fileData, nextSeqNum);
                byte[] encryptedPacket = encrypt(packetData, key);

                try {
                    sendPacket(dataOut, nextSeqNum, encryptedPacket);
                    System.out.println("Sent packet with seqNum: " + nextSeqNum);
                } catch (IOException e) {
                    System.err.println("Error sending packet: " + e.getMessage());
                    return;
                }

                nextSeqNum++;
            }

            long startTime = System.currentTimeMillis();
            int highestAck = base - 1;

            while (System.currentTimeMillis() - startTime < timeout) {
                try {
                    if (dataIn.available() > 0) {
                        int ack = receiveAck(clientSocket);
                        System.out.println("Received ACK: " + ack);
                        if (ack > highestAck) highestAck = ack;
                    }
                } catch (IOException e) {
                    System.err.println("ACK receive error, possibly client closed connection.");
                    return;
                }
            }

            if (highestAck < base + windowSize - 1) {
                System.out.println("Timeout! Retransmitting...");
                nextSeqNum = base;
            } else {
                base = highestAck + 1;
            }
        }
    }



    private byte[] encrypt(byte[] data, int key) {
        byte[] encrypted = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            encrypted[i] = (byte) (data[i] ^ key);
        }
        return encrypted;
    }

    private void sendPacket(OutputStream out, int seqNum, byte[] data) throws IOException {
        out.write(seqNum);
        out.write(data);
    }

    private int receiveAck(Socket socket) throws IOException {
        return socket.getInputStream().read();
    }

    private byte[] getPacketData(byte[] fileData, int seqNum) {
        int packetSize = 1024; // Define packet size
        int start = seqNum * packetSize;

        if (start >= fileData.length) {
            return new byte[0]; // No more data to send
        }

        int end = Math.min(start + packetSize, fileData.length);
        byte[] packetData = new byte[end - start];

        System.arraycopy(fileData, start, packetData, 0, packetData.length);
        return packetData;
    }
}
