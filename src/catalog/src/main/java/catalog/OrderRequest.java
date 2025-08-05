package catalog.src.main.java.catalog;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderRequest {
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("quantity")
    private int quantity;

    @JsonProperty("type")
    private String type;

    public OrderRequest() {}

    public OrderRequest(String name, int quantity, String type) {
        this.name = name;
        this.quantity = quantity;
        this.type = type;
    }

    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public String getType() { return type; }
}
