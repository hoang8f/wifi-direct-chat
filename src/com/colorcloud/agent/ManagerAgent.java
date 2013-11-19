package com.colorcloud.agent;

import com.colorcloud.wifichat.WifiDirectUtils;

import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.util.leap.Set;
import jade.util.leap.SortedSetImpl;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class ManagerAgent extends Agent implements ManagerInterface {

    private static final String TAG = "ReceivedMessage";
    private static final long serialVersionUID = 1594371294421614291L;
    private Set participants = new SortedSetImpl();
    private Codec codec = new SLCodec();
    private Context context;
    private String ipAddress = "133.19.63.184";
    private String agentName = "manager";

    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            if (args[0] instanceof Context) {
                context = (Context) args[0];
            }
        }

        // Activate the GUI
        registerO2AInterface(ManagerInterface.class, this);
        addBehaviour(new ParticipantsManager(this));

        Intent broadcast = new Intent();
        broadcast.setAction("jade.demo.agent.SEND_MESSAGE");
        Log.i(TAG, "@@@Sending broadcast " + broadcast.getAction());
        context.sendBroadcast(broadcast);
    }
    
    class ParticipantsManager extends CyclicBehaviour {
		private static final long serialVersionUID = -7712839879652888139L;

		ParticipantsManager(Agent a) {
            super(a);
        }

        public void onStart() {
            //Start cyclic
        }

        public void action() {
            // Listening for incomming
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                try {
                    //Get message
                    msg.getSender().getAddressesArray();
                    
                    WifiDirectUtils.OTHER_DEVICE_ADDRESS = msg.getSender().getLocalName();
                    Log.d(TAG, "@@@ other device address:" + WifiDirectUtils.OTHER_DEVICE_ADDRESS);
        			Toast.makeText(context, "Other device ip:" + WifiDirectUtils.OTHER_DEVICE_ADDRESS, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                block();
            }
        }
    }
    
    class OneShotMessage extends OneShotBehaviour {
		private static final long serialVersionUID = 7197253550536422665L;
		private String mMessage;
        private String mAddress;

        public OneShotMessage(String address) {
            mAddress = address;
        }

        @Override
        public void action() {
            ACLMessage message = new ACLMessage(ACLMessage.INFORM);
            message.setLanguage(codec.getName());
            String convId = "C-" + myAgent.getLocalName();
            message.setConversationId(convId);
            message.setContent(mMessage);
            AID dummyAid = new AID();
            dummyAid.setName(agentName + "@" + mAddress + ":1099/JADE");
            dummyAid.addAddresses("http://" + mAddress + ":7778/acc");
            message.addReceiver(dummyAid);
            myAgent.send(message);
            Log.i(TAG, "@@@Send message:" + message.getContent());
        }
    }

    protected void takeDown() {
    }
    
	@Override
	public void sendMessageToOtherManager(WifiP2pInfo info) {
		Toast.makeText(context, "send message to Other manager", Toast.LENGTH_SHORT).show();
		Log.d(TAG, "@@@send message to Other manager");
		if (!info.isGroupOwner) {
			//Not a group owner
			addBehaviour(new OneShotMessage(info.groupOwnerAddress.toString().replace("/", "")));
			//Update other device address
			WifiDirectUtils.OTHER_DEVICE_ADDRESS = info.groupOwnerAddress.toString().replace("/","");
			Toast.makeText(context, "Other device ip:" + WifiDirectUtils.OTHER_DEVICE_ADDRESS, Toast.LENGTH_LONG).show();
		} else {
			//Group owner
			//Do nothing, just receiver message to update client ip address
			Toast.makeText(context, "is group owner", Toast.LENGTH_SHORT).show();
		}
	}
}
