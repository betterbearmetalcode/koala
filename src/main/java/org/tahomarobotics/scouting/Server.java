package org.tahomarobotics.scouting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A server that listens for client connections and registers a service using JmDNS.
 */
public class Server {
    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    private ServerSocket serverSocket;
    private JmDNS jmdns;

    /**
     * Constructs a Server instance and optionally registers the service on the specified port.
     *
     * @param port    The port on which the server will listen for connections.
     * @param useMdns Whether to register the service using mDNS.
     * @throws IOException If an I/O error occurs when opening the socket or registering the service.
     */
    public Server(int port, boolean useMdns) throws IOException {
        serverSocket = new ServerSocket(port);

        if (useMdns) {
            jmdns = JmDNS.create();
            ServiceInfo serviceInfo = ServiceInfo.create("_http._tcp.local.", "koala", port, "Service for Koala, the Bear Metal data transfer library");
            jmdns.registerService(serviceInfo);
            logger.info("Service registered at port {}", port);
        }
    }

    /**
     * Returns the server's IP address and port in the format "IP:Port".
     *
     * @return A string representing the server's IP address and port.
     */
    public String getServerAddress() {
        return getServerIp() + ":" + getServerPort();
    }

    /**
     * Returns the server's IP address.
     *
     * @return The server's IP address as a string.
     */
    public String getServerIp() {
        InetAddress inetAddress = serverSocket.getInetAddress();
        return inetAddress.getHostAddress();
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
        logger.info("Server started at {}. Waiting for connections...", getServerAddress());
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
                if (jmdns != null) {
                    jmdns.unregisterAllServices();
                }
                logger.info("Server stopped and service unregistered.");
            }
        } catch (IOException e) {
            logger.error("Error stopping server", e);
        }
    }

    /**
     * Handles communication with a single client.
     */
    private record ClientHandler(Socket clientSocket) implements Runnable {

        @Override
        public void run() {
            try {
                // Get input stream from socket
                String clientMessage = getString();

                // Print the received message
                System.out.println("Received from client: " + clientMessage);

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

        private String getString() throws IOException {
            InputStream inputStream = clientSocket.getInputStream();
            StringBuilder clientMessageBuilder = new StringBuilder();
            int byteRead;

            // Read byte-by-byte until end of stream
            while ((byteRead = inputStream.read()) != -1) {
                clientMessageBuilder.append((char) byteRead);
            }

            // Convert the received byte sequence to a single string (the full JSON message)
            String clientMessage = clientMessageBuilder.toString();
            return clientMessage;
        }
    }
}
