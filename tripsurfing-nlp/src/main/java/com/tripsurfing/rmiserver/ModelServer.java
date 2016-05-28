package com.tripsurfing.rmiserver;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ModelServer extends Remote {
	public List<String> recognizeMentions(String sentence) throws RemoteException;
}
