package org.tahomarobotics.scouting;

import com.google.gson.*;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bson.Document;
import org.tahomarobotics.scouting.util.JsonUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * DatabaseManager manages CRUD operations for a MongoDB database, and includes special methods for specific operations.
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final Gson gson = new Gson();

    // MongoDB connection parameters
    private static final String CONNECTION_STRING = "mongodb://localhost:27017";
    private static final String DATABASE_PREFIX = "KoalaScouting_";

    // Public methods

    /**
     * Gets the real database name for the specified season.
     *
     * @param year the year to get the database for
     * @return the database name.
     */
    public String getDBName(int year) {
        return DATABASE_PREFIX + year;
    }

    /**
     * Checks to see if a database exists for the specified season.
     *
     * @param year the year of the season to check for
     * @return true if the database exists
     */
    public boolean doesSeasonDatabaseExist(int year) {
        try (MongoClient mongoClient = MongoClients.create(CONNECTION_STRING)) {
            return mongoClient.listDatabaseNames().into(new ArrayList<>()).contains(getDBName(year));
        } catch (Exception e) {
            logger.error("Database error", e);
            return false;
        }
    }

    // Methods for adding teams to the database

    /**
     * Processes a JSON string representing team details and ensures the team is in the database.
     *
     * @param year     the yearly database where the team will be checked and possibly added
     * @param teamJson a JSON string containing team details. Should be similar to TBA data
     */
    public void processTeamJson(int year, String teamJson) {
        JsonObject teamObject = gson.fromJson(teamJson, JsonObject.class);

        // Data to grab from JSON
        String key = getStringValue(teamObject, "key");
        int teamNumber = getIntValue(teamObject, "team_number");
        String nickname = getStringValue(teamObject, "nickname");
        String name = getStringValue(teamObject, "name");
        String schoolName = getStringValue(teamObject, "school_name");
        String city = getStringValue(teamObject, "city");
        String stateProv = getStringValue(teamObject, "state_prov");
        String country = getStringValue(teamObject, "country");
        String website = getStringValue(teamObject, "website");
        int rookieYear = getIntValue(teamObject, "rookie_year");
        String motto = getStringValue(teamObject, "motto");
        String tbaWebsite = "https://www.thebluealliance.com/team/" + getStringValue(teamObject, "team_number");

        try (MongoClient mongoClient = MongoClients.create(CONNECTION_STRING)) {
            MongoDatabase database = mongoClient.getDatabase(getDBName(year));
            MongoCollection<Document> collection = database.getCollection("teams");

            // Check if the team already exists, then if it doesn't then add it
            Document existingTeam = collection.find(new Document("key", key)).first();
            if (existingTeam == null) {
                // Insert the new team
                Document teamDoc = new Document("key", key)
                        .append("team_number", teamNumber)
                        .append("nickname", nickname)
                        .append("name", name)
                        .append("school_name", schoolName)
                        .append("city", city)
                        .append("state_prov", stateProv)
                        .append("country", country)
                        .append("website", website)
                        .append("tba_website", tbaWebsite)
                        .append("rookie_year", rookieYear)
                        .append("motto", motto);

                collection.insertOne(teamDoc);

                logger.info("Added new team: {} ({})", nickname, key);
            } else {
                logger.info("Team {} ({}) already exists in the database.", nickname, key);
            }
        }
    }

    /**
     * Gets the details of a team from their key and adds it to the database.
     *
     * @param year the yearly database where the team will be added
     * @param key  the team key of the team to process
     */
    public void processTeamFromKey(int year, String key) {
        String teamJson = TBAInterface.getTBAData("/team/" + key);

        if (teamJson != null) {
            processTeamJson(year, teamJson);
        } else {
            logger.warn("The team was null. Does it exist?");
        }
    }

    /**
     * Gets the teams for a specified event, then puts them into the database of the specified year.
     *
     * @param year     the yearly database where the collection will be created
     * @param eventKey the event key to get teams for. A valid event key looks like this: "2025wasno" (PNW District Glacier Peak Event 2025).
     */
    public void processTeamsForEvent(int year, String eventKey) {

        String teamObjects = TBAInterface.getTBAData("/event/" + eventKey + "/teams");

        if (teamObjects == null) {
            logger.error("TBA didn't return data. Is the event key {} valid?", eventKey);
            return; // Return because we can't do anything with null data
        }

        Gson gson = new Gson();

        JsonArray jsonArray = JsonParser.parseString(teamObjects).getAsJsonArray();

        for (JsonElement element : jsonArray) {
            processTeamJson(year, element.toString());
        }
    }

    /**
     * Gets the teams for a specified event, then puts them into the database of the year that they correspond to.
     *
     * @param eventKey the event key to get teams for. A valid event key looks like this: "2025wasno" (PNW District Glacier Peak Event 2025).
     */
    public void processTeamsForEvent(String eventKey) {
        int year = Integer.parseInt(eventKey.substring(0, 4));

        processTeamsForEvent(year, eventKey);
    }

    /**
     * Gets team keys from an arraylist and adds their details to the database. Useful for off season events or when there is no data from TBA on an event
     *
     * @param year  the yearly database where the teams will be added
     * @param teams an arraylist of teams to add. The arraylist should be team keys. Example: [frc2046, frc1678, frc2910].
     */
    public void processTeamsFromArrayList(int year, ArrayList<String> teams) {
        for (String i : teams) {
            processTeamFromKey(year, i);
        }
    }

    // Methods for adding matches to the database

    /**
     * Processes a JSON string representing match details from the main scout (scouting one individual team).
     *
     * @param year     the yearly database where the document will be added
     * @param matchJson a JSON string containing match details. Should follow the yearly schema for main scouting to the letter.
     */
    public void processMainScoutJson(int year, String matchJson) {
        Document matchDoc = Document.parse(matchJson);

        try (MongoClient mongoClient = MongoClients.create(CONNECTION_STRING)) {
            MongoDatabase database = mongoClient.getDatabase(getDBName(year));
            MongoCollection<Document> collection = database.getCollection("mainScout");

            // Insert the new team JSON data into the database
            collection.insertOne(matchDoc);
            logger.info("Added new main scout document from JSON data.");
        } catch (Exception e) {
            logger.error("Error inserting main scout document into the database", e);
        }
    }

    /**
     * Processes a JSON string representing match details from the strategy scout (scouting the whole alliance).
     *
     * @param year     the yearly database where the document will be added
     * @param matchJson a JSON string containing match details. Should follow the yearly schema for strategy scouting to the letter.
     */
    public void processStrategyScoutJson(int year, String matchJson) {
        Document matchDoc = Document.parse(matchJson);

        try (MongoClient mongoClient = MongoClients.create(CONNECTION_STRING)) {
            MongoDatabase database = mongoClient.getDatabase(getDBName(year));
            MongoCollection<Document> collection = database.getCollection("strategyScout");

            // Insert the new team JSON data into the database
            collection.insertOne(matchDoc);
            logger.info("Added new strategy scout document from JSON data.");
        } catch (Exception e) {
            logger.error("Error inserting strategy scout document into the database", e);
        }
    }

    // Methods for getting things from the database

    public List<HashMap<String, Object>> getMatchesFromTeam(int year, String teamKey, String eventKey) {
        try (MongoClient mongoClient = MongoClients.create(CONNECTION_STRING)) {
            MongoDatabase database = mongoClient.getDatabase(getDBName(year));
            MongoCollection<Document> collection = database.getCollection("mainScout");

            Bson filter = Filters.and(
                    Filters.eq("team_key", teamKey),
                    Filters.eq("event_key", eventKey)
            );
            MongoCursor<Document> cursor = collection.find(filter).iterator();
            List<HashMap<String, Object>> documentsList = new ArrayList<>();

            try {
                while (cursor.hasNext()) {
                    Document document = cursor.next();
                    HashMap<String, Object> map = JsonUtil.getHashMapFromJson(document.toJson());
                    documentsList.add(map);
                }
            } finally {
                cursor.close();
            }

            return documentsList;
        }
    }

    public List<HashMap<String, Object>> getTeamsFromMatch(int year, int matchNumber, String eventKey) {
        try (MongoClient mongoClient = MongoClients.create(CONNECTION_STRING)) {
            MongoDatabase database = mongoClient.getDatabase(getDBName(year));
            MongoCollection<Document> collection = database.getCollection("mainScout");

            Bson filter = Filters.and(
                    Filters.eq("match_num", matchNumber),
                    Filters.eq("event_key", eventKey)
            );
            MongoCursor<Document> cursor = collection.find(filter).iterator();
            List<HashMap<String, Object>> documentsList = new ArrayList<>();

            try {
                while (cursor.hasNext()) {
                    Document document = cursor.next();
                    HashMap<String, Object> map = JsonUtil.getHashMapFromJson(document.toJson());
                    documentsList.add(map);
                }
            } finally {
                cursor.close();
            }

            return documentsList;
        }
    }

    // Private methods

    private String getStringValue(JsonObject jsonObject, String key) {
        return jsonObject.has(key) && !jsonObject.get(key).isJsonNull() ? jsonObject.get(key).getAsString() : null;
    }

    private int getIntValue(JsonObject jsonObject, String key) {
        return jsonObject.has(key) && !jsonObject.get(key).isJsonNull() ? jsonObject.get(key).getAsInt() : 0;
    }
}