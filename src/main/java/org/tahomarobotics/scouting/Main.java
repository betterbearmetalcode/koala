package org.tahomarobotics.scouting;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {

        DatabaseManager databaseManager = new DatabaseManager();

        System.out.println(databaseManager.getTeamsFromMatch(2025, 8, "2024orsal"));
    }
}
