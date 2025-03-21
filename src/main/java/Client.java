import java.io.*;
import java.net.*;
import java.util.Random;

import static java.lang.System.out;

public class Client {
    private static final int SERVER_PORT = 8080;
    private static final String SERVER_HOST = "localhost";
    private static final String FILE_NAME = "received_image.jpg";

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            // Step 1: Perform encryption key exchange
            int key = performKeyExchange(in, out);

            // Step 2: Send URL to proxy server
            String url = "https://images.unsplash.com/photo-1726137569888-ce43cc13e414?q=80&w=3087&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDF8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D.jpg";
            sendRequest(out, url);

            // Step 3: Receive file
            byte[] fileData = receiveFileWithSlidingWindow(in, key);
            saveFile(FILE_NAME, fileData);

            System.out.println("File received and saved to: " + new File(FILE_NAME).getAbsolutePath());

            // Wait before closing to allow final ACKs
            Thread.sleep(500);

        } catch (IOException | InterruptedException e) {
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int performKeyExchange(InputStream in, OutputStream out) throws IOException {
        Random random = new Random();
        int clientRandom = random.nextInt();

        DataInputStream dataIn = new DataInputStream(in);
        DataOutputStream dataOut = new DataOutputStream(out);

        int serverRandom = dataIn.readInt();
        dataOut.writeInt(clientRandom);
        dataOut.flush();

        int key = clientRandom ^ serverRandom;
        System.out.println("Encryption key generated: " + key);
        return key;
    }

    private static void sendRequest(OutputStream out, String url) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out))) {
            writer.write(url + "\n");
            writer.flush();
            System.out.println("Request sent: " + url);
        }
    }

    private static byte[] receiveFileWithSlidingWindow(InputStream in, int key) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataInputStream dataIn = new DataInputStream(in);
        int expectedSeqNum = 0;
        int totalPackets = Integer.MAX_VALUE;

        while (expectedSeqNum < totalPackets) {
            try {
                int seqNum = dataIn.readInt();
                int packetLength = dataIn.readInt();
                byte[] packetData = new byte[packetLength];
                dataIn.readFully(packetData);

                out.println("Received packet: SeqNum=" + seqNum + ", Length=" + packetLength);

                byte[] decryptedPacket = decrypt(packetData, key);

                if (seqNum == expectedSeqNum) {
                    buffer.write(decryptedPacket);
                    expectedSeqNum++;

                    if (packetLength < 1024) {
                        totalPackets = expectedSeqNum;
                    }
                }

                sendAck(out, seqNum);
            } catch (EOFException e) {
                System.err.println("End of stream reached unexpectedly.");
                break;
            }
        }

        out.println("File reception complete.");
        return buffer.toByteArray();
    }

    private static void sendAck(OutputStream out, int ack) throws IOException {
        DataOutputStream dataOut = new DataOutputStream(out);
        dataOut.writeInt(ack);
        dataOut.flush();
        System.out.println("Sent ACK: " + ack);
    }

    private static byte[] decrypt(byte[] data, int key) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key);
        }
        return result;
    }

    private static void saveFile(String fileName, byte[] fileData) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(fileName)) {
            fileOutputStream.write(fileData);
            out.println("File saved successfully: " + fileName);
        } catch (IOException e) {
            System.err.println("Error saving file: " + e.getMessage());
        }
    }
}
