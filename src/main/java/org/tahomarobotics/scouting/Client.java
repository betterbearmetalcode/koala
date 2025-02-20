package org.tahomarobotics.scouting;

import javax.imageio.ImageIO;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client class for all interactions with client devices.
 */
public class Client {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private Socket socket;
    private final ArrayList<JmDNS> jmdnses = new ArrayList<>();
    private boolean connected = false;

    /**
     * Returns whether the client has connected to the server
     *
     * @return The connection status of the client
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Constructs a Client instance and initializes the JmDNS instances.
     *
     * @throws IOException If an I/O error occurs when creating the JmDNS instances.
     */
    public Client() throws IOException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (!address.isLoopbackAddress() && address.isSiteLocalAddress()) {
                    logger.info("Local Network IP: {}", address.getHostAddress());
                    JmDNS jmdns = JmDNS.create(address);
                    jmdnses.add(jmdns);
                }
            }
        }
    }

    /**
     * Discovers and connects to the koala service.
     */
    public void discoverAndConnect() {
        for (JmDNS jmdns: jmdnses) {
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
                        String ipAddress = info.getHostAddresses()[0];
                        logger.info("Service resolved: {}, {}:{}", info.getQualifiedName(), ipAddress, info.getPort());
                        try {
                            connectByIP(InetAddress.getByName(ipAddress), info.getPort());
                        } catch (UnknownHostException e) {
                            logger.error("Failed to connect to {}:{}", ipAddress, info.getPort(), e);
                        }
                    } else {
                        logger.warn("Service resolved, but no IP address or port: {}", info.getQualifiedName());
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
    public void connectByIP(InetAddress ip, int port) {
        try {
            socket = new Socket(ip, port);
            connected = true;
            logger.info("Connected to {}:{}", ip, port);
        } catch (Exception e) {
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
     * An overload of sendData to send images
     *
     * @param file the file to send
     */
    public void sendData(File file) {
        if (connected) {
            try {
                OutputStream out = socket.getOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(out);
                FileReader reader = new FileReader(file);
                int line;
                writer.write("fn:" + file.getName() + "\n");
                while ((line = reader.read()) != -1) {
                    writer.write(line);
                }
                writer.write('\u0003');
                out.flush();
                reader.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
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
                // Check if data is a valid JSON string
                JsonParser.parseString(data);
    
                OutputStream out = socket.getOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(out);
                StringBuilder pendingData = new StringBuilder("{ \"header\": {\n");
                List<String> headersAsList = new ArrayList<>(Arrays.asList(headers));

                for (String header : headers) {
                    // Format : "h[indexOfHeader] : "header" \n
                    pendingData.append("\"h").append(headersAsList.indexOf(header)).append("\" : \"").append(header).append("\",\n");
                }
                pendingData.deleteCharAt(pendingData.length() - 2);
                pendingData.append("},\n \"data\" : ").append(data).append("}");
    
                writer.write(pendingData.toString() + '\u0003');
                writer.flush();
                logger.info("Data sent: {}", pendingData);
            } catch (com.google.gson.JsonParseException e) {
                logger.error("Invalid JSON data: {}", data, e);
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