import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;

public class GraphGenerator {

    private static final String host_to_server = "local-local";
//    private static final String host_to_server = "pi-rho";
//    private static final String host_to_server = "rho-moxie";


    public static void generateBarChart(String[] csvFilePaths, String[] xAxisLabels,
                                        String chartTitle, String xAxisLabel, String yAxisLabel,
                                        String outputPath) {
        if (csvFilePaths.length != xAxisLabels.length) {
            System.err.println("Error: The number of CSV files and x-axis labels must be equal.");
            return;
        }

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        // Read throughput values from each CSV file.
        for (int i = 0; i < csvFilePaths.length; i++) {
            double throughput = readThroughputFromCSV(csvFilePaths[i]);
            dataset.addValue(throughput, "Throughput", xAxisLabels[i]);
        }

        // Create the bar chart.
        JFreeChart barChart = ChartFactory.createBarChart(
                chartTitle,
                xAxisLabel,
                yAxisLabel,
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


        int width = 800;
        int height = 600;
        try {
            ChartUtils.saveChartAsPNG(new File(outputPath), barChart, width, height);
            System.out.println("Bar chart generated at: " + outputPath);
        } catch (IOException e) {
            System.err.println("Error saving chart image.");
            e.printStackTrace();
        }
    }

    private static double readThroughputFromCSV(String csvFilePath) {
        double throughput = 0.0;
        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            // Read header line.
            String header = br.readLine();
            // Read the data line.
            String dataLine = br.readLine();
            if (dataLine != null) {
                String[] tokens = dataLine.split(",");
                if (tokens.length >= 3) {
                    throughput = Double.parseDouble(tokens[2].trim());
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Error reading throughput from CSV file: " + csvFilePath);
            e.printStackTrace();
        }
        return throughput;
    }

    // Example main method for testing.
    public static void main(String[] args) {
        String baseDir = System.getProperty("user.dir") + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator + host_to_server + File.separator + "resultsCSV" + File.separator;
        String[] csvFiles = {
                baseDir + "drop_disable_1_throughput.csv",
                baseDir + "drop_enable_1_throughput.csv",
                baseDir + "drop_disable_8_throughput.csv",
                baseDir + "drop_enable_8_throughput.csv",
                baseDir + "drop_disable_64_throughput.csv",
                baseDir + "drop_enable_64_throughput.csv"
        };

        // Corresponding x-axis labels.
        String[] xAxisLabels = {"1NoDrop", "1Drop", "8NoDrop", "8Drop", "64NoDrop", "64Drop"};

        generateBarChart(csvFiles, xAxisLabels,
                "Throughput", "Test Cases", "Throughput (Mbps)",
                 baseDir+ "bar_chart.png");
    }
}
