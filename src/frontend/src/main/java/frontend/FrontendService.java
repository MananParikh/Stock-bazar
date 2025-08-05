package frontend.src.main.java.frontend;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Collectors;

public class FrontendService {

    private static String catalogService = System.getenv("catalogservice");

 
    private static Cache lruCache;
    private static Boolean isCachingEnabled = true; // by default true
    private static String orderService0 = System.getenv("orderservice0");
    private static String orderService1 = System.getenv("orderservice1");
    private static String orderService2 = System.getenv("orderservice2");
    private static String orderServiceLeader = null;

    public static void main(String[] args) throws IOException {
        int capacity = 5;

        if(args.length == 1) {
            isCachingEnabled = Boolean.parseBoolean(args[0]);
        } else if(args.length == 2) {
            isCachingEnabled = Boolean.parseBoolean(args[0]);
            try {
                capacity = Integer.parseInt(args[1]);
            } catch (Exception e) {
                capacity  = 5;
            }
        }
        lruCache = new Cache(capacity);

        // frontend server listens on port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // define paths
        server.createContext("/stocks/", new CatalogHandler()); // catalog service endpoint (called by client)
        server.createContext("/orders/", new OrderHandler()); // order service endpoint (called by client)
        server.createContext("/updateCache", new UpdateCacheHandler()); // cache invalidation endpoint (called by catalog)

        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // creates threads as needed, reuses when possible
        server.start();

        orderServiceLeader = ElectOrderServiceLeader(); // elect order service leader on server start
        if(orderServiceLeader == null) {
            System.out.println("No Order Service Leader found.");
        }
        
    }

    // Ping order services 0-2, elect leader with highest id and assign it to orderServiceLeader
    public static String ElectOrderServiceLeader() {
        for (int i = 2; i > -1; i--) {
            String orderServiceLeader = System.getenv("orderservice" + i);
            System.out.println("Pinging Order Service " + i + ": " + orderServiceLeader);
            int status = 0;
            // call Order Service, and determine if it is online
            try {
                URL url = new URI(orderServiceLeader + "/health").toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(30);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                status = connection.getResponseCode();
            } catch (Exception e) {
                System.out.println("Error pinging Order Service " + i);
                // if the order service is down, continue to next order service
                status = 500; // set status to 500 to indicate failure
            }
            if (status == 200) {
                // If the order service is online, assign it as leader
                System.out.println("Order Service " + i + " is available");
                return orderServiceLeader;
            } else {
                // else continue to next order service
                System.out.println("Order Service " + i + " is not available.");
            }
        }
        // if all order services are offline, return null
        System.out.println("All Order Services are offline.");
        return null;
        
    }

    static class CatalogHandler implements HttpHandler {
        
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String[] pathSegments = exchange.getRequestURI().getPath().split("/");
            if(pathSegments.length >= 3) {
                String stockName = pathSegments[2];
                HashMap<String, Object> response = new HashMap<>();

                System.out.println("\nReceived lookup request for " + stockName);
                if(!lruCache.get(stockName).isEmpty() && isCachingEnabled) {
                    System.out.println("Cache lookup for " + stockName + ": " + lruCache.get(stockName));
                    response = lruCache.get(stockName);
                } else {
                    try {
                        System.out.println("Cache lookup for " + stockName + ": not found.");
                        // call Catalog Service, and decide whether its success or failure
                        URL url = new URI(catalogService + "/catalog?stock=" + stockName).toURL();
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setConnectTimeout(30);
                        connection.setRequestMethod("GET");
                        connection.setRequestProperty("Accept", "application/json");
                        JsonNode jsonNode = JsonResponse.readResponse(connection);

                        if(jsonNode != null) {
                        /* if json response has name associated with it,
                         which means stock is found, then wrap it inside data and send it to client
                          */
                            if(!jsonNode.get("name").isNull()) {
                                response.put("data", jsonNode);
                            } else {
                                response.put("error", new ErrorResponse(stockName + " stock not found."));
                            }
                            if(isCachingEnabled) // if caching is disabled, it doesn't make any sense to add anything in cache
                                lruCache.put(stockName, response);
                        } else {
                            // dead code
                            exchange.sendResponseHeaders(404, 0);
                        }
                    } catch (Exception e) {
                        System.out.println("Error in CatalogHandler: " + e.getMessage());
                    }

                }
                JsonResponse.sendJsonResponse(exchange, response);
            }
        }
    }

    static class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            // Process trade requests
            if(exchange.getRequestMethod().equalsIgnoreCase("post")) {
                // Read JSON body from client request 
                String requestBody;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                    requestBody = reader.lines().collect(Collectors.joining("\n"));
                } catch (IOException e) {
                    System.out.println("Error reading client request: " + e.getMessage());
                    exchange.sendResponseHeaders(400, 0); // Bad Request
                    exchange.close();
                    return;
                }

                System.out.println("\nReceived trade request: " + requestBody);

                // Attempt to forward request to order service
                boolean leaderCrash = true; // set to true to allow entry to the loop
                while(leaderCrash) { // looping to resend request if leader if we get a new leader
                    try {
                        // call order-service
                        URL url = new URI(orderServiceLeader + "/trade").toURL();
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setConnectTimeout(30);
                        connection.setRequestMethod("POST");
                        connection.setDoOutput(true);

                        // Set content type header,
                        // input (Content-Type) is in JSON (application/json) format.
                        connection.setRequestProperty("Content-Type", "application/json");

                        // Attempt to send request body to order service, elect new leader if fail
                        // connection.getOutputStream() purpose is to obtain an output stream for sending data to the server.
                        leaderCrash = false; // assume no crash until proven otherwise
                        JsonNode jsonNode = null;
                        HashMap<String, Object> response = new HashMap<>();
                        try (DataOutputStream os = new DataOutputStream(connection.getOutputStream())) {
                            os.writeBytes(requestBody);
                            os.flush();

                            // Read response from order service
                            jsonNode = JsonResponse.readResponse(connection);

                        } catch (IOException e) { // connection failed, leader is down
                            System.out.println("Error sending request to order service.");
                            System.out.println("Leader is down, electing new leader...");
                            leaderCrash = true; // this leader is down, elect new and try again
                            orderServiceLeader = ElectOrderServiceLeader();
                            if(orderServiceLeader != null) {
                                System.out.println("New Order Service Leader: " + orderServiceLeader);
                                continue; // continue to top of loop to process request with new leader
                            } else {
                                leaderCrash = false; // all order services are down, there is nothing we can do
                                System.out.println("No Order Service Leader found.");
                            }
                        }

                        // Set up response to client
                        if(jsonNode != null && Integer.parseInt(jsonNode.get("transaction_number").toString()) != -1) {
                            // We got a transaction number
                            response.put("data", jsonNode);
                        } else if(leaderCrash) {
                            // All order services were down
                            response.put("error", new ErrorResponse("order services are offline at the moment."));
                            leaderCrash = false; // all order services are down, there is nothing we can do
                        } else {
                            // Order request was processed but failed
                            response.put("error", new ErrorResponse("order request could not be completed."));
                        }

                        // Send response back to client
                        JsonResponse.sendJsonResponse(exchange, response);
                        System.out.println("Trade request completed: " + requestBody);
                        
                    } catch (Exception e) {
                        System.out.println("Error in OrderHandler(POST): " + e.getMessage());
                    }
                }

            // Process order query requests
            } else if(exchange.getRequestMethod().equalsIgnoreCase("get")) {
                String[] pathSegments = exchange.getRequestURI().getPath().split("/");
                System.out.println("Order Query request received from client");
                if(pathSegments.length >= 3) {
                    int orderNumber = Integer.parseInt(pathSegments[2]);
                    
                    boolean leaderCrash = true; // set to true to allow entry to the loop
                    while(leaderCrash) { // looping to resend request if leader if we get a new leader
                        
                        try {
                            URL url = new URI(orderServiceLeader + "/trade?transactionNumber=" + orderNumber).toURL();
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setConnectTimeout(30);
                            connection.setRequestMethod("GET");
                            connection.setRequestProperty("Accept", "application/json");

                            // attempt to read response
                            leaderCrash = false; // assume no crash until proven otherwise
                            JsonNode jsonNode = null;
                            HashMap<String, Object> response = new HashMap<>();
                            jsonNode = JsonResponse.readResponse(connection);
                            if (jsonNode == null || jsonNode.isNull()) { // connection failed, leader is down
                                System.out.println("Error sending request to order service.");
                                System.out.println("Leader is down, electing new leader...");
                                leaderCrash = true; // this leader is down, elect new and try again
                                orderServiceLeader = ElectOrderServiceLeader();
                                if(orderServiceLeader != null) {
                                    System.out.println("New Order Service Leader: " + orderServiceLeader);
                                    continue; // continue to top of loop to process request with new leader
                                } else {
                                    leaderCrash = false; // all order services are down, there is nothing we can do
                                    System.out.println("No Order Service Leader found.");
                                }
                            }

                            // set up response to client
                            if(jsonNode != null && !jsonNode.get("name").isNull()) {
                                // We got a transaction number
                                response.put("data", jsonNode);
                            } else if(leaderCrash) {
                                // All order services were down
                                response.put("error", new ErrorResponse("order services are offline at the moment."));
                                leaderCrash = false; // all order services are down, there is nothing we can do
                            } else {
                                // Query request was processed but failed
                                response.put("error", new ErrorResponse("order lookup request could not be completed."));
                            }
    
                            // Send response back to client
                            JsonResponse.sendJsonResponse(exchange, response);

                        } catch (Exception e) {
                            System.out.println("Error in OrderHandler(GET): " + e.getMessage());
                        }
                    }

                }
            }
        }
    }

    /*
        This handler will handle the requests sent from catalog-service to update the cache.
     */
    static  class UpdateCacheHandler implements  HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if(exchange.getRequestMethod().equalsIgnoreCase("get")) {
                String query = exchange.getRequestURI().getQuery();
                String stockName = null;
                if (query != null) stockName = query.split("=")[1]; // assuming the query is "name=stockName"

                if(stockName != null) {
                    if(isCachingEnabled) {
                        System.out.println("\nReceived invalidation request from catalog-service and successfully removed the cache entry for stock: " + stockName);
                        lruCache.removeEntry(stockName);
                    }
                    /*
                        The catalog service is unaware of whether caching is enabled on the frontend.
                        Therefore, even if caching is disabled, we still respond with 200 OK to any cache invalidation request.
                    */
                    exchange.sendResponseHeaders(200, 0);
                } else {
                    exchange.sendResponseHeaders(404, 0);
                }
            }
            exchange.close();
        }
    }
}