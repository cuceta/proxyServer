import java.io.*;
import java.net.*;
import java.util.Random;

import static java.lang.System.out;

public class Client {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("localhost", 8080); // Connect to proxy server
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();

        // Step 1: Perform encryption key exchange
        int key = performKeyExchange(in, out);

        // Step 2: Send URL to proxy server
        String url = "https://images.unsplash.com/photo-1726137569888-ce43cc13e414?q=80&w=3087&auto=format&fit=crop&ixlib=rb-4.0.3&ixid=M3wxMjA3fDF8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D.jpg"; // Example URL
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
        writer.write(url + "\n");
        writer.flush();

        // Step 3: Receive file from proxy server using sliding windows and RTO
        byte[] fileData = receiveFileWithSlidingWindow(in, key);

        // Step 4: Save file to the current working directory
        String fileName = "received_image.jpg"; // File name
        FileOutputStream fileOutputStream = new FileOutputStream(fileName); // Save in the same directory
        fileOutputStream.write(fileData);
        fileOutputStream.close();
        System.out.println("File received and saved to: " + new File(fileName).getAbsolutePath());

        // Close the socket after all operations are complete
        socket.close();
    }

    private static int performKeyExchange(InputStream in, OutputStream out) throws IOException {
        // Step 1: Generate a random number for key exchange
        Random random = new Random();
        int randomNum = random.nextInt();

        // Step 2: Receive server's random number
        DataInputStream dataIn = new DataInputStream(in);
        int serverRandomNum = dataIn.readInt();

        // Step 3: Send client's random number to server
        DataOutputStream dataOut = new DataOutputStream(out);
        dataOut.writeInt(randomNum);

        // Step 4: Generate encryption key using XOR
        int key = randomNum ^ serverRandomNum;
        System.out.println("Encryption key generated: " + key);
        return key;
    }

    private static byte[] receiveFileWithSlidingWindow(InputStream in, int key) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int expectedSeqNum = 0; // Expected sequence number
        int totalPackets = Integer.MAX_VALUE; // Unknown total packets initially

        while (expectedSeqNum < totalPackets) {
            try {
                // Receive packet
                DataInputStream dataIn = new DataInputStream(in);
                int seqNum = dataIn.readInt(); // Read sequence number
                int packetLength = dataIn.readInt(); // Read packet length
                byte[] packetData = new byte[packetLength];
                dataIn.readFully(packetData); // Read packet data

                // Log the received packet
                out.println("Received packet with seqNum: " + seqNum + ", length: " + packetLength);

                // Decrypt packet
                byte[] decryptedPacket = decrypt(packetData, key);

                if (seqNum == expectedSeqNum) {
                    buffer.write(decryptedPacket); // Write packet to buffer
                    sendAck(out, expectedSeqNum); // Send ACK
                    expectedSeqNum++;

                    // Check for end of file
                    if (packetLength < 1024) {
                        totalPackets = expectedSeqNum; // Last packet received
                    }
                } else {
                    // Send duplicate ACK for the last correctly received packet
                    sendAck(out, expectedSeqNum - 1);
                }
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
        dataOut.writeInt(ack); // Send ACK
        System.out.println("Sent ACK: " + ack); // Log the ACK
    }

    private static byte[] decrypt(byte[] data, int key) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key); // XOR decryption
        }
        return result;
    }
}