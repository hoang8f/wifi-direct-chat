package com.colorcloud.agent;

import android.net.wifi.p2p.WifiP2pInfo;

/**
 * This interface implements the logic of the chat client running on the user
 * terminal.
 * 
 * @author Michele Izzo - Telecomitalia
 */

public interface ManagerInterface {
	public void sendMessageToOtherManager(WifiP2pInfo info);
}