package order.src.main.java.order;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderResponse {
    @JsonProperty("transaction_number")
    private Integer transaction_number;

    public OrderResponse() {}

    public OrderResponse(Integer transaction_number) {
        this.transaction_number = transaction_number;
    }

    public Integer gettransaction_number() {return transaction_number;}
}
