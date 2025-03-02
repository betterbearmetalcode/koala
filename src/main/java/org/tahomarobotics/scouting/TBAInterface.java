package org.tahomarobotics.scouting;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.intellij.lang.annotations.RegExp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;

public class TBAInterface {
    private static final Logger LOGGER = LoggerFactory.getLogger(TBAInterface.class);

    /**
     * Base URL for The Blue Alliance API
     */
    public static final String TBA_API = "https://www.thebluealliance.com/api/v3";

    /**
     * Method to fetch data from The Blue Alliance API
     * @param endpoint the endpoint of the data you want to get. An example would be "/status".
     * @return the JSON data from The Blue Alliance API, or null if an error occurs
     * @see <a href="https://www.thebluealliance.com/apidocs/v3">https://www.thebluealliance.com/apidocs/v3</a>
     */
    public static String getTBAData(String endpoint) {
        try {
            // Construct the full URI by combining the base server URL and the endpoint
            URI object = new URI(TBA_API + endpoint);
            
            HttpResponse<String> response;

            try {
                // Don't put into try-with-resources because HttpClient cannot be converted to AutoCloseable
                HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                
                // Build the HTTP request with the API key in the header
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(object)
                        .header("X-TBA-Auth-Key", getConfig())
                        .GET()
                        .build();

                // Send the request and receive the response
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }

            // Handle different response status codes
            if (response.statusCode() == 401) {
                LOGGER.error("Authorization token is not valid");
                return null;
            } else if (response.statusCode() == 404) {
                LOGGER.error("Endpoint not found");
                return null;
            } else {
                LOGGER.info("Got good response from " + TBA_API + "{}", endpoint);
                return response.body();
            }
        } catch (URISyntaxException e) {
            LOGGER.error("Invalid URI: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Checks with TBA to see if the provided event key is valid
     * @param key the key to check
     * @return true if the key is valid
     */
    public static boolean isValidEventKey(String key) {
        Gson gson = new Gson();
        if (key.matches("\\p{Punct}"))
            return false;
        JsonObject tbaData = gson.fromJson(getTBAData("/event/" + key + "/simple"), JsonObject.class);

        if (tbaData == null) { return false; }
        return !tbaData.has("Error");
    }

    private static String getConfig() {
        Gson gson = new Gson();
        try (InputStream inputStream = TBAInterface.class.getClassLoader().getResourceAsStream("schema/2025/game.json")) {
            if (inputStream == null) {
                LOGGER.error("Could not find the schema");
                return null;
            }
            InputStreamReader reader = new InputStreamReader(inputStream);
            var schema = gson.fromJson(reader, Map.class);
            return new String(Base64.getDecoder().decode(schema.get("hash").toString()));
        } catch (IOException e) {
            LOGGER.error("Failed to read from resources: ", e);
            return null;
        }
    }
}