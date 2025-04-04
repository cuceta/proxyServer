import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class GraphGenerator {
    // Global variable host_to_server; change as needed.
    public static String host_to_server = "local-local";

    public static void main(String[] args) {
        // Expect six x-axis labels as command-line arguments.
//        if (args.length != 6) {
//            System.out.println("Please provide 6 x-axis labels.");
//            return;
//        }
        String[] xLabels = {"1NoDrop", "1Drop", "8NoDrop", "8Drop", "64NoDrop", "64Drop"};

        // Expected JSON file names.
        String[] jsonFiles = {
                "drop_disable_1_throughput.json",
                "drop_enable_1_throughput.json",
                "drop_disable_8_throughput.json",
                "drop_enable_8_throughput.json",
                "drop_disable_64_throughput.json",
                "drop_enable_64_throughput.json"
        };

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        JSONParser parser = new JSONParser();

        for (int i = 0; i < jsonFiles.length; i++) {
            File file = new File(host_to_server + File.separator + "jsonResults" + File.separator + jsonFiles[i]);
            if (!file.exists()) {
                System.out.println("JSON file not found: " + file.getAbsolutePath());
                continue;
            }
            try (FileReader reader = new FileReader(file)) {
                JSONObject jsonObject = (JSONObject) parser.parse(reader);
                // Assumes the JSON has a key "throughputMbps".
                double throughput = Double.parseDouble(jsonObject.get("throughputMbps").toString());
                // Add the throughput value to the dataset with the given x-axis label.
                dataset.addValue(throughput, "Throughput (Mbps)", xLabels[i]);
            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        }

        // Create the bar chart.
        JFreeChart barChart = ChartFactory.createBarChart(
                "Throughput Bar Chart",
                "Category",
                "Throughput (Mbps)",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false);

        // Display the chart in a frame.
        ChartFrame chartFrame = new ChartFrame("Throughput Bar Chart", barChart);
        chartFrame.pack();
        chartFrame.setVisible(true);
    }
}
