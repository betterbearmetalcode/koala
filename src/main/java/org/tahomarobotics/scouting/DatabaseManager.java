package org.tahomarobotics.scouting;

import com.google.gson.*;
import com.mongodb.client.*;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.vault.RangeOptions;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bson.Document;
import org.tahomarobotics.scouting.util.JsonUtil;

import javax.swing.*;
import java.util.*;

/**
 * DatabaseManager manages CRUD operations for scouting data in a MongoDB database.
 * It handles team and match data, organizing it by season.
 * The class also integrates with The Blue Alliance (TBA) API to retrieve team and event details.
 */
public class DatabaseManager {

    private final int year;
    private final MongoClient mongoClient;

    /**
     * Constructs a DatabaseManager for a specific year.
     *
     * @param year the year for which the database manager is being created.
     */
    public DatabaseManager(int year) {
        this.year = year;
        this.mongoClient = MongoClients.create(CONNECTION_STRING);
    }

    // MongoDB connection parameters
    private static final String CONNECTION_STRING = "mongodb://localhost:27017";
    private static final String DATABASE_PREFIX = "KoalaScouting_";

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final Gson gson = new Gson();

    /**
     * Gets the real database name for the specified season.
     *
     * @return the database name.
     */
    public String getDBName() {
        return DATABASE_PREFIX + year;
    }

    /**
     * Gets all matches from TBA for a specific event and adds them to the database.
     *
     * @param eventKey the event key to get matches for.
     */
    public void getAllMatchesFromTBA(String eventKey) {
        String matchesJson = TBAInterface.getTBAData("/event/" + eventKey + "/matches");
        if (matchesJson == null) {
            logger.error("TBA didn't return data. Is the event key {} valid?", eventKey);
            return;
        }

        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection("tbaMatches");
        JsonArray jsonArray = JsonParser.parseString(matchesJson).getAsJsonArray();

        logger.info("Ready to add {} matches to the database.", jsonArray.size());
        for (JsonElement element : jsonArray) {
            Document matchDoc = Document.parse(element.toString());
            String matchKey = matchDoc.getString("key");

            // Check if the match already exists in the database
            Document existingMatch = collection.find(new Document("key", matchKey)).first();
            if (existingMatch == null) {
                try {
                    collection.insertOne(matchDoc);
                    logger.info("Added new match document from TBA data.");
                } catch (Exception e) {
                    logger.error("Error inserting match document: {}", e.getMessage());
                }
            } else {
                logger.info("Match with key {} already exists in the database.", matchKey);
            }
        }
        logger.info("Added {} matches to the database.", jsonArray.size());
    }

    /**
     * Processes a JSON string representing team details and ensures the team is in the database.
     *
     * @param teamJson a JSON string containing team details. Should be similar to TBA data.
     */
    public void processTeamJson(String teamJson, String... events) {
        if (teamJson == null) {
            logger.warn("Team JSON is null.");
            return;
        }

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

        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection("teams");

        // Check if the team already exists, then if it doesn't then add it
        Document existingTeam = collection.find(new Document("key", key)).first();
        if (existingTeam == null) {
            // Insert the new team
            Document teamDoc = new Document("key", key)
                    .append("team_number", teamNumber)
                    .append("nickname", nickname)
                    .append("events", Arrays.asList(events))
                    .append("name", name)
                    .append("school_name", schoolName)
                    .append("city", city)
                    .append("state_prov", stateProv)
                    .append("country", country)
                    .append("website", website)
                    .append("tba_website", tbaWebsite)
                    .append("rookie_year", rookieYear)
                    .append("motto", motto);

            try {
                collection.insertOne(teamDoc);
                logger.info("Added new team: {} ({})", nickname, key);
            } catch (Exception e) {
                logger.error("Error inserting team: {}", e.getMessage());
            }
        } else {
            logger.info("Team {} ({}) already exists in the database.", nickname, key);
        }
    }

    /**
     * Adds events to a team in the database.
     *
     * @param teamKey the team key to add events to.
     * @param events  the events to add.
     */
    public void addEventsToTeam(String teamKey, String... events) {
        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection("teams");

        Document existingTeam = collection.find(new Document("key", teamKey)).first();
        if (existingTeam == null) {
            logger.warn("Team {} does not exist in the database.", teamKey);
            return;
        }

        List<String> existingEvents = existingTeam.getList("events", String.class, new ArrayList<>());
        existingEvents.addAll(Arrays.asList(events));
        existingEvents = new ArrayList<>(new HashSet<>(existingEvents)); // Remove duplicates

        collection.updateOne(Filters.eq("key", teamKey), new Document("$set", new Document("events", existingEvents)));
        logger.info("Added events to team {}: {}", teamKey, Arrays.toString(events));
    }

    /**
     * Gets the details of a team from their key and adds it to the database.
     *
     * @param key the team key of the team to process.
     */
    public void processTeamFromKey(String key, String... events) {
        String teamJson = TBAInterface.getTBAData("/team/" + key);

        if (teamJson != null) {
            processTeamJson(teamJson, events);
        } else {
            logger.error("TBA didn't return data. Does the team {} exist?", key);
        }
    }

    /**
     * Gets the teams for a specified event, then adds them into the database.
     *
     * @param eventKey the event key to get teams for. A valid event key looks like this: "2025wasno" (PNW District Glacier Peak Event 2025).
     */
    public void processTeamsForEvent(String eventKey) {
        if (Integer.parseInt(eventKey.substring(0, 4)) == year) {
            String teamObjects = TBAInterface.getTBAData("/event/" + eventKey + "/teams");

            if (teamObjects == null) {
                logger.error("TBA didn't return data. Is the event key {} valid?", eventKey);
                return; // Return because we can't do anything with null data
            }

            JsonArray jsonArray = JsonParser.parseString(teamObjects).getAsJsonArray();

            for (JsonElement element : jsonArray) {
                processTeamJson(element.toString(), eventKey);
            }
        } else {
            logger.warn("Event key {} is not for the year {}.", eventKey, year);
        }
    }

    /**
     * Gets team keys from an arraylist and adds their details to the database. Useful for off season events or when there is no data from TBA on an event.
     *
     * @param teams an arraylist of teams to add. The arraylist should be team keys. Example: [frc2046, frc1678, frc2910].
     */
    public void processTeamsFromArrayList(ArrayList<String> teams) {
        for (String i : teams) {
            processTeamFromKey(i);
        }
    }

    /**
     * Processes a JSON string representing match details from the main scout (scouting one individual team).
     *
     * @param matchJson a JSON string containing match details. Should follow the yearly schema for main scouting to the letter.
     */
    public void processMainScoutJson(String matchJson) {
        Document matchDoc = Document.parse(matchJson);

        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection("mainScout");

        // Insert the new team JSON data into the database
        try {
            collection.insertOne(matchDoc);
            logger.info("Added new main scout document from JSON data.");
        } catch (Exception e) {
            logger.error("Error inserting main scout document: {}", e.getMessage());
        }
    }

    /**
     * Processes a JSON string representing match details from the strategy scout (scouting the whole alliance).
     *
     * @param matchJson a JSON string containing match details. Should follow the yearly schema for strategy scouting to the letter.
     */
    public void processStrategyScoutJson(String matchJson) {
        Document matchDoc = Document.parse(matchJson);
        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection("strategyScout");

        // Insert the new team JSON data into the database
        try {
            collection.insertOne(matchDoc);
            logger.info("Added new strategy scout document from JSON data.");
        } catch (Exception e) {
            logger.error("Error inserting strategy scout document: {}", e.getMessage());
        }
    }

    public void processPitsJson(String matchJson) {
        Document matchDoc = Document.parse(matchJson);
        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection("pits");

        // Insert the new pits JSON data into the database
        try {
            collection.insertOne(matchDoc);
            logger.info("Added new pits document from JSON data.");
        } catch (Exception e) {
            logger.error("Error inserting pits document: {}", e.getMessage());
        }
    }

    /**
     * Gets matches from a team for a specific event.
     *
     * @param teamKey  the team key to filter matches.
     * @param eventKey the event key to filter matches.
     * @return a list of matches for the specified team and event.
     */
    public List<HashMap<String, Object>> getMatchesFromTeam(String teamKey, String eventKey) {
        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection("mainScout");

        Bson filter = Filters.and(
                Filters.eq("team_key", teamKey),
                Filters.eq("event_key", eventKey)
        );
        return getHashMaps(collection, filter);
    }

    /**
     * Gets teams from a specific match for a specific event.
     *
     * @param matchNumber the match number to filter teams.
     * @param eventKey    the event key to filter teams.
     * @return a list of teams for the specified match and event.
     */
    public List<HashMap<String, Object>> getTeamsFromMatch(int matchNumber, String eventKey) {
        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection("mainScout");

        Bson filter = Filters.and(
                Filters.eq("match_num", matchNumber),
                Filters.eq("event_key", eventKey)
        );
        return getHashMaps(collection, filter);
    }

    /**
     * Gets teams from a specific event.
     *
     * @param eventKey the event key to filter teams.
     * @return a map of team numbers and their names for the specified event.
     */
    public HashMap<Integer, String> getTeamsFromEvent(String eventKey) {
        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection("mainScout");

        Bson filter = Filters.eq("event_key", eventKey);
        List<HashMap<String, Object>> documentsList = getHashMaps(collection, filter);

        HashMap<Integer, String> teams = new HashMap<>();
        Set<String> teamKeys = new HashSet<>();

        for (HashMap<String, Object> document : documentsList) {
            String teamKey = (String) document.get("team_key");
            if (teamKey != null) {
                teamKeys.add(teamKey);
            }
        }

        MongoCollection<Document> teamCollection = database.getCollection("teams");
        for (String teamKey : teamKeys) {
            Document teamDocument = teamCollection.find(new Document("key", teamKey)).first();
            if (teamDocument != null) {
                String teamName = teamDocument.getString("nickname");
                if (teamName != null) {
                    int teamNumber = Integer.parseInt(teamKey.substring(3));
                    teams.put(teamNumber, teamName);
                }
            } else {
                logger.warn("Team document not found for key: {}", teamKey);
            }
        }

        return teams;
    }

    /**
     * Gets matches from a specific event.
     *
     * @param eventKey the event key to filter matches.
     * @return a list of matches for the specified event.
     */
    public List<HashMap<String, Object>> getMatchesFromEvent(String eventKey) {
        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection("mainScout");

        Bson filter = Filters.eq("event_key", eventKey);
        return getHashMaps(collection, filter);
    }

    public List<HashMap<String, Object>> getStratForEvent(String eventKey) {
        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection("strategyScout");

        Bson filter = Filters.eq("event_key", eventKey);
        return getHashMaps(collection, filter);
    }

    public List<HashMap<String, Object>> getPitsForEvent(String eventKey) {
        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection("pits");

        Bson filter = Filters.eq("event_key", eventKey);
        return getHashMaps(collection, filter);
    }

    /**
     * Gets the keys of a random document in the main scout collection. It also formats subkeys to be more readable.
     *
     * @return an array of keys
     */
    public String[] getKeysForMainScout() {
        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection("mainScout");

        Document random = collection.aggregate(List.of(Aggregates.sample(1))).first();

        if (random == null) {
            logger.warn("No documents found in mainScout collection for the year {}.", year);
            return new String[0];
        }

        List<String> keys = new ArrayList<>();

        for (String key : random.keySet()) {
            if (key.equals("_id") || key.equals("team_key") || key.equals("event_key") || key.equals("match_num")) {
                continue;
            }
            Object value = random.get(key);
            if (value instanceof Document subDoc) {
                for (String subKey : subDoc.keySet()) {
                    keys.add(key + "_" + subKey);
                }
            } else {
                keys.add(key);
            }
        }

        return keys.toArray(new String[0]);
    }

    /**
     * Converts MongoDB documents to a list of hash maps based on a filter.
     *
     * @param collection the MongoDB collection to query.
     * @param filter     the filter to apply to the query.
     * @return a list of hash maps representing the documents.
     */
    @NotNull
    private List<HashMap<String, Object>> getHashMaps(MongoCollection<Document> collection, Bson filter) {
        MongoCursor<Document> cursor = collection.find(filter).iterator();
        List<HashMap<String, Object>> documentsList = new ArrayList<>();

        try {
            while (cursor.hasNext()) {
                Document document = cursor.next();
                HashMap<String, Object> map = new HashMap<>(document);
                documentsList.add(map);
            }
        } finally {
            cursor.close();
        }

        return documentsList;
    }

    /**
     * Gets a string value from a JSON object.
     *
     * @param jsonObject the JSON object to extract the value from.
     * @param key        the key of the value to extract.
     * @return the string value, or null if the key does not exist or the value is null.
     */
    private String getStringValue(JsonObject jsonObject, String key) {
        return jsonObject.has(key) && !jsonObject.get(key).isJsonNull() ? jsonObject.get(key).getAsString() : null;
    }

    /**
     * Gets an integer value from a JSON object.
     *
     * @param jsonObject the JSON object to extract the value from.
     * @param key        the key of the value to extract.
     * @return the integer value, or 0 if the key does not exist or the value is null.
     */
    private int getIntValue(JsonObject jsonObject, String key) {
        return jsonObject.has(key) && !jsonObject.get(key).isJsonNull() ? jsonObject.get(key).getAsInt() : 0;
    }
}