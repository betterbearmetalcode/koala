package org.tahomarobotics.scouting;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class TBAInterface {
    private static final Logger logger = LoggerFactory.getLogger(TBAInterface.class);

    // Public Methods

    /**
     * Base URL for The Blue Alliance API
     */
    public static final String TBASERVER = "https://www.thebluealliance.com/api/v3";

    /**
     * Method to fetch data from The Blue Alliance API
     * @param endpoint the endpoint of the data you want to get. An example would be "/status".
     * @return the JSON data from The Blue Alliance API, or null if an error occurs
     * @see <a href="https://www.thebluealliance.com/apidocs/v3">https://www.thebluealliance.com/apidocs/v3</a>
     */
    public static String getTBAData(String endpoint) {
        try {
            // Construct the full URI by combining the base server URL and the endpoint
            URI object = new URI(TBASERVER + endpoint);

            // Create an HTTP client
            HttpResponse<String> response;
            try {
                 HttpClient client = HttpClient.newBuilder()
                         .version(HttpClient.Version.HTTP_2)
                         .followRedirects(HttpClient.Redirect.NORMAL)
                         .build();
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
                logger.error("Authorization token is not valid");
                return null;
            } else if (response.statusCode() == 404) {
                logger.error("Endpoint not found");
                return null;
            } else {
                logger.info("Got good response from " + TBASERVER + "{}", endpoint);
                return response.body();
            }
        } catch (URISyntaxException e) {
            logger.error("Invalid URI: {}", e.getMessage());
        }
        return null;
    }


    // Private Method

    private static String apiKey() {
        String keyEnvVar = System.getenv("KOALA_TBA_API_KEY");
        
        if (keyEnvVar == null) {
            logger.error("API key not found in environment variable KOALA_TBA_API_KEY. Please add it if you want to use the TBA API.");
            return null;
        } else {
            return keyEnvVar;
        }
        
        // OLD CODE THAT COULD BE REMOVED BUT IS STILL HERE BECAUSE MAYBE WE WANT TO USE IT IN THE FUTURE
        
//        Gson gson = new Gson();
//        try (InputStream inputStream = TBAInterface.class.getClassLoader().getResourceAsStream("APIKeys.json")) {
//            if (inputStream == null) {
//                logger.error("APIKeys.json file not found in the resources directory.");
//                return null;
//            }
//            InputStreamReader reader = new InputStreamReader(inputStream);
//            var apiKeys = gson.fromJson(reader, Map.class);
//            return (String) apiKeys.get("apiKeyTBA");
//        } catch (IOException e) {
//            logger.error("Failed to read APIKeys.json from resources: ", e);
//            return null;
//        }
    }
}