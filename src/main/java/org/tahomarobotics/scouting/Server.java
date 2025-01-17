package org.tahomarobotics.scouting;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A server that listens for client connections and registers a service using JmDNS.
 */
public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private ServerSocket serverSocket;
    private JmDNS jmdns;

    /**
     * Constructs a Server instance and registers the service on the specified port.
     *
     * @param port The port on which the server will listen for connections.
     * @throws IOException If an I/O error occurs when opening the socket or registering the service.
     */
    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        jmdns = JmDNS.create();
        ServiceInfo serviceInfo = ServiceInfo.create("_http._tcp.local.", "koala", port, "Service for Koala, the Bear Metal data transfer library");
        jmdns.registerService(serviceInfo);
        logger.info("Service registered at port {}", port);
    }

    /**
     * Starts the server and listens for incoming client connections.
     * This method runs indefinitely until the server is stopped.
     */
    public void start() {
        logger.info("Server started. Waiting for connections...");
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("Client connected: {}", clientSocket.getInetAddress());
                // Start a new thread to handle the client connection
                new Thread(new ClientHandler(clientSocket)).start();
            } catch (IOException e) {
                logger.error("Error accepting client connection", e);
                break;
            }
        }
    }

    /**
     * Stops the server and unregisters the service.
     * This method closes the server socket and unregisters all services registered with JmDNS.
     */
    public void stop() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
                jmdns.unregisterAllServices();
                logger.info("Server stopped and service unregistered.");
            }
        } catch (IOException e) {
            logger.error("Error stopping server", e);
        }
    }

    /**
     * Handles communication with a single client.
     */
    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                // Handle client communication here
                logger.info("Handling client: {}", clientSocket.getInetAddress());
                // Example: Read from and write to the client socket
                // InputStream input = clientSocket.getInputStream();
                // OutputStream output = clientSocket.getOutputStream();
                // ...
            } catch (Exception e) {
                logger.error("Error handling client", e);
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    logger.error("Error closing client socket", e);
                }
            }
        }
    }
}
