package org.tahomarobotics.scouting;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {

//        DatabaseManager databaseManager = new DatabaseManager(2025);
//
//        databaseManager.processTeamsForEvent("2025oral");
//
//        Server server = new Server(2046, false);
//
//        server.start();

        Client client = new Client();

        client.connectByIP("0.0.0.0", 2046);

        Thread.sleep(5000);

        client.sendData("""
                {
                    "data": = 3
                }
                """);
    }
}
