package org.tahomarobotics.scouting;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client class for discovering and connecting to services using JmDNS.
 */
public class Client {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private Socket socket;
    private JmDNS jmdns;
    private boolean connected = false;

    public Client() throws IOException {
        jmdns = JmDNS.create(InetAddress.getLocalHost());
    }

    /**
     * Discovers and connects to a service of the specified type.
     *
     * @param serviceType the type of service to discover.
     */
    public void discoverAndConnect(String serviceType) {
        jmdns.addServiceListener(serviceType, new ServiceListener() {
            @Override
            public void serviceAdded(ServiceEvent event) {
                logger.info("Service added: {}", event.getName());
            }

            @Override
            public void serviceRemoved(ServiceEvent event) {
                logger.info("Service removed: {}", event.getName());
            }

            @Override
            public void serviceResolved(ServiceEvent event) {
                ServiceInfo info = event.getInfo();
                logger.info("Service resolved: {}", info.getQualifiedName());
                connect(info.getHostAddresses()[0], info.getPort());
            }
        });
    }

    private void connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            connected = true;
            logger.info("Connected to {}:{}", host, port);
        } catch (IOException e) {
            logger.error("Failed to connect to {}:{}", host, port, e);
        }
    }

    /**
     * Sends data to the connected server.
     *
     * @param data the data to send.
     */
    public void sendData(String data) {
        if (connected) {
            try {
                OutputStream out = socket.getOutputStream();
                out.write(data.getBytes());
                out.flush();
                logger.info("Data sent: {}", data);
            } catch (IOException e) {
                logger.error("Failed to send data", e);
            }
        } else {
            logger.warn("Not connected to a server.");
        }
    }

    /**
     * Disconnects from the server.
     */
    public void disconnect() {
        try {
            if (socket != null) {
                socket.close();
                connected = false;
                logger.info("Disconnected from the server.");
            }
        } catch (IOException e) {
            logger.error("Failed to disconnect", e);
        }
    }
}