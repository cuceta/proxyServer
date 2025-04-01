import java.io.*;
import java.net.Socket;
import java.util.Random;

public class Client {
    private static final String PROXY_HOST = "localhost";
    private static final int PROXY_PORT = 8080;

    // CHOW CHOW IMAGE
//    private static final String URL = "https://blogs.biomedcentral.com/bmcseriesblog/wp-content/uploads/sites/9/2017/03/ChowChow2Szczecin.jpg";

    // FLOWERS IMAGE
//    private static final String URL = "https://images.contentstack.io/v3/assets/bltcedd8dbd5891265b/blt682575f35cf2888d/6668cee126c2a688bd1aa8b1/Birthday-Flowers-Colors.jpg?q=70&width=1200&auto=webp.jpg";

    //BEACH
//    private static final String URL = "https://www.celebritycruises.com/blog/content/uploads/2021/07/best-beaches-in-dominican-republic-playa-la-ensenada-1600x890.jpg";

    //ICE CREAM
    private static  String URL;
    private static  String drop;
    private static String windowSize;
    private static  String host_to_server;
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
            System.out.println("Encryption Key Established: " + encryptionKey);

            // --- Send the URL (hard-coded) ---
            out.writeUTF(URL);
            out.flush();

            // Determine file name based on URL.
            String fileName = "drop_"+drop+"_"+sanitizeFileName(URL);
            System.out.println("Saving downloaded file as: " + fileName);

            // --- Measure Throughput ---
            long startTime = System.nanoTime();
            receiveFileWithSlidingWindow(in, out, fileName);
            long endTime = System.nanoTime();

            // Calculate file size and throughput in Mbps.
            File downloadedFile = new File("temp", fileName);
            long fileSizeBytes = downloadedFile.length();
            double elapsedSeconds = (endTime - startTime) / 1e9;
            // Convert file size to bits and then calculate Mbps.
            double throughputBps = (fileSizeBytes * 8) / elapsedSeconds;
            double throughputMbps = throughputBps / 1e6;

            System.out.println("File Size (bytes): " + fileSizeBytes);
            System.out.println("Elapsed Time (seconds): " + elapsedSeconds);
            System.out.println("Throughput (Mbps): " + throughputMbps);

            // Generate an HTML report with throughput information.
            generateReport(fileSizeBytes, elapsedSeconds, throughputMbps);

            // --- File Integrity Validation using cmp ---
            String downloadedFilePath = "temp" + File.separator + fileName;
            String referenceFilePath = "reference" + File.separator + fileName;
            validateFileIntegrity(downloadedFilePath, referenceFilePath);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void receiveFileWithSlidingWindow(DataInputStream in, DataOutputStream out, String fileName)
            throws IOException {
        // Create relative "temp" directory if it does not exist.
        File tempDir = new File("temp");
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
                if (seq == -1) {  // End-of-transmission indicator.
                    break;
                }
                int length = in.readInt();
                byte[] buffer = new byte[length];
                in.readFully(buffer);
                fileOut.write(buffer);
                fileOut.flush();
                // Send acknowledgment for the received packet.
                out.writeInt(seq);
                out.flush();
                System.out.println("Sent ACK for packet: " + seq);
            }
        }
    }

    private static String sanitizeFileName(String url) {
        // Remove the protocol part and replace non-alphanumeric characters with underscores.
        String sanitized = url.replaceAll("https?://", "");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
        return sanitized;
    }

    private static void validateFileIntegrity(String downloadedFilePath, String referenceFilePath)
            throws IOException, InterruptedException {
        // Build the cmp command.
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

    private static void generateReport(long fileSize, double elapsedSeconds, double throughputMbps)
            throws IOException {
        File resultDir = new File(host_to_server);
        if (!resultDir.exists()) {
            resultDir.mkdirs();
        }
        String htmlFileName = "drop_"+drop+"_"+windowSize+"_throughput.html";
        String htmlContent =
                "<h1>Throughput Report for simulation with " + drop+ " drop simulation and a " + windowSize + " window size.</h1>\n" +
                "<p>File Size: " + fileSize + " bytes</p>\n" +
                "<p>Elapsed Time: " + String.format("%.3f", elapsedSeconds) + " seconds</p>\n"+
                "<p>Throughput: " + throughputMbps + " Mbps</p>\n";

        File outputFile = new File(resultDir, htmlFileName);

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(htmlContent);
        }

        System.out.println("Throughput report generated: " + htmlFileName);
    }
}