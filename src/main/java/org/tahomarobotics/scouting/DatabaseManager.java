package org.tahomarobotics.scouting;

import com.google.gson.*;
import com.mongodb.client.*;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bson.Document;

import java.util.*;

/**
 * Manages database operations.
 */
public class DatabaseManager {

    private final int year;
    private final MongoClient mongoClient;

    // MongoDB connection parameters
    private static final String CONNECTION_STRING = "mongodb://localhost:27017";
    private static final String DATABASE_PREFIX = "KoalaScouting_";
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final Gson gson = new Gson();

    /**
     * Constructs a DatabaseManager for a specified year.
     *
     * @param year the year used to form the database name and queries.
     */
    public DatabaseManager(int year) {
        this.year = year;
        this.mongoClient = MongoClients.create(CONNECTION_STRING);
    }

    /**
     * Constructs a DatabaseManager for a specified year and MongoClient.
     *
     * @param year        the year used to form the database name and queries.
     * @param mongoClient the MongoClient instance to use for database operations.
     */
    public DatabaseManager(int year, MongoClient mongoClient) {
        this.year = year;
        this.mongoClient = mongoClient;
    }

    /**
     * Returns the database name for the current season.
     *
     * @return the fully formed database name, combining a prefix with the year.
     */
    public String getDBName() {
        return DATABASE_PREFIX + year;
    }

    /**
     * Pulls data from The Blue Alliance (TBA) for a given database type and event.
     *
     * <p>Supported database types:
     * <ul>
     *   <li><code>TEAMS</code>: Pulls team data for each team participating in an event.</li>
     *   <li><code>TBA_MATCHES</code>: Pulls all match data for the event that exists.</li>
     * </ul>
     * Unsupported database types will log a warning.
     * </p>
     *
     * @param databaseType the type of database to pull data into.
     * @param eventKey     the event key for which data should be retrieved.
     */
    public void pullFromTBA(DatabaseType databaseType, String eventKey) {
        switch (databaseType) {
            case TEAMS -> {
                if (Integer.parseInt(eventKey.substring(0, 4)) != year) {
                    logger.warn("Event key {} is not for the year {}.", eventKey, year);
                }
                String teamObjects = TBAInterface.getTBAData("/event/" + eventKey + "/teams");
                if (teamObjects == null) {
                    logger.error("TBA didn't return data for query /event/{}/teams. Is the event key {} valid?", eventKey, eventKey);
                    return;
                }
                JsonArray jsonArray = JsonParser.parseString(teamObjects).getAsJsonArray();
                for (JsonElement element : jsonArray) {
                    processJSON(DatabaseType.TEAMS, element.toString(), eventKey);
                }
            }
            case TBA_MATCHES -> {
                String matchesJson = TBAInterface.getTBAData("/event/" + eventKey + "/matches");
                if (matchesJson == null) {
                    logger.error("TBA didn't return data for query /event/{}/matches. Is the event key {} valid?", eventKey, eventKey);
                    return;
                }
                MongoDatabase database = mongoClient.getDatabase(getDBName());
                MongoCollection<Document> collection = database.getCollection(databaseType.getCollectionName());
                JsonArray jsonArray = JsonParser.parseString(matchesJson).getAsJsonArray();

                List<Document> newMatches = new ArrayList<>();
                List<String> matchKeys = new ArrayList<>();
                for (JsonElement element : jsonArray) {
                    Document matchDoc = Document.parse(element.toString());
                    String matchKey = matchDoc.getString("key");
                    matchKeys.add(matchKey);
                }

                List<Document> existingMatches = collection.find(Filters.in("key", matchKeys)).into(new ArrayList<>());
                Set<String> existingSet = new HashSet<>();
                for (Document doc : existingMatches) {
                    existingSet.add(doc.getString("key"));
                }

                for (JsonElement element : jsonArray) {
                    Document matchDoc = Document.parse(element.toString());
                    String matchKey = matchDoc.getString("key");
                    if (!existingSet.contains(matchKey)) {
                        newMatches.add(matchDoc);
                    }
                }
                if (!newMatches.isEmpty()) {
                    collection.insertMany(newMatches);
                    logger.info("Added {} new matches to the database.", newMatches.size());
                } else {
                    logger.info("No new matches to insert.");
                }
            }
            default -> logger.warn("Database {} is not supported for pulling from TBA.", databaseType);
        }
    }

    /**
     * Processes a JSON string and adds its data to the appropriate database collection.
     *
     * <p>Supported database types:
     * <ul>
     *   <li><code>TEAMS</code>: Processes team details and associates optional events. If the team already exists, no data will be updated other than new events.</li>
     *   <li><code>MATCH</code>, <code>STRATEGY</code>, <code>PITS</code>: Inserts match, strategy, or pit data respectively. If data already exists for the match, it will be updated.</li>
     * </ul>
     * Unsupported database types will log a warning.
     * </p>
     *
     * @param databaseType the type of database to process.
     * @param json         the JSON string to be processed.
     * @param events       optional event keys associated with the data (used only for <code>TEAMS</code>).
     */
    public void processJSON(DatabaseType databaseType, String json, String... events) {
        if (json == null) {
            logger.warn("{} JSON is null. Returning...", databaseType.getCollectionName());
            return;
        }

        JsonObject jsonObject = gson.fromJson(json, JsonObject.class);

        switch (databaseType) {
            case TEAMS -> {
                // Extract data from JSON.
                String key = getStringValue(jsonObject, "key");
                int teamNumber = getIntValue(jsonObject, "team_number");
                String nickname = getStringValue(jsonObject, "nickname");
                String name = getStringValue(jsonObject, "name");
                String schoolName = getStringValue(jsonObject, "school_name");
                String city = getStringValue(jsonObject, "city");
                String stateProv = getStringValue(jsonObject, "state_prov");
                String country = getStringValue(jsonObject, "country");
                String website = getStringValue(jsonObject, "website");
                int rookieYear = getIntValue(jsonObject, "rookie_year");
                String motto = getStringValue(jsonObject, "motto");
                String tbaWebsite = "https://www.thebluealliance.com/team/" + getStringValue(jsonObject, "team_number");

                MongoDatabase database = mongoClient.getDatabase(getDBName());
                MongoCollection<Document> collection = database.getCollection(databaseType.getCollectionName());

                Document existingTeam = collection.find(new Document("key", key)).first();
                if (existingTeam == null) {
                    Document teamDoc = new Document("key", key).append("team_number", teamNumber).append("nickname", nickname).append("events", Arrays.asList(events)).append("name", name).append("school_name", schoolName).append("city", city).append("state_prov", stateProv).append("country", country).append("website", website).append("tba_website", tbaWebsite).append("rookie_year", rookieYear).append("motto", motto);

                    try {
                        collection.insertOne(teamDoc);
                        logger.info("Added new team: {} ({})", nickname, key);
                    } catch (Exception e) {
                        logger.error("Error inserting team: {}", e.getMessage());
                    }
                } else {
                    logger.info("Team {} ({}) already exists in the database. Still updating events.", nickname, key);

                    List<String> existingEvents = existingTeam.getList("events", String.class);
                    List<String> newEvents = new ArrayList<>(Arrays.asList(events));
                    newEvents.removeAll(existingEvents);
                    if (!newEvents.isEmpty()) {
                        existingEvents.addAll(newEvents);
                    }
                    try {
                        collection.updateOne(Filters.eq("key", key), new Document("$set", new Document("events", existingEvents)));
                    } catch (Exception e) {
                        logger.error("Error updating team events to {}: {}", existingEvents, e.getMessage());
                    }
                }
            }
            case MATCH, STRATEGY, PITS -> {
                Document matchDoc = Document.parse(json);

                MongoDatabase database = mongoClient.getDatabase(getDBName());
                MongoCollection<Document> collection = database.getCollection(databaseType.getCollectionName());

                try {
                    collection.insertOne(matchDoc);
                    logger.info("Added new {} document from JSON data.", databaseType.getCollectionName());
                } catch (Exception e) {
                    logger.error("Error inserting {} document: {}", databaseType.getCollectionName(), e.getMessage());
                }
            }
            default -> logger.warn("Database {} is not supported for processing JSON.", databaseType);
        }
    }

    /**
     * Retrieves data related to a specific match.
     *
     * <p>Supported database types:
     * <ul>
     *   <li><code>TEAMS</code>: Retrieves team objects of every team that participated in the specified match</li>
     *   <li><code>MATCH</code> and <code>STRATEGY</code>: Retrieves match and strat objects respectively for the specified match.</li>
     * </ul>
     * Unsupported database types will log a warning.
     * </p>
     *
     * @param databaseType the type of database.
     * @param matchNumber  the match number.
     * @param eventKey     the event key.
     * @return a list of hash maps representing the match data.
     */
    public List<HashMap<String, Object>> getDataFromMatch(DatabaseType databaseType, int matchNumber, String eventKey) {
        switch (databaseType) {
            case TEAMS -> {
                List<HashMap<String, Object>> matchData = getDataFromMatch(DatabaseType.MATCH, matchNumber, eventKey);
                if (matchData.isEmpty()) {
                    logger.warn("No match data found for match number {} and event key {}.", matchNumber, eventKey);
                    return List.of();
                }
                Set<String> teamKeys = new HashSet<>();
                for (HashMap<String, Object> match : matchData) {
                    teamKeys.add("frc" + match.get("team"));
                }
                MongoDatabase database = mongoClient.getDatabase(getDBName());
                MongoCollection<Document> collection = database.getCollection(DatabaseType.TEAMS.getCollectionName());
                List<Document> teamDocs = collection.find(Filters.in("key", teamKeys)).into(new ArrayList<>());
                List<HashMap<String, Object>> teams = new ArrayList<>();
                for (Document doc : teamDocs) {
                    teams.add(new HashMap<>(doc));
                }
                return teams;
            }
            case MATCH -> {
                MongoDatabase database = mongoClient.getDatabase(getDBName());
                MongoCollection<Document> collection = database.getCollection(databaseType.getCollectionName());
                Bson filter = Filters.and(Filters.eq("match", Integer.toString(matchNumber)), Filters.eq("event_key", eventKey));
                return getHashMaps(collection, filter);
            }
            case STRATEGY -> {
                MongoDatabase database = mongoClient.getDatabase(getDBName());
                MongoCollection<Document> collection = database.getCollection(databaseType.getCollectionName());
                Bson filter = Filters.and(Filters.eq("match", matchNumber), Filters.eq("event_key", eventKey));
                return getHashMaps(collection, filter);
            }
            default -> logger.warn("Database {} is not supported for getting from match.", databaseType);
        }
        return List.of();
    }

    /**
     * Retrieves all objects from the specified event.
     *
     * <p>Supported database types:
     * <ul>
     *   <li><code>TEAMS</code>, <code>MATCH</code>, <code>STRATEGY</code>, <code>PITS</code>, and <code>TBA_MATCHES</code></li>
     * </ul>
     * Unsupported database types will log a warning.
     * </p>
     *
     * @param databaseType the type of database.
     * @param eventKey     the event key identifier.
     * @return a list of hash maps representing the event data.
     */
    public List<HashMap<String, Object>> getDataFromEvent(DatabaseType databaseType, String eventKey) {
        switch (databaseType) {
            case TEAMS -> {
                MongoDatabase database = mongoClient.getDatabase(getDBName());
                MongoCollection<Document> collection = database.getCollection(databaseType.getCollectionName());
                Bson filter = Filters.eq("events", eventKey);
                return getHashMaps(collection, filter);
            }
            case MATCH, STRATEGY, PITS, TBA_MATCHES -> {
                MongoDatabase database = mongoClient.getDatabase(getDBName());
                MongoCollection<Document> collection = database.getCollection(databaseType.getCollectionName());
                Bson filter = Filters.eq("event_key", eventKey);
                return getHashMaps(collection, filter);
            }
            default -> logger.warn("Database {} is not supported for getting from event.", databaseType);
        }
        return List.of();
    }

    /**
     * Retrieves team-specific data from the database.
     *
     * <p>Supported database types:
     * <ul>
     *   <li><code>TEAMS</code>: Retrieves a team object for the specified team number.</li>
     *   <li><code>MATCH</code>, <code>STRATEGY</code>, <code>PITS</code>, and <code>TBA_MATCHES</code>: Retrieves data objects filtered by team number and event key.</li>
     * </ul>
     * Unsupported database types will log a warning.
     * </p>
     *
     * @param databaseType the type of database.
     * @param teamNum      the team number.
     * @param eventKey     the event key.
     * @return a list of hash maps representing the team data.
     */
    public List<HashMap<String, Object>> getDataFromTeam(DatabaseType databaseType, int teamNum, String eventKey) {
        switch (databaseType) {
            case TEAMS -> {
                MongoDatabase database = mongoClient.getDatabase(getDBName());
                MongoCollection<Document> collection = database.getCollection(databaseType.getCollectionName());
                Bson filter = Filters.eq("key", "frc" + teamNum);
                return getHashMaps(collection, filter);
            }
            case MATCH, PITS -> {
                MongoDatabase database = mongoClient.getDatabase(getDBName());
                MongoCollection<Document> collection = database.getCollection(databaseType.getCollectionName());
                Bson filter = Filters.and(Filters.eq("team", Integer.toString(teamNum)), Filters.eq("event_key", eventKey));
                return getHashMaps(collection, filter);
            }
            case STRATEGY -> {
                MongoDatabase database = mongoClient.getDatabase(getDBName());
                MongoCollection<Document> collection = database.getCollection(databaseType.getCollectionName());
                Bson filter = Filters.and(
                        Filters.eq("event_key", eventKey),
                        Filters.or(
                                Filters.eq("strategy.1", teamNum),
                                Filters.eq("strategy.2", teamNum),
                                Filters.eq("strategy.3", teamNum)
                        )
                );
                return getHashMaps(collection, filter);
            }
            case TBA_MATCHES -> {
                MongoDatabase database = mongoClient.getDatabase(getDBName());
                MongoCollection<Document> collection = database.getCollection(databaseType.getCollectionName());
                Bson filter = Filters.and(Filters.eq("event_key", eventKey), Filters.or(Filters.regex("alliances.red.team_keys", ".*frc" + teamNum + ".*"), Filters.regex("alliances.blue.team_keys", ".*frc" + teamNum + ".*")));
                return getHashMaps(collection, filter);
            }
            default -> logger.warn("Database {} is not supported for getting from team.", databaseType);
        }
        return List.of();
    }

    /**
     * Retrieves an array of key names from a random document in the specified collection.
     *
     * <p>Supported database types:
     * <ul>
     *   <li><code>TEAMS</code>, <code>MATCH</code>, <code>STRATEGY</code>, <code>PITS</code>, and <code>TBA_MATCHES</code></li>
     * </ul>
     * For <code>MATCH</code>, <code>STRATEGY</code>, and <code>PITS</code> types certain keys are skipped.
     * </p>
     *
     * @param databaseType the type of database.
     * @return an array of key names present in a random document.
     */
    public String[] getKeys(DatabaseType databaseType) {
        switch (databaseType) {
            case TEAMS, MATCH, STRATEGY, PITS, TBA_MATCHES -> {
                MongoDatabase database = mongoClient.getDatabase(getDBName());
                MongoCollection<Document> collection = database.getCollection(databaseType.getCollectionName());
                Document random = collection.aggregate(List.of(Aggregates.sample(1))).first();
                if (random == null) {
                    logger.warn("No documents found in match collection for the year {}.", year);
                    return new String[0];
                }
                List<String> keys = new ArrayList<>();
                for (String key : random.keySet()) {
                    if (key.equals("_id")) {
                        continue;
                    }
                    if (databaseType == DatabaseType.MATCH || databaseType == DatabaseType.STRATEGY || databaseType == DatabaseType.PITS) {
                        switch (key) {
                            case "team_key", "event_key", "match_num", "match", "match_key" -> {
                                continue;
                            }
                        }
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
            default -> logger.warn("Database {} is not supported for getting keys.", databaseType);
        }
        return new String[0];
    }


    //.#####...######..#####...#####...######...####....####...######..######..#####...........##...##..######..######..##..##...####...#####....####..
    //.##..##..##......##..##..##..##..##......##..##..##..##....##....##......##..##..........###.###..##........##....##..##..##..##..##..##..##.....
    //.##..##..####....#####...#####...####....##......######....##....####....##..##..........##.#.##..####......##....######..##..##..##..##...####..
    //.##..##..##......##......##..##..##......##..##..##..##....##....##......##..##..........##...##..##........##....##..##..##..##..##..##......##.
    //.#####...######..##......##..##..######...####...##..##....##....######..#####...........##...##..######....##....##..##...####...#####....####..
    // Deprecated Methods Below!!!!

    /**
     * Processes a JSON string representing team details and ensures the team is in the database.
     *
     * @param teamJson a JSON string containing team details. Should be similar to TBA data.
     * @see #processJSON(DatabaseType, String, String...)
     * @deprecated
     */
    @Deprecated
    public void processTeamJson(String teamJson, String... events) {
        processJSON(DatabaseType.TEAMS, teamJson, events);
    }

    /**
     * Gets the teams for a specified event, then adds them into the database.
     *
     * @param eventKey the event key to get teams for. A valid event key looks like this: "2025wasno" (PNW District Glacier Peak Event 2025).
     * @see #pullFromTBA(DatabaseType, String)
     * @deprecated
     */
    @Deprecated
    public void processTeamsForEvent(String eventKey) {
        pullFromTBA(DatabaseType.TEAMS, eventKey);
    }

    /**
     * Gets team keys from an arraylist and adds their details to the database. Useful for off season events or when there is no data from TBA on an event.
     *
     * @param teams an arraylist of teams to add. The arraylist should be team keys. Example: [frc2046, frc1678, frc2910].
     * @see #pullFromTBA(DatabaseType, String)
     * @deprecated
     */
    @Deprecated
    public void processTeamsFromArrayList(ArrayList<String> teams) {
        for (String team : teams) {
            pullFromTBA(DatabaseType.TEAMS, team);
        }
    }

    /**
     * Processes a JSON string representing match details from the main scout (scouting one individual team).
     *
     * @param matchJson a JSON string containing match details. Should follow the yearly schema for main scouting to the letter.
     * @see #processJSON(DatabaseType, String, String...)
     * @deprecated
     */
    @Deprecated
    public void processMainScoutJson(String matchJson) {
        processJSON(DatabaseType.MATCH, matchJson);
    }

    /**
     * Processes a JSON string representing match details from the strategy scout (scouting the whole alliance).
     *
     * @param matchJson a JSON string containing match details. Should follow the yearly schema for strategy scouting to the letter.
     * @see #processJSON(DatabaseType, String, String...)
     * @deprecated
     */
    @Deprecated
    public void processStrategyScoutJson(String matchJson) {
        processJSON(DatabaseType.STRATEGY, matchJson);
    }

    /**
     * Processes a JSON string representing pit scouting details.
     *
     * @param matchJson a JSON string containing pit scouting details. Should follow the yearly schema for pit scouting to the letter.
     * @see #processJSON(DatabaseType, String, String...)
     * @deprecated
     */
    @Deprecated
    public void processPitsJson(String matchJson) {
        processJSON(DatabaseType.PITS, matchJson);
    }

    /**
     * Gets matches from a team for a specific event.
     *
     * @param teamKey  the team key to filter matches.
     * @param eventKey the event key to filter matches.
     * @return a list of matches for the specified team and event.
     * @see #getDataFromTeam(DatabaseType, int, String)
     * @deprecated
     */
    @Deprecated
    public List<HashMap<String, Object>> getMatchesFromTeam(String teamKey, String eventKey) {
        return getDataFromTeam(DatabaseType.MATCH, Integer.parseInt(teamKey.substring(3)), eventKey);
    }

    /**
     * Gets teams from a specific match for a specific event.
     *
     * @param matchNumber the match number to filter teams.
     * @param eventKey    the event key to filter teams.
     * @return a list of teams for the specified match and event.
     * @see #getDataFromMatch(DatabaseType, int, String)
     * @deprecated
     */
    @Deprecated
    public List<HashMap<String, Object>> getTeamsFromMatch(int matchNumber, String eventKey) {
        return getDataFromMatch(DatabaseType.MATCH, matchNumber, eventKey);
    }

    /**
     * Gets teams from a specific event.
     *
     * @param eventKey the event key to filter teams.
     * @return a map of team numbers and their names for the specified event.
     * @see #getDataFromEvent(DatabaseType, String)
     * @deprecated Use {@link #getDataFromEvent(DatabaseType, String)} with extra code instead.
     */
    @Deprecated
    public HashMap<Integer, String> getTeamsFromEvent(String eventKey) {
        MongoDatabase database = mongoClient.getDatabase(getDBName());
        MongoCollection<Document> collection = database.getCollection(DatabaseType.MATCH.getCollectionName());

        Bson filter = Filters.eq("event_key", eventKey);

        List<HashMap<String, Object>> documentsList = getHashMaps(collection, filter);

        HashMap<Integer, String> teams = new HashMap<>();
        Set<String> teamKeys = new HashSet<>();

        for (HashMap<String, Object> document : documentsList) {
            String teamKey = (String) document.get("team");
            if (teamKey != null) {
                teamKeys.add(teamKey);
            }
        }

        MongoCollection<Document> teamCollection = database.getCollection(DatabaseType.TEAMS.getCollectionName());
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
     * @see #getDataFromEvent(DatabaseType, String)
     * @deprecated
     */
    @Deprecated
    public List<HashMap<String, Object>> getMatchesFromEvent(String eventKey) {
        return getDataFromEvent(DatabaseType.MATCH, eventKey);
    }

    /**
     * Gets strategy scout data from a specific event.
     *
     * @param eventKey the event key to filter strategy scout data.
     * @return a list of strategy scout data for the specified event.
     * @see #getDataFromEvent(DatabaseType, String)
     * @deprecated
     */
    @Deprecated
    public List<HashMap<String, Object>> getStratForEvent(String eventKey) {
        return getDataFromEvent(DatabaseType.STRATEGY, eventKey);
    }

    /**
     * Gets pit scout data from a specific event.
     *
     * @param eventKey the event key to filter pit scout data.
     * @return a list of pit scout data for the specified event.
     * @see #getDataFromEvent(DatabaseType, String)
     * @deprecated
     */
    @Deprecated
    public List<HashMap<String, Object>> getPitsForEvent(String eventKey) {
        return getDataFromEvent(DatabaseType.PITS, eventKey);
    }

    /**
     * Gets the keys of a random document in the main scout collection. It also formats subkeys to be more readable.
     *
     * @return an array of keys
     * @see #getKeys(DatabaseType)
     * @deprecated
     */
    @Deprecated
    public String[] getKeysForMainScout() {
        return getKeys(DatabaseType.MATCH);
    }

    //------------------------------End of Deprecated Methods------------------------------//

    /**
     * Converts MongoDB documents to a list of hash maps based on a filter.
     *
     * @param collection the MongoDB collection to query.
     * @param filter     the filter to apply to the query.
     * @return a list of hash maps representing the documents.
     */
    @NotNull
    private List<HashMap<String, Object>> getHashMaps(MongoCollection<Document> collection, Bson filter) {
        List<HashMap<String, Object>> documentsList = new ArrayList<>();
        try (MongoCursor<Document> cursor = collection.find(filter).iterator()) {
            while (cursor.hasNext()) {
                Document document = cursor.next();
                HashMap<String, Object> map = new HashMap<>(document);
                documentsList.add(map);
            }
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