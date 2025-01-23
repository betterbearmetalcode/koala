package org.tahomarobotics.scouting;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client class for all interactions with client devices.
 */
public class Client {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private Socket socket;
    private final ArrayList<JmDNS> jmdnss = new ArrayList<>();
    private boolean connected = false;

    public Client() throws IOException {
        InetAddress[] addresses = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
        for (int i = 0; i < addresses.length/2; i++) {
            jmdnss.add(JmDNS.create(InetAddress.getByName(addresses[i].getHostAddress())));
        }
    }

    /**
     * Discovers and connects to the koala service.
     */
    public void discoverAndConnect() {
        for (JmDNS jmdns: jmdnss) {
            jmdns.addServiceListener("_http._tcp.local.", new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    String serviceName = event.getName();
                    if (serviceName.equals("koala")) {
                        jmdns.requestServiceInfo("_http._tcp.local.", serviceName);
                        logger.info("Koala service added: {}", serviceName);
                    } else {
                        logger.info("Service added (but not koala): {}", serviceName);
                    }
                }

                @Override
                public void serviceRemoved(ServiceEvent event) {
                    logger.info("Service removed: {}", event.getName());
                }

                @Override
                public void serviceResolved(ServiceEvent event) {
                    ServiceInfo info = event.getInfo();
                    if (info.getHostAddresses().length > 0 && info.getPort() > 0) {
                        logger.info("Service resolved: {}", info.getQualifiedName());
                        connectByIP(info.getHostAddresses()[0], info.getPort());
                    } else {
                        logger.warn("Service resolution incomplete: {}", info.getQualifiedName());
                    }
                }
            });
        }
    }

    /**
     * Connects to a server via IP address.
     *
     * @param ip the ip of the server
     * @param port the port the server is listening on
     */
    public void connectByIP(String ip, int port) {
        try {
            socket = new Socket(ip, port);
            connected = true;
            logger.info("Connected to {}:{}", ip, port);
        } catch (IOException e) {
            logger.error("Failed to connect to {}:{}", ip, port, e);
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
                OutputStreamWriter writer = new OutputStreamWriter(out);
                writer.write(data + '\u0003');
                writer.flush();
                logger.info("Data sent: {}", data);
            } catch (IOException e) {
                logger.error("Failed to send data", e);
            }
        } else {
            logger.warn("Not connected to a server.");
        }
    }

    /**
     * Sends data to the connected server.
     *
     * @param data the data to send.
     *
     * @param headers headers to be put at the top of the data packet
     */
    public void sendData(String data, String... headers) {
        if (connected) {
            try {
                OutputStream out = socket.getOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(out);
                StringBuilder pendingData = new StringBuilder("{ \"header\": {\n");
                List<String> headersAsList = Arrays.stream(headers).toList();
                for (String header : headers) {
                    //Format : "h[indexOfHeader] : "header" \n
                    pendingData.append("\"h").append(headersAsList.indexOf(header)).append("\" : \"").append(header).append("\",\n");
                }
                pendingData.deleteCharAt(pendingData.length()-2);
                pendingData.append("},\n \"data\" : ").append(data).append("}");

                writer.write(pendingData.toString() + '\u0003');
                writer.flush();
                logger.info("Data sent: {}", pendingData.toString());
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