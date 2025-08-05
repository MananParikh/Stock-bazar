package catalog.src.main.java.catalog;

public class Stock {
    private String stockName;
    private double stockPrice;
    private int stockVolume;
    private int stockQuantity;

    public Stock(String name, double price, int volume, int quantity) {
        this.stockName = name;
        this.stockPrice = price;
        this.stockVolume = volume;
        this.stockQuantity = quantity;
    }

    public String getName() {
        return stockName;
    }

    public double getPrice() {
        return stockPrice;
    }

    public int getVolume() {
        return stockVolume;
    }

    public int getQuantity() {
        return stockQuantity;
    }

    public void setPrice(double price) {
        this.stockPrice = price;
    }

    public void addVolume(int volume) {
        this.stockVolume += volume;
    }

    public void setQuantity(int quantity) {
        this.stockQuantity = quantity;
    }
}
