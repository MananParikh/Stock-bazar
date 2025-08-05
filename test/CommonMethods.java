package test;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CommonMethods {

    public static JsonNode sendlookUpRequest(URI uri) throws Exception {
        URL url = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");

        return readResponse(connection);
    }

    public static JsonNode sendOrderRequest(URI uri, String stockName, int quantity, String type) throws Exception {
        URL url = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        // Set content type header,
        // input (Content-Type) is in JSON (application/json) format.
        connection.setRequestProperty("Content-Type", "application/json");

        // Calling the API and send request data
        // connection.getOutputStream() purpose is to obtain an output stream for sending data to the server.

        String data_to_send = "{\"name\": \"" + stockName + "\", \"quantity\": " + quantity + ", \"type\" : \"" + type + "\"}";

        try (DataOutputStream os = new DataOutputStream(connection.getOutputStream())) {
            os.writeBytes(data_to_send);
            os.flush();
        }

        return readResponse(connection);
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
            e.printStackTrace();
        }
        if(response != null) {
            JsonNode jsonNode = new ObjectMapper().readTree(response.toString());
            return jsonNode;
        }
        return null;
    }
}