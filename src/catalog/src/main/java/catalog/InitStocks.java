package catalog.src.main.java.catalog;
import java.io.*;

public class InitStocks {
    public static void initStocks(String csvFile) {
        String[] initialStocks = {
            "GameStart,15.47,100,100",
            "RottenFishCo,6.23,100,100",
            "Amazon,172.61,100,100",
            "Apple,197.23,100,100",
            "Pfizer,54.34,100,100",
            "Airbnb,45.78,100,100",
            "Alphabet,145.45,100,100",
            "Google,212.39,100,100",
            "Walmart,134.62,100,100",
            "Meta,164.20,100,100"
        };

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {
            for (String stock : initialStocks) {
                writer.write(stock);
                writer.newLine();
            }
            System.out.println("Stocks initialized successfully.");
        } catch (IOException e) {
            System.out.println("Error initializing stocks: " + e.getMessage());
        }
    }
}