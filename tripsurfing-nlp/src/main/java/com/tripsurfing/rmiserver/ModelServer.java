package com.tripsurfing.rmiserver;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface ModelServer extends Remote {
	public Map<String, List<String>> recognizeMentions(String sentence) throws RemoteException;
}
