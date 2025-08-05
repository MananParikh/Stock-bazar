package catalog.src.main.java.catalog;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

class CatalogService {
    private Map<String, Stock> stockMap = new ConcurrentHashMap<>(); // will allow multiple threads to access different parts of the map at once
    private Map<String, ReentrantLock> stockLocks = new ConcurrentHashMap<>(); // exclusive locking for each stock (trades)
    private Map<String, ReentrantReadWriteLock> stockReadWriteLocks = new ConcurrentHashMap<>(); // read locking for each stock (lookups)
    private String csvFile = "/app/data/stocks.csv"; // path to the csv file
    private static final String frontEndService = System.getenv("frontendservice");

    public CatalogService() {
        initIfEmpty(); // initialize the stock csv with default values if empty
        loadStocks(); // load stocks from the csv file into the HashMap
        scheduleCsvSave(); // schedule a task to save stocks to csv every 60 seconds
    }

    // creates csv if necessary
    public synchronized void initIfEmpty() {
        // check if the csv file doesn't exists or is empty
        File file = new File(csvFile);
        if (!file.exists() || file.length() == 0) {
            // create a new csv file with default stock values
            InitStocks.initStocks(csvFile);
        }
    }

    // loads stocks from CSV into stock map
    public synchronized void loadStocks() {
        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");
                stockMap.put(data[0], new Stock(data[0], Double.parseDouble(data[1]), Integer.parseInt(data[2]), Integer.parseInt(data[3])));
            }
        } catch (IOException e) {
            System.out.println("Error loading stocks: " + e.getMessage());
        }
    }

    // schedule a task to save stocks to CSV every 60 seconds
    public void scheduleCsvSave() {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this::storeStocks, 31, 30, TimeUnit.SECONDS);
    }

    // saves stock map to CSV 
    public synchronized void storeStocks() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvFile))) {
            for (Stock stock : stockMap.values()) {
                bw.write(stock.getName() + "," + stock.getPrice() + "," + stock.getVolume() + "," + stock.getQuantity());
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("Error storing stocks: " + e.getMessage());
        }
    }

    public Stock getStock(String name) {
        stockReadWriteLocks.putIfAbsent(name, new ReentrantReadWriteLock()); // Give stock a lock
        ReentrantReadWriteLock lock = stockReadWriteLocks.get(name); // lock corresponds to specific stock
        lock.readLock().lock(); // Read lock only this specific stock
        Stock stock = null;
        try {
        stock =  stockMap.get(name);
        } finally {
            lock.readLock().unlock(); // unlock here
        }
        return stock; // return stock object
    }

    // processes order requests and updates stock map if order is successful
    public boolean updateStock(String name, int quantityChange, String type) {
        Stock stock = stockMap.get(name);
        if (stock == null) {
            return false; // stock not found
        }

        // validate type
        if (!(type.equalsIgnoreCase("buy") || type.equalsIgnoreCase("sell"))) {
            return false; // invalid type
        }

        // sign quantityChange
        if (type.equalsIgnoreCase("buy")) {
            quantityChange = quantityChange * -1; // a buy order has negative quanityChange
        }

        // Lock specific stock and attempt to update
        stockLocks.putIfAbsent(name, new ReentrantLock()); // Give stock a lock
        ReentrantLock lock = stockLocks.get(name); // lock corresponds to specific stock
        lock.lock(); // Lock only this specific stock

        try {
            // check if stock is available
            int newQuantity = stock.getQuantity() + quantityChange;
            if (newQuantity < 0) {
                return false; // could not complete trade, not enough stock
            }
            // allow trade to go through
            stock.addVolume(Math.abs(quantityChange));
            stock.setQuantity(newQuantity);
        } finally {
            lock.unlock(); // unlock here
        }

        return true;
    }

    public static void main(String[] args) throws IOException {
        
        // catalog server listens on port 8081       
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0); 

        server.createContext("/catalog", new CatalogHandler()); // catalog service endpoint
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // creates threads as needed, reuses when possible
        server.start();
    }

    static class CatalogHandler implements HttpHandler {
        CatalogService catalog = new CatalogService(); // load catalog and schedule save
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) { // handle lookup requests
                
                // read request: (Parse query to get stock name)
                String query = exchange.getRequestURI().getQuery();
                String stockName = null;
                if (query != null) stockName = query.split("=")[1]; // assuming the query is "name=stockName"
                // look up stock
                Stock stock = catalog.getStock(stockName);
                LookUpResponse response;

                // send response: LookUpResponse(String name, double price, int quantity)
                if (stock != null) {
                    response = new LookUpResponse(stock.getName(), stock.getPrice(), stock.getQuantity());
                    
                } else {
                    // if stock not found, make an empty object 
                    response = new LookUpResponse();
                }
                JsonResponse.sendJsonResponse(exchange, response);
                exchange.close();


            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) { // handle order requests
                // read request: (String, stockName, int quantity, String type)
                String requestBody;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                    requestBody = reader.lines().collect(Collectors.joining("\n"));
                }
                ObjectMapper objectMapper = new ObjectMapper();
                OrderRequest order = objectMapper.readValue(requestBody, OrderRequest.class);
                String stockName = order.getName();
                int quantity = order.getQuantity();
                String type = order.getType();

                // attempt to place order
                boolean success = catalog.updateStock(stockName, quantity, type);

                if(success) {
                    // send Invalidation request to frontend service to update the cache
                    sendInvalidationRequest(stockName);
                }

                // send response: (empty body), header code indicates success or failure
                exchange.sendResponseHeaders(success ? 200 : 404, 0);
                exchange.close();
            }
        }
    }

    // sends invalidation request to frontend service to update cache
    private static void sendInvalidationRequest(String stockName) {
        try {
            URL url = new URI("http://" + frontEndService + ":8080/updateCache?name=" + stockName).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(30);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.getResponseCode();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}