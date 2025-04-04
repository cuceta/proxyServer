import java.io.*;
import java.net.Socket;
import java.util.Random;

public class Client {
    private static final String PROXY_HOST = "localhost";
    private static final int PROXY_PORT = 26896;

    private static String host_to_server;

    // Image URL and parameters.
    private static String URL;
    private static String drop;
    private static String windowSize;

    public static void main(String[] args) {
        // Expected args: URL, windowSize, drop, host_to_server
        URL = args[0];
        windowSize = args[1];
        drop = args[2];
        host_to_server = args[3];

        try (Socket socket = new Socket(PROXY_HOST, PROXY_PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            // --- Encryption Key Exchange ---
            int clientID = 123;
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

            // Determine file name based on URL.
            String fileName = "drop_" + drop + "_" + windowSize + "_" + sanitizeFileName(URL);
            System.out.println("Saving downloaded file as: " + fileName);

            // --- Measure Throughput ---
            long startTime = System.nanoTime();
            receiveFileWithSlidingWindow(in, out, fileName, encryptionKey);
            long endTime = System.nanoTime();

            // Calculate file size and throughput in Mbps.
            File downloadedFile = new File(host_to_server + File.separator + "temp", fileName);
            long fileSizeBytes = downloadedFile.length();
            double elapsedSeconds = (endTime - startTime) / 1e9;
            double throughputBps = (fileSizeBytes * 8) / elapsedSeconds;
            double throughputMbps = throughputBps / 1e6;

            System.out.println("File Size (bytes): " + fileSizeBytes);
            System.out.println("Elapsed Time (seconds): " + elapsedSeconds);
            System.out.println("Throughput (Mbps): " + throughputMbps);

            // Generate an HTML report.
            generateReport(fileSizeBytes, elapsedSeconds, throughputMbps, downloadedFile);
            // Generate a JSON report.
            generateJsonReport(fileSizeBytes, elapsedSeconds, throughputMbps);

            // --- File Integrity Validation using cmp ---
            String downloadedFilePath = host_to_server + File.separator + "temp" + File.separator + fileName;
            String referenceFilePath = "reference" + File.separator + sanitizeFileName(URL);
            validateFileIntegrity(downloadedFilePath, referenceFilePath);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void receiveFileWithSlidingWindow(DataInputStream in, DataOutputStream out, String fileName, long key)
            throws IOException {
        File tempDir = new File(host_to_server + File.separator + "temp");
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
                if (seq == -1) {  // Termination packet.
                    break;
                }
                int length = in.readInt();
                byte[] encryptedBuffer = new byte[length];
                in.readFully(encryptedBuffer);
                // Decrypt the received data.
                byte[] buffer = Encryption.encryptDecrypt(encryptedBuffer, key);
                fileOut.write(buffer);
                fileOut.flush();
                // Send acknowledgment.
                out.writeInt(seq);
                out.flush();
                System.out.println("Sent ACK for packet: " + seq);
            }
        }
    }

    private static String sanitizeFileName(String url) {
        String sanitized = url.replaceAll("https?://", "");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9\\.\\-&]", "_");
        return sanitized;
    }

    private static void validateFileIntegrity(String downloadedFilePath, String referenceFilePath)
            throws IOException, InterruptedException {
        String command = "cmp " + downloadedFilePath + " " + referenceFilePath;
        System.out.println("Validating file integrity with command: " + command);
        Process process = Runtime.getRuntime().exec(command);
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            System.out.println("File integrity validated: files match.");
        } else {
            System.out.println("File integrity check failed: files differ.");
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    System.out.println(line);
                }
            }
        }
    }

    private static void generateReport(long fileSize, double elapsedSeconds, double throughputMbps, File downloadedFile)
            throws IOException {
        File resultDir = new File(host_to_server + File.separator + "htmlFiles");
        if (!resultDir.exists()) {
            resultDir.mkdirs();
        }
        String htmlFileName = "drop_" + drop + "_" + windowSize + "_throughput.html";
        String htmlContent =
                "<div style='padding: 25px 50px;'> \n " +
                        "<h1>Throughput Report for simulation with " + drop + " drop simulation and a " + windowSize + " window size.</h1> \n" +
                        "<div style='display: flex; flex-wrap: wrap;'> \n" +
                        "<div style='flex: 0 0 40%;'> \n" +
                        "<img src='" + downloadedFile + "' style='width: 100%; height: auto;'/> \n" +
                        "</div> \n" +
                        "<div style='flex: 1; padding-left: 20px;'> \n" +
                        "<p>File Size: " + fileSize + " bytes</p> \n" +
                        "<p>Elapsed Time: " + String.format("%.3f", elapsedSeconds) + " seconds</p> \n" +
                        "<p>Throughput: " + throughputMbps + " Mbps</p> \n" +
                        "</div>\n" +
                        "</div> \n";
        File outputFile = new File(resultDir, htmlFileName);
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(htmlContent);
        }
        System.out.println("Throughput report generated: " + htmlFileName);
    }

    private static void generateJsonReport(long fileSize, double elapsedSeconds, double throughputMbps)
            throws IOException {
        File jsonDir = new File(host_to_server + File.separator + "jsonResults");
        if (!jsonDir.exists()) {
            jsonDir.mkdirs();
        }
        String jsonFileName = "drop_" + drop + "_" + windowSize + "_throughput.json";
        String jsonContent = "{\n" +
                "  \"fileSize\": " + fileSize + ",\n" +
                "  \"elapsedTime\": " + String.format("%.3f", elapsedSeconds) + ",\n" +
                "  \"throughputMbps\": " + throughputMbps + "\n" +
                "}";
        File outputFile = new File(jsonDir, jsonFileName);
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(jsonContent);
        }
        System.out.println("JSON throughput report generated: " + jsonFileName);
    }
}
