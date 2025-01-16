package org.tahomarobotics.scouting.util;

import javax.jmdns.*;
import java.io.IOException;
import java.net.InetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MDNSUtil {
    private static final Logger logger = LoggerFactory.getLogger(MDNSUtil.class);

    private static final String TYPE = "_koala._tcp.local.";
    private static final String NAME = "Koala";

    /**
     * Registers a service using mDNS.
     *
     * @param port The port number on which the service should run.
     */
    public static void registerService(int port) {
        try {
            JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
            ServiceInfo serviceInfo = ServiceInfo.create(TYPE, NAME, port, "The Koala Data Transfer Service (Bear Metal Scouting)");
            jmdns.registerService(serviceInfo);
            logger.info("Koala mDNS service registered on port {}", port);
        } catch (IOException e) {
            logger.error("Error registering mDNS service", e);
        }
    }
    /**
     * Discovers a service using mDNS.
     *
     * @return The discovered service info, or null if no service was found.
     */
    public static ServiceInfo discoverService() {
        try {
            JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
            ServiceInfo serviceInfo = jmdns.getServiceInfo(TYPE, NAME, 5000);
            if (serviceInfo != null) {
                logger.info("Koala service discovered");
            } else {
                logger.warn("Koala service not found");
            }
            return serviceInfo;
        } catch (IOException e) {
            logger.error("Error discovering Koala mDNS service", e);
        }
        return null;
    }
}
