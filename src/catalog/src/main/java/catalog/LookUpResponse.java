package catalog.src.main.java.catalog;
import com.fasterxml.jackson.annotation.JsonProperty;

public class LookUpResponse {
    @JsonProperty("name")
    private String name;

    @JsonProperty("price")
    private double price;

    @JsonProperty("quantity")
    private int quantity;

    public LookUpResponse() {}

    public LookUpResponse(String name, double price, int quantity) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
}