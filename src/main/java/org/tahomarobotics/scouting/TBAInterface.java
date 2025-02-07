package org.tahomarobotics.scouting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TBAInterface {
    private static final Logger LOGGER = LoggerFactory.getLogger(TBAInterface.class);

    // Public Methods

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

            // Create an HTTP client
            HttpResponse<String> response;

            try (
                    HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build()
            ) {

                // Build the HTTP request with the API key in the header
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(object)
                        .header("X-TBA-Auth-Key", apiKey())
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

    private static String apiKey() {
        String keyEnvVar = System.getenv("KOALA_TBA_API_KEY");
        
        if (keyEnvVar == null) {
            LOGGER.error("API key not found in environment variable KOALA_TBA_API_KEY. Please add it if you want to use the TBA API.");
            return null;
        } else {
            return keyEnvVar;
        }
    }
}