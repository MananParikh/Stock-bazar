package client.src.main.java.client;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Client {

    private static String frontEndService = System.getenv("frontendservice");
    private static String BASE_URL = frontEndService;
    private static final String lookUpEndPoint = "/stocks/";
    private static final String tradeEndPoint = "/orders/";

    public static void main(String[] args) {
        try {

            double probability = 0.8; // default probability
            int total_requests = 8; // default total number of requests
            boolean shouldPrintLatency = false; // Flag to control whether latency output is printed, default false
            if(args.length >= 1) {
                /*
                    1st argument indicates probability
                    2nd argument indicates the number of requests
                    3rd argument indicates flag to print statement neccessary for evaluation
                    4th argument indicates IP address of frontendservice
                 */
                try {
                    probability = Double.parseDouble(args[0]); // if probability is passed as an argument to test performance.
                    if(args.length == 2)
                        total_requests = Integer.parseInt(args[1]); // if total number of requests is passed as an argument to test performance.
                    else if(args.length == 3) {
                        total_requests = Integer.parseInt(args[1]);
                        shouldPrintLatency = Boolean.parseBoolean(args[2]);
                    } else if(args.length == 4) {
                        total_requests = Integer.parseInt(args[1]);
                        shouldPrintLatency = Boolean.parseBoolean(args[2]);
                        frontEndService = args[3];
                        BASE_URL = "http://" + frontEndService + ":8080";
                    }
                } catch(Exception e) {
                    // in case of any exception, set them back to default
                    probability = 0.8;
                    total_requests = 8;
                }
            }

            int counter = 0; // flag to count the number of requests sent

            // These ArrayLists store the latency of each request, which will be used to calculate the average latency later.
            ArrayList<Long> lookUpLatencies = new ArrayList<>();
            ArrayList<Long> tradeLatencies = new ArrayList<>();

            System.out.println("Started execution");

            /*
                successfulTradeRequests is used to locally store all the successful trade requests.
                In this hashmap, key is the transaction number, value is array of stock_name, quantity, and operation_type
             */
            HashMap<Integer,ArrayList<String> > successfulTradeRequests = new HashMap<>();

            while(counter < total_requests) {
                String stockName = getRandomStockName();

                long startTime = System.currentTimeMillis(); // start timing for lookup request
                int qty = sendlookUpRequest(stockName);
                long endTime = System.currentTimeMillis(); // end timing for lookup request
                
                if(shouldPrintLatency) {
                    System.out.println("Duration to complete lookup request (in miliseconds): " + (endTime - startTime));
                    lookUpLatencies.add((endTime - startTime));
                }
                
                counter++;

                if(counter < total_requests && qty >= 1) {
                    if(Math.random() <= probability) {
                        startTime = System.currentTimeMillis(); // start timing for trade request

                        int randomQuantity = getRandomQuantity();
                        String randomOperation = getRandomOperation();

                        int transactionNumber = sendTradeRequest(stockName, randomQuantity, randomOperation);

                        endTime = System.currentTimeMillis(); // end timing for trade request
                        
                        if(shouldPrintLatency) {
                            System.out.println("Duration to complete order request (in miliseconds): " + (endTime - startTime));
                            tradeLatencies.add((endTime - startTime));
                        }
                        counter++;

                        if(transactionNumber != -1) {
                            // for each successful trade request, map the transaction_number with the details of order-request, so that it can be used later on to compare with the server response of GET /orders/<order_number>
                            successfulTradeRequests.put(transactionNumber, new ArrayList<>(Arrays.asList(stockName, String.valueOf(randomQuantity), randomOperation)));
                        }
                    }
                }
            }
            /*
                Iterate through each successful trade request, and send a GET /orders/<order_number>
             */
            for (Map.Entry<Integer, ArrayList<String>> entry : successfulTradeRequests.entrySet()) {
                Integer key = entry.getKey();
                ArrayList<String> locallyStoredTransactionDetails = entry.getValue();

                // sendOrderLookUpRequest will return an array of size 3, they will be in the order of stock_name, quantity, type
                ArrayList<String> serverResponse = sendOrderLookUpRequest(key);

                if(locallyStoredTransactionDetails.size() == serverResponse.size()) {
                    boolean areEqual = true;

                    // Compare each element - (name, quantity, type) in both lists
                    for (int i = 0; i < locallyStoredTransactionDetails.size(); i++) {
                        if (!locallyStoredTransactionDetails.get(i).equals(serverResponse.get(i))) {
                            System.out.println("For Transaction number: " + key + ", below are the details.");

                            System.out.println("locally stored stock name: " + locallyStoredTransactionDetails.get(0) + "\t server responded with stock name: " + serverResponse.get(0));
                            System.out.println("locally stored stock quantity: " + locallyStoredTransactionDetails.get(1) + "\t server responded with stock quantity: " + serverResponse.get(1));
                            System.out.println("locally stored stock type: " + locallyStoredTransactionDetails.get(2) + "\t server responded with stock type: " + serverResponse.get(2));

                            areEqual = false;
                            break;
                        }
                    }

                    if(areEqual) {
                        System.out.println("Server response matches with the locally stored information for the transaction number " + key);
                    }
                } else {
                    System.out.println("Server response does not match with the locally stored information for the transaction number " + key);
                    System.out.println("locally stored stock name: " + locallyStoredTransactionDetails.get(0));
                    System.out.println("locally stored stock quantity: " + locallyStoredTransactionDetails.get(1));
                    System.out.println("locally stored stock type: " + locallyStoredTransactionDetails.get(2));
                    if(serverResponse.isEmpty()) {
                        System.out.println("Server could not find anything with the transaction number: " + key);
                    } else if(serverResponse.size() == 1) {
                        // dead code, it won't be the case where server will ever reply with 2 elements in response, either no elements or three elements (stock name, quantity, and type)
                        System.out.println("Server responded with stock name: " + serverResponse.get(0));
                    } else if(serverResponse.size() == 2) {
                        // dead code, it won't be the case where server will ever reply with 2 elements in response, either no elements or three elements (stock name, quantity, and type)
                        System.out.println("Server responded with stock name: " + serverResponse.get(0));
                        System.out.println("Server responded with stock quantity: " + serverResponse.get(1));
                    }
                }
            }

            // If latency printing is enabled, calculate average latencies for lookup and trade requests
            if(shouldPrintLatency) {
                // Calculate average lookup latency, excluding the first request to avoid handshake/setup overhead
                if(! lookUpLatencies.isEmpty() && lookUpLatencies.size() >= 2) {
                    double lookUpLatencyAverage = 0;
                    for (int i=1; i<lookUpLatencies.size(); i++) {
                        lookUpLatencyAverage += lookUpLatencies.get(i);
                    }
                    // Exclude the first latency from the average calculation
                    lookUpLatencyAverage =  lookUpLatencyAverage / (lookUpLatencies.size()-1);

                    System.out.println("\nAverage Lookup Latency: " + String.format("%.2f", lookUpLatencyAverage) + " ms (based on " + (lookUpLatencies.size() - 1) + " lookup requests)");
                }

                if(! tradeLatencies.isEmpty()) {
                    if(tradeLatencies.size() >= 2) {
                        double tradeLatencyAverage = 0;

                        for (int i=1; i<tradeLatencies.size(); i++) {
                            tradeLatencyAverage += tradeLatencies.get(i);
                        }
                        tradeLatencyAverage = tradeLatencyAverage / (tradeLatencies.size()-1);

                        System.out.println("Average Trade Latency: " + String.format("%.2f", tradeLatencyAverage) + " ms (based on " + (tradeLatencies.size() - 1) + " trade requests)");

                    } else { // if tradeLatencies.size() == 1
                        System.out.println("Average Trade Latency: " + tradeLatencies.get(0) + " ms (only one trade request)");
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static int sendlookUpRequest(String stockName) throws Exception {
        URL url = new URI(BASE_URL + lookUpEndPoint + stockName).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        System.out.println("\nLookUp:");
        System.out.println(lookUpEndPoint + stockName);

        JsonNode jsonNode = readResponse(connection);

        if(jsonNode != null && jsonNode.get("data") != null) {
            // jsonNode.get() will neither be Integer nor String, so that's why first convert to string, and then parseInt
            return Integer.parseInt(jsonNode.get("data").get("quantity").toString());
        }
        /*
            If jsonNode is null, OR
            jsonNode does not have data key, then it means it has error,
            so in either cases, stock is not found, and -1 should be returned to indicate failure.
         */
        return -1;

    }

    public static ArrayList<String> sendOrderLookUpRequest(int transactionNumber) throws Exception {
        URL url = new URI(BASE_URL + tradeEndPoint + transactionNumber).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        System.out.println("\nOrder Lookup:");
        System.out.println(tradeEndPoint + transactionNumber);

        JsonNode jsonNode = readResponse(connection);

        ArrayList<String> serverResponse = new ArrayList<>();

        if(jsonNode != null && jsonNode.get("data") != null) {
            // jsonNode.get() will neither be Integer nor String, so that's why first convert to string, and then parseInt
            serverResponse.add(jsonNode.get("data").get("name").toString().replace("\"", "").trim());
            serverResponse.add(jsonNode.get("data").get("quantity").toString());
            serverResponse.add(jsonNode.get("data").get("type").toString().replace("\"", "").trim());
        }
        /*
            If jsonNode is null, OR
            jsonNode does not have data key, then it means it has error,
            so in either cases, stock is not found, and -1 should be returned to indicate failure.
         */
        return serverResponse;

    }



    public static int sendTradeRequest(String stockName, int quantity, String type) throws Exception {
        URL url = new URI(BASE_URL + tradeEndPoint).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        // Set content type header,
        // input (Content-Type) is in JSON (application/json) format.
        connection.setRequestProperty("Content-Type", "application/json");

        // Calling the API and send request data
        // connection.getOutputStream() purpose is to obtain an output stream for sending data to the server.

        String data_to_send = "{\"name\": \"" + stockName + "\", \"quantity\": " + quantity + ", \"type\" : \"" + type + "\"}";
        System.out.println("\nOrder:");
        System.out.println(data_to_send);

        try (DataOutputStream os = new DataOutputStream(connection.getOutputStream())) {
            os.writeBytes(data_to_send);
            os.flush();
        }

        JsonNode jsonNode = readResponse(connection);

        if(jsonNode != null && jsonNode.get("data") != null) {
            // jsonNode.get() will neither be Integer nor String, so that's why first convert to string, and then parseInt
            return Integer.parseInt(jsonNode.get("data").get("transaction_number").toString());
        }

        return -1;
    }

    private static JsonNode readResponse(HttpURLConnection connection) throws Exception {
        StringBuilder response = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            System.out.println("Server's Response: " + response);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(response != null) {
            JsonNode jsonNode = new ObjectMapper().readTree(response.toString());
            return jsonNode;
        }

        return null;
    }

    private static String getRandomStockName() {
        String[] stocks = {"GameStart", "RottenFishCo", "Tesla", "Amazon", "Apple", "Pfizer", "Airbnb", "Alphabet", "Google", "Walmart", "Meta"};
        return stocks[getRandomIndex(0,10)];
    }

    private static int getRandomQuantity() {
        int[] quantities = {1, 2, 4, 20, 25, 30, 120, 230, 250};
        return quantities[getRandomIndex(0, 8)];
    }

    private static String getRandomOperation() {
        String[] operations = {"buy", "sell"};
        return operations[getRandomIndex(0, 1)];
    }

    private static int getRandomIndex(int min, int max) {
        //  randomNum = min + (int)(Math.random() * ((max – min) + 1));
        return min + (int)(Math.random() * ((max - min) + 1));
    }
}
