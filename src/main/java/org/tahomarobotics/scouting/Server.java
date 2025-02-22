package org.tahomarobotics.scouting;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;

/**
 * A server that listens for client connections and registers a service using JmDNS.
 */
public class Server {
    private final List<ServerListener> listeners = new ArrayList<>();
    public interface ServerListener extends EventListener {
        void receivedData(String data);
    }
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private static final Gson gson = new Gson();
    private final ServerSocket serverSocket;
    private JmDNS jmdns;
    
    private boolean running = false;

    private final int year;

    /**
     * Constructs a Server instance and optionally registers the service on the specified port.
     *
     * @param port      The port on which the server will listen for connections.
     * @param useMdns   Whether to register the service using mDNS.
     * @param year      The year that the season is currently.
     * @param ipAddress The IP address to bind the service to.
     * @throws IOException If an I/O error occurs when opening the socket or registering the service.
     */
    public Server(int port, boolean useMdns, int year, InetAddress ipAddress) throws IOException {
        serverSocket = new ServerSocket(port);

        this.year = year;

        if (useMdns) {
            ServiceInfo serviceInfo = ServiceInfo.create("_http._tcp.local.", "koala", port, "Service for Koala, the Bear Metal data transfer library");
            jmdns = JmDNS.create(ipAddress);
            jmdns.registerService(serviceInfo);
            logger.info("Service registered on address {} at port {}", ipAddress.getHostAddress(), port);
        }
    }

    /**
     * Adds a listener to the server that is called when data is received
     *
     * @param listener The listener to be added
     */
    public void addListener(ServerListener listener) {
        listeners.add(listener);
    }

    /**
     * Returns the server's port.
     *
     * @return The server's port as an integer.
     */
    public int getServerPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Starts the server and listens for incoming client connections.
     * This method runs indefinitely until the server is stopped.
     */
    public void start() {
        logger.info("Server started. Waiting for connections...");
        running = true;

        try {
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                logger.info("Client connected: {}", clientSocket.getInetAddress());
                // Start a new thread to handle the client connection
                new Thread(new ClientHandler(clientSocket, year)).start();
            }
        } catch (IOException e) {
            logger.error("Error accepting client connection", e);
        } finally {
            stop();
        }
    }

    /**
     * Stops the server and unregisters the service.
     * This method closes the server socket and unregisters all services registered with JmDNS.
     */
    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (jmdns != null) {
                jmdns.unregisterAllServices();
                jmdns.close();
            }
            running = false;
            logger.info("Server stopped and service unregistered.");
        } catch (IOException e) {
            logger.error("Error stopping server", e);
        }
    }
    
    /**
     * Returns whether the server is running.
     *
     * @return Whether the server is running.
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Returns the InetAddress of the server.
     *
     * @return The InetAddress of the server.
     */
    public InetAddress getInetAddress() {
        return serverSocket.getInetAddress();
    }

    /**
     * Handles communication with a single client.
     */
    private class ClientHandler implements Runnable {
        Socket clientSocket;
        int year;
        ClientHandler(Socket clientSocket, int year) {
            this.clientSocket = clientSocket;
            this.year = year;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    // Get input stream from socket
                    String clientMessage = getString();

                    // Print the received message
                    logger.info("Received from client: {}", clientMessage);

                    for (ServerListener listener: listeners)
                        listener.receivedData(clientMessage);

                    if (clientMessage.startsWith("fn:")) {
                        handleFile(clientMessage);
                    } else {
                        handleData(clientMessage);
                    }
                }
            } catch (Exception e) {
                logger.error("Error handling client", e);
            }
        }

        private void handleFile(String clientMessage) {
            int i = 0;
            StringBuilder fileName = new StringBuilder();
            boolean nameStarted = false;
            for (char s : clientMessage.toCharArray()) {
                if (s == ':')
                    nameStarted = true;
                if (s == '\n')
                    break;
                if (nameStarted)
                    fileName.append(s);
                i++;
            }
            String imageData = clientMessage.substring(i+1);
            File file = new File(fileName.toString());
            BufferedImage image;
            try {
                FileWriter writer = new FileWriter(file);
                writer.write(imageData);
                image = ImageIO.read(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void handleData(String clientMessage) {
            JsonObject jsonObject = gson.fromJson(clientMessage, JsonObject.class);

            String header = "";
            try {
                JsonElement headerElement = jsonObject.get("header");

                if (headerElement == null) {
                    logger.error("No header in json data. Skipping...");
                    return;
                }

                header = headerElement.getAsJsonObject().get("h0").getAsString();
            } catch (NullPointerException e) {
                logger.warn("No headers received");
            }


            DatabaseManager databaseManager = new DatabaseManager(year);

            String data = jsonObject.get("data").toString();



            switch (header) {
                case "match":
                    databaseManager.processMainScoutJson(data);
                    break;
                case "strat":
                    databaseManager.processStrategyScoutJson(data);
                    break;
                case "pit":
                    break;
                default:
                    logger.warn("\"{}\" is not a valid header!", header);
            }
        }

        private String getString() throws IOException {
            InputStream inputStream = clientSocket.getInputStream();
            StringBuilder clientMessageBuilder = new StringBuilder();
            int byteRead;

            while ((byteRead = inputStream.read()) != '\u0003' && byteRead != -1) {
                clientMessageBuilder.append((char) byteRead);
            }

            return clientMessageBuilder.toString();
        }
    }
}
