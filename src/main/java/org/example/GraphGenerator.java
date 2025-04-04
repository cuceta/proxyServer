package org.example;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class GraphGenerator {
    // Global variable host_to_server; change as needed.
    public static String host_to_server = "local-local";

    public static void main(String[] args) {
        // Using preset x-axis labels.
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
            // Adjusted path to load JSON files.
            File file = new File("src" + File.separator + "main" + File.separator + "java"
                    + File.separator + host_to_server + File.separator + "jsonResults"
                    + File.separator + jsonFiles[i]);
            if (!file.exists()) {
                System.out.println("JSON file not found: " + file.getAbsolutePath());
                continue;
            }
            try (FileReader reader = new FileReader(file)) {
                JSONObject jsonObject = (JSONObject) parser.parse(reader);
                // Assumes the JSON has a key "throughputMbps".
                double throughput = Double.parseDouble(jsonObject.get("throughputMbps").toString());
                // Add the throughput value to the dataset with the corresponding x-axis label.
                dataset.addValue(throughput, "Throughput (Mbps)", xLabels[i]);
            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        }

        // Create the bar chart.
        JFreeChart barChart = ChartFactory.createBarChart(
                host_to_server + " Throughput Bar Chart",
                "Category",
                "Throughput (Mbps)",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false);

        // Set the bar color to green.
        CategoryPlot plot = barChart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        Color green = Color.decode("#a3b18a");
        renderer.setSeriesPaint(0, green);

        // Set the plot background to white.
        Color grey =Color.decode("#ede6dd");
        plot.setBackgroundPaint(grey);

        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setDefaultItemLabelsVisible(true);


        // Save the chart as an image in the host_to_server directory.
        try {
            File dir = new File("src" + File.separator + "main" + File.separator + "java"
                    + File.separator + host_to_server);
            if (!dir.exists()) {
                dir.mkdirs(); // Create the directory if it doesn't exist.
            }
            File outputFile = new File(dir, "throughput_bar_chart.png");
            ChartUtils.saveChartAsPNG(outputFile, barChart, 1000, 700);
            System.out.println("Graph saved as: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Display the chart in a frame.
        ChartFrame chartFrame = new ChartFrame("Throughput Bar Chart", barChart);
        chartFrame.pack();
        chartFrame.setVisible(true);
    }
}
