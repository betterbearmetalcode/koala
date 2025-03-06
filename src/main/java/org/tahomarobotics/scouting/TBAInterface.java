package org.tahomarobotics.scouting;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.http2.Header;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

public class TBAInterface {
    private static final Logger LOGGER = LoggerFactory.getLogger(TBAInterface.class);
    private static final OkHttpClient CLIENT = new OkHttpClient();
    public static final String TBA_API = "https://www.thebluealliance.com/api/v3";

    /**
     * Method to fetch data from The Blue Alliance API <br>
     *
     * @param endpoint the endpoint of the data you want to get. An example would be "/status".
     * @param headers  the headers to send with the request
     * @return the JSON data from The Blue Alliance API, or null if an error occurs
     * @see <a href="https://www.thebluealliance.com/apidocs/v3">https://www.thebluealliance.com/apidocs/v3</a>
     */
    @Nullable
    public static Response getTBAResponse(String endpoint, Headers headers) {
        try {
            Request request = new Request.Builder().url(TBA_API + endpoint).headers(headers).header("X-TBA-Auth-Key", Objects.requireNonNull(getConfig())).build();

            return CLIENT.newCall(request).execute();
        } catch (IOException | NullPointerException e) {
            LOGGER.error("Failed to execute request: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Method to fetch data from The Blue Alliance API <br>
     *
     * @param endpoint the endpoint of the data you want to get. An example would be "/status".
     * @param headers  the headers to send with the request
     * @return the JSON data from The Blue Alliance API, or null if an error occurs
     * @see <a href="https://www.thebluealliance.com/apidocs/v3">https://www.thebluealliance.com/apidocs/v3</a>
     */
    @Nullable
    public static Response getTBAResponse(String endpoint, Header... headers) {
        Headers.Builder headersBuilder = new Headers.Builder();
        for (Header header : headers) {
            headersBuilder.add(header.name.utf8(), header.value.utf8());
        }
        return getTBAResponse(endpoint, headersBuilder.build());
    }
    
    @Nullable
    public static Response getTBAResponse(String endpoint, String ifNoneMatch, Headers headers) {
        return getTBAResponse(endpoint, new Headers.Builder().addAll(headers).add("If-None-Match", ifNoneMatch).build());
    }
    
    @Nullable
    public static Response getTBAResponse(String endpoint, String ifNoneMatch, Header... headers) {
        Headers.Builder headersBuilder = new Headers.Builder();
        for (Header header : headers) {
            headersBuilder.add(header.name.utf8(), header.value.utf8());
        }
        headersBuilder.add("If-None-Match", ifNoneMatch);
        return getTBAResponse(endpoint, headersBuilder.build());
    }

    /**
     * Method to fetch data from The Blue Alliance API
     *
     * @param endpoint the endpoint of the data you want to get. An example would be "/status".
     * @return the JSON data from The Blue Alliance API, or null if an error occurs
     * @see <a href="https://www.thebluealliance.com/apidocs/v3">https://www.thebluealliance.com/apidocs/v3</a>
     */
    public static String getTBAData(String endpoint) {
        try (Response response = getTBAResponse(endpoint)) {
            assert response != null;
            int statusCode = response.code();
            if (statusCode == 401) {
                LOGGER.error("Authorization token is not valid");
                return null;
            } else if (statusCode == 404) {
                LOGGER.error("Endpoint not found");
                return null;
            } else if (response.body() != null) {
                LOGGER.info("Got good response from " + TBA_API + "{}", endpoint);
                return response.body().string();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to execute request: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Checks with TBA to see if the provided event key is valid
     *
     * @param key the key to check
     * @return true if the key is valid
     */
    public static boolean isValidEventKey(String key) {
        Gson gson = new Gson();
        if (key.matches("\\p{Punct}")) return false;
        JsonObject tbaData = gson.fromJson(getTBAData("/event/" + key + "/simple"), JsonObject.class);

        if (tbaData == null) {
            return false;
        }
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