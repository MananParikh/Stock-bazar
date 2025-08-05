package test;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;

public class OrderServiceTest {
    @Test
    void testValidOrderBuyRequest() throws Exception{
        // send order request
        JsonNode response = CommonMethods.sendOrderRequest(new URI("http://localhost:8082/trade"), "GameStart", 1, "buy");

        // The response should contain a "transaction_number", and it should not be null.
        Assertions.assertFalse(response.get("transaction_number").isNull());

         /*
            Since the exact transaction_number is unknown at any given time, we cannot assert a specific number.
            However, we do know that for any valid order request, the transaction_number will always be greater than zero.
        */
        int actualTransactionNumber = Integer.parseInt(response.get("transaction_number").toString());
        Assertions.assertTrue(actualTransactionNumber > 0);
    }

    @Test
    void testValidOrderSellRequest() throws Exception{
        // send order request
        JsonNode response = CommonMethods.sendOrderRequest(new URI("http://localhost:8082/trade"), "GameStart", 1, "sell");

        // The response should contain a "transaction_number" key , and it should not be null.
        Assertions.assertFalse(response.get("transaction_number").isNull());

         /*
            Since the exact transaction_number is unknown at any given time, we cannot assert a specific number.
            However, we do know that for any valid order request, the transaction_number will always be greater than zero.
        */
        int actualTransactionNumber = Integer.parseInt(response.get("transaction_number").toString());
        Assertions.assertTrue(actualTransactionNumber > 0);
    }

    @Test
    void testInvalidOrderRequest() throws Exception {
        // send order request with quantity that is not available in our records.
        JsonNode response = CommonMethods.sendOrderRequest(new URI("http://localhost:8082/trade"), "GameStart", 1000, "buy");

        // The response should contain a "transaction_number" with value -1.
        Assertions.assertEquals(-1, Integer.parseInt(response.get("transaction_number").toString()));
    }

    @Test
    void testTransactionLookUpRequest() throws Exception {
        // send order request
        JsonNode response = CommonMethods.sendOrderRequest(new URI("http://localhost:8082/trade"), "GameStart", 1, "sell");

        // The response should contain a "transaction_number" key , and it should not be null.
        Assertions.assertFalse(response.get("transaction_number").isNull());

         /*
            Since the exact transaction_number is unknown at any given time, we cannot assert a specific number.
            However, we do know that for any valid order request, the transaction_number will always be greater than zero.
        */
        int actualTransactionNumber = Integer.parseInt(response.get("transaction_number").toString());
        Assertions.assertTrue(actualTransactionNumber > 0);

        response = CommonMethods.sendlookUpRequest(new URI("http://localhost:8082/trade?transactionNumber=" + actualTransactionNumber));
        String expectedResponse = "{\"name\":\"GameStart\",\"quantity\":1,\"type\":\"sell\"}";

        Assertions.assertEquals(expectedResponse, response.toString());
    }

    @Test
    void testHealthofAllReplicas() throws Exception {
        final int EXPECTED_RESCODE = 200;

        // send health request to leader (order service 2)
        int resCode = CommonMethods.sendGETRequest(new URI("http://localhost:8084/health"));
        Assertions.assertEquals(EXPECTED_RESCODE, resCode);

        // send health request to follower (order service 0)
        resCode = CommonMethods.sendGETRequest(new URI("http://localhost:8082/health"));
        Assertions.assertEquals(EXPECTED_RESCODE, resCode);

        // send health request to follower (order service 1)
        resCode = CommonMethods.sendGETRequest(new URI("http://localhost:8083/health"));
        Assertions.assertEquals(EXPECTED_RESCODE, resCode);
    }

    @Test
    void testUpdateInReplicas() throws Exception {
        // send order request to LEADER - order service 2
        JsonNode response = CommonMethods.sendOrderRequest(new URI("http://localhost:8084/trade"), "GameStart", 1, "sell");

        // The response should contain a "transaction_number" key , and it should not be null.
        Assertions.assertFalse(response.get("transaction_number").isNull());

        String reqBody = "{\"name\":\"GameStart\",\"quantity\":1,\"type\":\"sell\"}";
        final int EXPECTED_RESCODE = 200;

        // send order update to follower => order-service 0
        int resCode = CommonMethods.sendPOSTRequest(new URI("http://localhost:8082/update"), reqBody);
        Assertions.assertEquals(EXPECTED_RESCODE, resCode);

        // send order update to follower => order-service 1
        resCode = CommonMethods.sendPOSTRequest(new URI("http://localhost:8083/update"), reqBody);
        Assertions.assertEquals(EXPECTED_RESCODE, resCode);
    }

    @Test
    void testInformReplicasWithOrderUpdate() throws Exception{
        // send order request to frontend
        JsonNode response = CommonMethods.sendOrderRequest(new URI("http://localhost:8080/orders/"), "GameStart", 1, "sell");

        // The response should contain a "data" key, and it should not be null.
        Assertions.assertFalse(response.get("data").isNull());

        // The response should contain a "transaction_number" key under "data", and it should not be null.
        Assertions.assertFalse(response.get("data").get("transaction_number").isNull());

        int actualTransactionNumber = Integer.parseInt(response.get("data").get("transaction_number").toString());

        // send health request to leader (order service 2)
        int resCode = CommonMethods.sendGETRequest(new URI("http://localhost:8084/inform?transactionNumber=" + actualTransactionNumber));
        Assertions.assertEquals(204, resCode); // 204, no update

        // send health request to follower (order service 0)
        resCode = CommonMethods.sendGETRequest(new URI("http://localhost:8082/inform?transactionNumber=" + actualTransactionNumber));
        Assertions.assertEquals(203, resCode);

        // send health request to follower (order service 1)
        resCode = CommonMethods.sendGETRequest(new URI("http://localhost:8083/inform?transactionNumber=" + actualTransactionNumber));
        Assertions.assertEquals(203, resCode);
    }
}