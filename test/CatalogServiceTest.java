package test;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;

public class CatalogServiceTest {
    @Test
    void testValidLookUpRequest() throws Exception {
        JsonNode response = CommonMethods.sendlookUpRequest(new URI("http://localhost:8081/catalog?stock=GameStart"));
        // stockName that we receive in response must be the stock name for which we requested lookUp request
        String actualStockName = response.get("name").toString();
        Assertions.assertEquals("\"GameStart\"", actualStockName);

        // price that we receive in response must be same as the price we set for the first time.
        double actualPrice = Double.parseDouble(response.get("price").toString());
        Assertions.assertEquals(15.47, actualPrice);

        /*
            Since the exact quantity is unknown at any given time, we cannot assert a specific number.
            However, we do know that for any valid lookup request, the quantity will always be greater than or equal to zero.
        */
        int actualQuantity = Integer.parseInt(response.get("quantity").toString());
        Assertions.assertTrue(actualQuantity >= 0); // returned quantity should not be a negative number
    }

    @Test
    void testInvalidLookUpRequest() throws Exception {
        JsonNode response = CommonMethods.sendlookUpRequest(new URI("http://localhost:8081/catalog?stock=Tesla")); // make a lookUp request of stock that is not available in our records;

        // stockName that we receive in response must be null as record does not exist in our system
        Assertions.assertTrue(response.get("name").isNull());
    }
}