package com.tripsurfing.rmiserver;

import java.io.FileInputStream;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelServerRunner {

    private static final Logger logger = LoggerFactory.getLogger(ModelServerRunner.class);

    public static void main(String args[]) throws Exception {
        String host = "localhost";
        ModelServer server;
        server = new ModelServerImpl(args[0]);
        Properties properties = new Properties();
        properties.load(new FileInputStream(args[0]));
        ModelServer stub = (ModelServer) UnicastRemoteObject.exportObject(server, 0);
        // bind the remote object's stub in the registry
        int port = Integer.parseInt(properties.getProperty("RMI_PORT"));
        LocateRegistry.createRegistry(port);
        LocateRegistry.getRegistry(port).bind("TkServer_" + host, stub);
        logger.info("TKServer is listening at: {}", host);
    }
}
