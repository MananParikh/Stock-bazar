package test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;


public class FullApplicationTest {
    @Test
    void testValidLookUpRequest() throws Exception {
        JsonNode response = CommonMethods.sendlookUpRequest(new URI("http://localhost:8080/stocks/GameStart"));

        // data should not be null
        Assertions.assertFalse(response.get("data").isNull());

        // stockName that we receive in response must be the stock name for which we requested lookUp request
        String actualStockName = response.get("data").get("name").toString();
        Assertions.assertEquals("\"GameStart\"", actualStockName);

        // price that we receive in response must be same as the price we set for the first time.
        double actualPrice = Double.parseDouble(response.get("data").get("price").toString());
        Assertions.assertEquals(15.47, actualPrice);

        /*
            Since the exact quantity is unknown at any given time, we cannot assert a specific number.
            However, we do know that for any valid lookup request, the quantity will always be greater than or equal to zero.
        */
        int actualQuantity = Integer.parseInt(response.get("data").get("quantity").toString());
        Assertions.assertTrue(actualQuantity >= 0); // returned quantity should not be a negative number
    }

    @Test
    void testMultipleLookUpRequests() throws Exception {
        String[] stocks = {"GameStart", "Pfizer", "RottenFishCo", "GameStart", "Google", "Alphabet", "Walmart"};
        for(int i=0; i<7; i++) {
            JsonNode response = CommonMethods.sendlookUpRequest(new URI("http://localhost:8080/stocks/" + stocks[i]));

            // data should not be null
            Assertions.assertFalse(response.get("data").isNull());

            // stockName that we receive in response must be the stock name for which we requested lookUp request
            String actualStockName = response.get("data").get("name").toString();
            Assertions.assertEquals(stocks[i], actualStockName.replace("\"", ""));

            int actualQuantity = Integer.parseInt(response.get("data").get("quantity").toString());
            Assertions.assertTrue(actualQuantity >= 0); // returned quantity should not be a negative number
        }
    }

    @Test
    void testInvalidLookUpRequest() throws Exception{
        JsonNode response = CommonMethods.sendlookUpRequest(new URI("http://localhost:8080/stocks/Tesla")); // make a lookUp request of stock that is not available in our records;

        Assertions.assertFalse(response.get("error").isNull()); // returned json object must have error key in it

        String actualErrorMessage = response.get("error").get("message").toString();
        Assertions.assertNotNull(actualErrorMessage); // there should be a message, it can't be null or empty string

        int actualCode = Integer.parseInt(response.get("error").get("code").toString());
        Assertions.assertEquals(404, actualCode); // returned code should be 404
    }

    @Test
    void testValidOrderBuyRequest() throws Exception{
        /*
            First, send a lookup request to check the current quantity before buying one share of stock.
            After the order is completed, verify that: quantityBeforeBuy == quantityAfterBuy + 1.
        */
        JsonNode lookUpResponseBeforeBuy = CommonMethods.sendlookUpRequest(new URI("http://localhost:8080/stocks/GameStart"));
        int quantityBeforeBuy = Integer.parseInt(lookUpResponseBeforeBuy.get("data").get("quantity").toString());

        // send order request
        JsonNode response = CommonMethods.sendOrderRequest(new URI("http://localhost:8080/orders/"), "GameStart", 1, "buy");

        // The response should contain a "data" key, and it should not be null.
        Assertions.assertFalse(response.get("data").isNull());

        // The response should contain a "transaction_number" key under "data", and it should not be null.
        Assertions.assertFalse(response.get("data").get("transaction_number").isNull());

         /*
            Since the exact transaction_number is unknown at any given time, we cannot assert a specific number.
            However, we do know that for any valid order request, the transaction_number will always be greater than zero.
        */
        int actualTransactionNumber = Integer.parseInt(response.get("data").get("transaction_number").toString());
        Assertions.assertTrue(actualTransactionNumber > 0);

        // send a lookUp request after buy to verify quantity before and after
        JsonNode lookUpResponseAfterBuy = CommonMethods.sendlookUpRequest(new URI("http://localhost:8080/stocks/GameStart"));
        int quantityAfterBuy = Integer.parseInt(lookUpResponseAfterBuy.get("data").get("quantity").toString());

        Assertions.assertEquals(quantityBeforeBuy, quantityAfterBuy+1);
    }

    @Test
    void testValidOrderSellRequest() throws Exception{
        /*
            First, send a lookup request to check the current quantity before selling one share of stock.
            After the order is completed, verify that: quantityAfterSell == quantityBeforeSell + 1.
        */
        JsonNode lookUpResponseBeforeSell = CommonMethods.sendlookUpRequest(new URI("http://localhost:8080/stocks/GameStart"));
        int quantityBeforeSell = Integer.parseInt(lookUpResponseBeforeSell.get("data").get("quantity").toString());

        // send order request
        JsonNode response = CommonMethods.sendOrderRequest(new URI("http://localhost:8080/orders/"), "GameStart", 1, "sell");

        // The response should contain a "data" key, and it should not be null.
        Assertions.assertFalse(response.get("data").isNull());

        // The response should contain a "transaction_number" key under "data", and it should not be null.
        Assertions.assertFalse(response.get("data").get("transaction_number").isNull());

         /*
            Since the exact transaction_number is unknown at any given time, we cannot assert a specific number.
            However, we do know that for any valid order request, the transaction_number will always be greater than zero.
        */
        int actualTransactionNumber = Integer.parseInt(response.get("data").get("transaction_number").toString());
        Assertions.assertTrue(actualTransactionNumber > 0);

        // send a lookUp request after sell to verify quantity before and after
        JsonNode lookUpResponseAfterSell = CommonMethods.sendlookUpRequest(new URI("http://localhost:8080/stocks/GameStart"));
        int quantityAfterSell = Integer.parseInt(lookUpResponseAfterSell.get("data").get("quantity").toString());

        Assertions.assertEquals(quantityBeforeSell + 1, quantityAfterSell);
    }

    @Test
    void testValidTransactionLookUpRequest() throws Exception{
        /*
            First, send a lookup request to check the current quantity before selling one share of stock.
            After the order is completed, verify that: quantityAfterSell == quantityBeforeSell + 1.
        */
        JsonNode lookUpResponseBeforeSell = CommonMethods.sendlookUpRequest(new URI("http://localhost:8080/stocks/GameStart"));
        int quantityBeforeSell = Integer.parseInt(lookUpResponseBeforeSell.get("data").get("quantity").toString());

        // send order request
        JsonNode response = CommonMethods.sendOrderRequest(new URI("http://localhost:8080/orders/"), "GameStart", 1, "sell");

        // The response should contain a "data" key, and it should not be null.
        Assertions.assertFalse(response.get("data").isNull());

        // The response should contain a "transaction_number" key under "data", and it should not be null.
        Assertions.assertFalse(response.get("data").get("transaction_number").isNull());

         /*
            Since the exact transaction_number is unknown at any given time, we cannot assert a specific number.
            However, we do know that for any valid order request, the transaction_number will always be greater than zero.
        */
        int actualTransactionNumber = Integer.parseInt(response.get("data").get("transaction_number").toString());
        Assertions.assertTrue(actualTransactionNumber > 0);

        // send a order-lookup request after sell to verify locally stored trade request matches with disk file or not
        JsonNode orderLookUpRequest = CommonMethods.sendlookUpRequest(new URI("http://localhost:8080/orders/" + actualTransactionNumber));
        Assertions.assertEquals("GameStart", orderLookUpRequest.get("data").get("name").toString().replace("\"", ""));
        Assertions.assertEquals("1", orderLookUpRequest.get("data").get("quantity").toString().replace("\"", ""));
        Assertions.assertEquals("sell", orderLookUpRequest.get("data").get("type").toString().replace("\"", ""));
    }

    @Test
    void testInvalidOrderRequest() throws Exception {
        // send order request with quantity that is not available in our records.
        JsonNode response = CommonMethods.sendOrderRequest(new URI("http://localhost:8080/orders/"), "GameStart", 1000, "buy");

        // The response should contain a "error" key, and it should not be null.
        Assertions.assertFalse(response.get("error").isNull());
    }

    @Test
    void testInvalidationRequest() throws Exception {
        // send invalidation request for a stock that is in our db
        int responseCode = CommonMethods.sendGETRequest(new URI("http://localhost:8080/updateCache?name=GameStart"));

        // GameStart is a valid stock that is in our db, so it should return response code 200
        Assertions.assertEquals(200, responseCode);
    }

    @Test
    void testInvalidationRequestWithInsufficientParameters() throws Exception {
        // send invalidation request without a stock name
        int responseCode = CommonMethods.sendGETRequest(new URI("http://localhost:8080/updateCache"));

        // since we didn't pass any stock name, it should return response code 404
        Assertions.assertEquals(404, responseCode);
    }
}

/*
 javac -cp "lib/junit-platform-console-standalone-1.10.0.jar:lib/jackson-core-2.15.0.jar:lib/jackson-annotations-2.15.0.jar:lib/jackson-databind-2.15.0.jar" test/FullApplicationTest.java
 *
  java -jar lib/junit-platform-console-standalone-1.10.0.jar \
  -cp ".:lib/jackson-core-2.15.0.jar:lib/jackson-annotations-2.15.0.jar:lib/jackson-databind-2.15.0.jar" \
  --select-class test.FullApplicationTest
 *
 */