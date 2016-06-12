package com.tripsurfing.rmiserver;

import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelServerRunner {

    private static final Logger logger = LoggerFactory.getLogger(ModelServerRunner.class);

    public static void main(String args[]) throws Exception {
        String host = "localhost";
        ModelServer server;
        if (args.length > 0)
            server = new ModelServerImpl(args[0]);
        else
            server = new ModelServerImpl();

        ModelServer stub = (ModelServer) UnicastRemoteObject.exportObject(server, 0);
        // bind the remote object's stub in the registry
        LocateRegistry.createRegistry(52478);
        LocateRegistry.getRegistry(52478).bind("TkServer_" + host, stub);
        logger.info("TKServer is listening at: {}", host);
    }
}
