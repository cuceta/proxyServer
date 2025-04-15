import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class Client {
//    private static final String PROXY_HOST = "localhost";
//        private static final String PROXY_HOST = "rho.cs.oswego.edu";
        private static final String PROXY_HOST = "moxie.cs.oswego.edu";

    private static final int PROXY_PORT = 26896;

    // ICE CREAM image URL is now provided via command line arguments.
    private static String URL;
    private static String drop;
    private static String windowSize;
    private static String host_to_server;

    public static void main(String[] args) {
        URL = args[0];
        windowSize = args[1];
        drop = args[2];
        host_to_server = args[3];

        try (Socket socket = new Socket(PROXY_HOST, PROXY_PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            // --- Encryption Key Exchange ---
            int clientID = 123;  // Arbitrary client ID.
            int clientRandom = new Random().nextInt(1000);
            out.writeInt(clientID);
            out.writeInt(clientRandom);
            out.flush();

            int serverID = in.readInt();
            int serverRandom = in.readInt();
            int encryptionKey = clientRandom ^ serverRandom;
//            System.out.println("Encryption Key Established: " + encryptionKey);

            // --- Send the URL ---
            out.writeUTF(URL);
            out.flush();

            // Determine file name based on URL.
            String fileName = "drop_" + drop + "_" + windowSize + "_" + sanitizeFileName(URL);

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

//            System.out.println("File Size (bytes): " + fileSizeBytes);
//            System.out.println("Elapsed Time (seconds): " + elapsedSeconds);
//            System.out.println("Throughput (Mbps): " + throughputMbps);

            // Generate an HTML report with throughput information and save CSV.
            generateReport(fileSizeBytes, elapsedSeconds, throughputMbps, downloadedFile);

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
        // Create the temporary directory if it doesn't exist.
        File tempDir = new File(host_to_server + File.separator + "temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        File outputFile = new File(tempDir, fileName);

        // Use a TreeMap to hold packets in sorted order by sequence number.
        TreeMap<Integer, byte[]> packetMap = new TreeMap<>();

        // Read packets until termination.
        while (true) {
            int seq;
            try {
                seq = in.readInt();
            } catch (EOFException e) {
//                System.out.println("End of stream reached.");
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

            // Store the packet by its sequence number.
            packetMap.put(seq, buffer);

            // Send an acknowledgment for this packet.
            out.writeInt(seq);
            out.flush();
//            System.out.println("Sent ACK for packet: " + seq);
        }

        // After all packets are received, write them in the correct order.
        try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
            for (Map.Entry<Integer, byte[]> entry : packetMap.entrySet()) {
                fileOut.write(entry.getValue());
            }
        }
    }

    private static String sanitizeFileName(String url) {
        // Remove the protocol part and replace non-alphanumeric characters with underscores.
        String sanitized = url.replaceAll("https?://", "");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9\\.\\-&]", "_");
        return sanitized;
    }

    private static void validateFileIntegrity(String downloadedFilePath, String referenceFilePath)
            throws IOException, InterruptedException {
        String command = "cmp " + downloadedFilePath + " " + referenceFilePath;
//        System.out.println("Validating file integrity with command: " + command);
        Process process = Runtime.getRuntime().exec(command);
        int exitCode = process.waitFor();

        if (exitCode == 0) {
//            System.out.println("File integrity validated: files match.");
        } else {
//            System.out.println("File integrity check failed: files differ.");
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
//                    System.out.println(line);
                }
            }
        }
    }

    private static void generateReport(long fileSize, double elapsedSeconds, double throughputMbps, File downloadedFile)
            throws IOException {
        File resultDirHTML = new File(host_to_server + File.separator + "htmlFiles");
        if (!resultDirHTML.exists()) {
            resultDirHTML.mkdirs();
        }

        File resultDirCSV = new File(host_to_server + File.separator + "resultsCSV");
        if (!resultDirCSV.exists()) {
            resultDirCSV.mkdirs();
        }
        // Create a base name for both the HTML and CSV files.
        String baseName = "drop_" + drop + "_" + windowSize + "_throughput";
        String htmlFileName = baseName + ".html";
        String htmlContent =
                "<div style='padding: 25px 50px;'> \n" +
                        "  <h1>Throughput Report for simulation with " + drop + " drop simulation and a " + windowSize + " window size.</h1> \n" +
                        "  <div style='display: flex; flex-wrap: wrap;'> \n" +
                        "    <div style='flex: 0 0 40%;'> \n" +
                        "      <img src='" + downloadedFile + "' style='width: 100%; height: auto;'/> \n" +
                        "    </div> \n" +
                        "    <div style='flex: 1; padding-left: 20px; align-content:end;'> \n" +
                        "      <p>File Size: " + fileSize + " bytes</p> \n" +
                        "      <p>Elapsed Time: " + String.format("%.3f", elapsedSeconds) + " seconds</p> \n" +
                        "      <p>Throughput: " + throughputMbps + " Mbps</p> \n" +
                        "    </div>\n" +
                        "  </div> \n" +
                        "</div> \n";

        // Save the HTML report.
        File htmlFile = new File(resultDirHTML, htmlFileName);
        try (FileWriter writer = new FileWriter(htmlFile)) {
            writer.write(htmlContent);
        }
//        System.out.println("Throughput report generated: " + htmlFileName);

        // Save the throughput data in a CSV file.
        String csvFileName = baseName + ".csv";
        File csvFile = new File(resultDirCSV, csvFileName);
        try (FileWriter csvWriter = new FileWriter(csvFile)) {
            // Write header and data row.
            csvWriter.write("FileSize,ElapsedTime,Throughput\n");
            csvWriter.write(fileSize + "," + elapsedSeconds + "," + throughputMbps + "\n");
        }
//        System.out.println("Throughput data saved to CSV: " + csvFileName);
    }
}
