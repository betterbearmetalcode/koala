package org.tahomarobotics.scouting;

public enum DatabaseType {
    MATCH("match"),
    STRATEGY("strat"),
    PITS("pits"),
    TEAMS("teams"),
    TBA_MATCHES("tbaMatches");
    
    private final String collectionName;
    
    DatabaseType(String collectionName) {
        this.collectionName = collectionName;
    }
    
    public String getCollectionName() {
        return collectionName;
    }
}
