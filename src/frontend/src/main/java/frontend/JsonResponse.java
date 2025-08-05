package frontend.src.main.java.frontend;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

public class JsonResponse {
    

    public static void sendJsonResponse(HttpExchange exchange, Object response) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            String json = mapper.writeValueAsString(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(responseBytes);
            os.close();
        }
 
        // There are possibilities of following exceptions,
        // so catch block to handle the exceptions
        catch (JsonGenerationException e) {
            System.out.println("Error generating JSON in JsonResponse.java: " + e.getMessage());
        }
        catch (JsonMappingException e) {
            System.out.println("Error mapping JSON in JsonResponse.java: " + e.getMessage());
        }
        catch(Exception e) {
            System.out.println("Error in JsonResponse.java: " + e.getMessage());
        } 
        finally {
            if (exchange != null) {
                exchange.close();
            }
        }
    }

    
    public static JsonNode readResponse(HttpURLConnection connection) throws Exception {
        StringBuilder response = null;
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } catch (Exception e) {
            System.out.println("Error reading response in JsonResponse.java: " + e.getMessage());
        }
        if(response != null) {
            JsonNode jsonNode = new ObjectMapper().readTree(response.toString());
            return jsonNode;
        }
        
        return null;
    } 
}
