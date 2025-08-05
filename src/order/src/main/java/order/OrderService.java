package order.src.main.java.order;
import java.io.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class OrderService {

    private static String catalogService = System.getenv("catalogservice");
    private static String orderservice0 = System.getenv("orderservice0");
    private static String orderservice1 = System.getenv("orderservice1");
    private static String orderservice2 = System.getenv("orderservice2");
    
    protected static Map<Integer, OrderRequest> orderLogMap = new ConcurrentHashMap<>(); // will allow multiple threads to access different parts of the map at once
    private String csvFile = "/app/data/orders.csv"; // path to the csv file
    private static Boolean isLeader = false; // leader assignment object
    public static int myId = -1; // id of the current order service

    public OrderService() {
        loadOrders();
        scheduleCsvSave();
    }

    // loads the order log map from CSV
    public synchronized void loadOrders() {
        // check if csv exists, if not create it
        if (new File(csvFile).exists() == false) {
            try {
                new File(csvFile).createNewFile();
            } catch (IOException e) {
                System.out.println("Error creating orders file: " + e.getMessage());
            }
            return;
        }
        // if csv does exist, load orderlog from it
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                int transactionNumber = Integer.parseInt(parts[0]);
                String name = parts[1];
                int quantity = Integer.parseInt(parts[2]);
                String type = parts[3];
                OrderRequest order = new OrderRequest(name, quantity, type);
                orderLogMap.put(transactionNumber, order);
            }
        } catch (IOException e) {
            System.out.println("Error loading orders: " + e.getMessage());
        }
    }

    // schedule a task to save order log to CSV every 60 seconds
    public void scheduleCsvSave() {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this::storeOrders, 31, 30, TimeUnit.SECONDS);
    }

    // saves the order log map to CSV
    public synchronized void storeOrders() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvFile))) {
            for (Integer transactionNumber : orderLogMap.keySet()) {
                OrderRequest order = orderLogMap.get(transactionNumber);
                bw.write(transactionNumber + "," + order.getName() + "," + order.getQuantity() + "," + order.getType());
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error storing orders: " + e.getMessage());
        }
    }

    // updates the order log map with order/transaction number, returns transaction number
    public static synchronized Integer updateOrderLog(OrderRequest order) {
        Integer transactionNumber = generateTransactionNumber();
        orderLogMap.put(transactionNumber, order);
        return transactionNumber;
    }

    // generate an incremental transaction number
    private static synchronized Integer generateTransactionNumber() {
        // get the last key in the map and add 1 to it
        return orderLogMap.size(); // the last key in the map should be the size of the map
    }

    // Send order updates to the follower replicas (called by leader)
    public static void UpdateFollowers(String requestBody) {
        int status = 0; // response code

        // iterate over the follower ids, get address, send update
        int[] replicaIds = {0, 1, 2}; // all replica ids
        for (int i : replicaIds) {
            if (i == myId) continue; // skip self
            String followerAddress = System.getenv("orderservice" + i);
            try {
                URL url = new URI(followerAddress + "/update").toURL(); // follower's update handler
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(20);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.getOutputStream().write(requestBody.getBytes());
                status = conn.getResponseCode();
                if (status == 200) {
                    System.out.println("UpdateFollowers: Update sent to follower " + i + " successfully.");
                } else {
                    System.out.println("UpdateFollowers: Failed to send update to follower " + i + ": " + status);
                }
            } catch (Exception e) {
                System.out.println("UpdateFollowers: Error sending update to follower " + i);
            }
        }
    }

    // determine id of self (enviornment variable will be "self")
    public static int findSelfId() {
        if (orderservice0.equals("self")) {
            System.out.println("\nfindSelfId: 0");
            return 0;
        } else if (orderservice1.equals("self")) {
            System.out.println("\nfindSelfId: 1");
            return 1;
        } else if (orderservice2.equals("self")) {
            System.out.println("\nfindSelfId: 2");
            return 2;
        } else {
            System.out.println("\nError: unable to determine self id.");
            System.out.println("orderservice0: " + orderservice0);
            System.out.println("orderservice1: " + orderservice1);
            System.out.println("orderservice2: " + orderservice2);
            return -1; // this should not happen
        }    
    }

    // recover orders from leader replica after follower crash (called by follower)
    public static void recoverOrders() {
        // get last transaction number from order log
        int lastTransactionNumber = orderLogMap.size() - 1;
        System.out.println("\nRecoverOrders: Last transaction number: " + lastTransactionNumber);

        // iterate over other replica ids, get address, ask for updates
        int[] replicaIds = {2, 1, 0}; // all replica ids
        for (int i : replicaIds) {
            if (i == myId) continue; // skip self
            String replicaAddress = System.getenv("orderservice" + i);
        
            try {
                // send last transaction number to replica's inform handler to ask for updates
                System.out.println("RecoverOrders: asking for updated orders from replica " + i);
                URL url = new URI(replicaAddress + "/inform?transactionNumber=" + lastTransactionNumber).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(40);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                
                // read replica response code (200 means there were updates)
                OrderRequest MissingOrder;
                ObjectMapper objectMapper = new ObjectMapper();
                int responseCode = conn.getResponseCode();
                if (responseCode == 203 || responseCode == 204) { // replica was not leader or there were no updates
                    System.out.println("RecoverOrders: No new orders to recover from replica " + i);
                    continue; // no new orders to recover
                } else if (responseCode != 200) { // error, 404
                    System.out.println("RecoverOrders: Error recovering orders from replica " + i + ": status = " + responseCode);
                    continue;
                }
                // read leader's response body
                System.out.println("RecoverOrders: New orders found from replica " + i);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String responseBody = reader.lines().collect(Collectors.joining("\n"));
                    
                    // create a missingOrders list of orderRequest objects from responseBody
                    java.util.List<OrderRequest> missingOrders = objectMapper.readValue(responseBody, objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, OrderRequest.class));
                    // iterate over list for orders, write to order log
                    for (OrderRequest missingOrder : missingOrders) {
                        int tn = updateOrderLog(missingOrder); // write to log
                        System.out.println("RecoverOrders: Order with name " + missingOrder.getName() + " and transaction number " + tn + " added to order log.");
                    }

                    break; // if orders were recovered, stop asking replicas for orders
                    
                } catch (Exception e) {
                    System.out.println("RecoverOrders: Error recovering orders from leader: " + e.getMessage());
                }
            } catch (Exception e) {
                System.out.println("RecoverOrders: Error reading response from replica: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws IOException {
        
        // order server listens on container port 8082
        HttpServer server = HttpServer.create(new InetSocketAddress(8082), 0);

        server.createContext("/trade", new OrderHandler()); // order endpoint (called by frontend)
        server.createContext("/health", new HealthHandler()); // health check endpoint (called by frontend)
        server.createContext("/inform", new InformHandler()); // request order updates endpoint (called by follower)
        server.createContext("/update", new UpdateHandler()); // provide order updates endpoint (called by leader)

        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // creates threads as needed, reuses when possible
        server.start();

        myId = findSelfId(); // finds the id of this container
        recoverOrders(); // recover orders from leader
    }

    // Listener for receiving trade and order lookup requests from front end
    static class OrderHandler implements HttpHandler {
        OrderService orderService = new OrderService(); // load orders and schedule save
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            // the frontend is using this replica as leader
            if (!isLeader) {
                System.out.println("I am now the leader");
            }
            isLeader = true;
            
            // forward order request to catalog service to confirm or deny
            // log order and respond with transaction number if confirmed
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                
                // forward the request to the CatalogService
                try {
                    String requestBody;
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                        requestBody = reader.lines().collect(Collectors.joining("\n"));
                    }
                    System.out.println("\nReceived trade request: " + requestBody);
                    ObjectMapper objectMapper = new ObjectMapper();
                    OrderRequest order = objectMapper.readValue(requestBody, OrderRequest.class);

                    URL url = new URI(catalogService + "/catalog").toURL(); // CatalogService URL
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(30);
                    conn.setRequestMethod("POST"); // tell catalogService that this is an order request
                    conn.setDoOutput(true);
                    conn.getOutputStream().write(requestBody.getBytes());

                    // read the response from CatalogService
                    int responseCode = conn.getResponseCode();

                    Integer transactionNumber; // "Integer" allows us to pass transactionNumber as an object (required by sendJsonResponse)

                    if (responseCode == 200) {
                        // if order successful, update the order log, generate transaction #, update follower replicas
                        transactionNumber = updateOrderLog(order);
                        UpdateFollowers(requestBody); // send order to followers
                    } else {
                        transactionNumber = -1; // transaction failed
                    }    
                    // send the response back to the front end
                    JsonResponse.sendJsonResponse(exchange, new OrderResponse(transactionNumber));
                    
                } catch (Exception e) {
                    exchange.sendResponseHeaders(500, 0);
                }
                exchange.close();

            // order lookup request: find transaction number in order log, reply with order details
            } else if("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                // read request: (Parse query to get transaction number)
                String query = exchange.getRequestURI().getQuery();
                int transactionNumber = -1;
                if (query != null)
                    transactionNumber = Integer.parseInt(query.split("=")[1]); // assuming the query is "transactionNumber=number"

                System.out.println("\nReceived order lookup request: " + transactionNumber);
                // look up order
                OrderRequest order = OrderService.orderLogMap.get(transactionNumber);
                if (order == null) {
                    JsonResponse.sendJsonResponse(exchange, new OrderRequest());
                } else {
                    JsonResponse.sendJsonResponse(exchange, order);
                }

                // send response

                exchange.close();
            }
        }
    }

    // Health check listener to report status to frontend
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "Online";
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            exchange.close();
        }
    }

    // Handler for the /inform endpoint. 
    // Used to inform a leader replica that a follower has recovered 
    // and needs to be provided with missed orders.
    static class InformHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            
            // a follower has recovered and must be provided with missed orders
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {

                // read request: (Parse query to get transaction number)
                String query = exchange.getRequestURI().getQuery();
                int transactionNumber = Integer.parseInt(query.split("=")[1]); // assuming the query is "transactionNumber={transactionNumber}"
                String response;
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                // check if transaction number maps to an order
                OrderRequest order = orderLogMap.get(transactionNumber);
                try {
                    // If this replica is a follower, reply null
                    if (!isLeader) {
                        response = "null";
                        exchange.sendResponseHeaders(203, response.length()); // 203 Non-Authoritative Information

                    } else if (transactionNumber >= (orderLogMap.size()-1)) { // we have no new orders
                        System.out.println("InformHandler: No new updates since TN#: " + transactionNumber);
                        response = "null"; // no new orders to send
                        exchange.sendResponseHeaders(204, response.length()); // 204 No Content

                    } else if (order != null) { // we have new order(s) to send, get all subsequent orders
                        System.out.println("InformHandler: Order found for transaction number " + transactionNumber);
                        java.util.List<OrderRequest> MissingOrderLog = new java.util.ArrayList<>(); // create a new list to store the missing orders
                        for (int i = (transactionNumber + 1); i < orderLogMap.size(); i++) {
                            MissingOrderLog.add(orderLogMap.get(i)); // get all orders from the transaction number to the end of the log
                        }
                        // send the missing orders to the follower
                        ObjectMapper objectMapper = new ObjectMapper();
                        response = objectMapper.writeValueAsString(MissingOrderLog); // convert the map to JSON
                        exchange.sendResponseHeaders(200, response.length());

                    } else if (transactionNumber == -1) { // follower has no orders, send all orders
                        System.out.println("InformHandler: Follower has no orders, sending all orders");
                        java.util.List<OrderRequest> MissingOrderLog = new java.util.ArrayList<>(); // create a new list to store the missing orders
                        // get all orders in our log
                        for (int i = (transactionNumber + 1); i < orderLogMap.size(); i++) {
                            MissingOrderLog.add(orderLogMap.get(i)); 
                        }
                        // send the missing orders to the follower
                        ObjectMapper objectMapper = new ObjectMapper();
                        response = objectMapper.writeValueAsString(MissingOrderLog); // convert the map to JSON
                        exchange.sendResponseHeaders(200, response.length());

                    } else {
                        System.out.println("InformHandler: Order not found for transaction number " + transactionNumber);
                        response = "null"; // error
                        exchange.sendResponseHeaders(404, response.length()); // 404 Not Found
                    }
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes(StandardCharsets.UTF_8)); // write the response body
                    os.close();
                } catch (Exception e) {
                    System.out.println("Error sending missing orders to follower: " + e.getMessage());
                    exchange.sendResponseHeaders(500, 0);
                }
            }
            exchange.close();

        }
    }

    // Listener to receive order updates from the leader
    static class UpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {

                // a leader replica is using this replica as a follower
                if (isLeader) {
                    System.out.println("I am no longer leader");
                }
                isLeader = false;

                // read leader's update
                String requestBody;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                    requestBody = reader.lines().collect(Collectors.joining("\n"));
                
                    // read order from requestBody
                    ObjectMapper objectMapper = new ObjectMapper();
                    OrderRequest order = objectMapper.readValue(requestBody, OrderRequest.class);
                    
                    // write order to orderlog
                    Integer transactionNumber = updateOrderLog(order); 
                    System.out.println("Update recieved: " + requestBody);

                    exchange.sendResponseHeaders(200, 0);
                } catch (Exception e) {
                    System.out.println("Error in reading update from leader: " + e.getMessage());
                    exchange.sendResponseHeaders(500, 0);
                }
            }
            exchange.close();
        }
    }

}
