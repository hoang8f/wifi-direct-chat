/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.colorcloud.wifichat;

import static com.colorcloud.wifichat.Constants.*;

import jade.android.AgentContainerHandler;
import jade.android.AgentHandler;
import jade.android.RuntimeCallback;
import jade.android.RuntimeService;
import jade.android.RuntimeServiceBinder;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.colorcloud.agent.MainContainerInterface;
import com.colorcloud.agent.ManagerAgent;
import com.colorcloud.agent.ManagerInterface;
import com.colorcloud.wifichat.DeviceListFragment.DeviceActionListener;
import com.colorcloud.wifichat.WiFiDirectApp.PTPLog;

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
public class WiFiDirectActivity extends Activity implements DeviceActionListener, MainContainerInterface {

    public static final String TAG = "PTP_Activity";

    WiFiDirectApp mApp = null;

    boolean mHasFocus = false;
    private boolean retryChannel = false;
    private boolean shouldSendMessageToManager = false;

    //jade agent
    private RuntimeServiceBinder runtimeServiceBinder;
    private AgentContainerHandler mainContainerHandler;
    private ServiceConnection serviceConnection;
    private AgentController mManagerAgentController;
    private WifiP2pInfo mInfo;
    private TextView mainContainerStatus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);   // statically draw two <fragment class=>

        mApp = (WiFiDirectApp) getApplication();

        mApp.mHomeActivity = this;

        // If service not started yet, start it.
        Intent serviceIntent = new Intent(this, ConnectionService.class);
        startService(serviceIntent);  // start the connection service

        PTPLog.d(TAG, "onCreate : home activity launched, start service anyway.");
        mainContainerStatus = (TextView) findViewById(R.id.main_containter_status);
    }

    /**
     *
     */
    @Override
    public void onResume() {
        super.onResume();
        mHasFocus = true;
        if (mApp.mThisDevice != null) {
            PTPLog.d(TAG, "onResume : redraw this device details");
            updateThisDevice(mApp.mThisDevice);

            // if p2p connetion info available, and my status is connected, enabled start chatting !
            if (mApp.mP2pInfo != null && mApp.mThisDevice.status == WifiP2pDevice.CONNECTED) {
                PTPLog.d(TAG, "onResume : redraw detail fragment");
                onConnectionInfoAvailable(mApp.mP2pInfo);
            } else {
                // XXX stop client, if any.
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {  // the activity is no long visible
        super.onStop();
        mHasFocus = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mApp.mHomeActivity = null;
        PTPLog.d(TAG, "onDestroy: reset app home activity.");
    }

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
                DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
                if (fragmentList != null) {
                    fragmentList.clearPeers();
                }
                if (fragmentDetails != null) {
                    fragmentDetails.resetViews();
                }
            }
        });
    }

    /**
     * process WIFI_P2P_THIS_DEVICE_CHANGED_ACTION intent, refresh this device.
     */
    public void updateThisDevice(final WifiP2pDevice device) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
                fragment.updateThisDevice(device);
            }
        });
    }

    /**
     * update the device list fragment.
     */
    public void onPeersAvailable(final WifiP2pDeviceList peerList) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
                fragmentList.onPeersAvailable(mApp.mPeers);  // use application cached list.
                DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);

                for (WifiP2pDevice d : peerList.getDeviceList()) {
                    if (d.status == WifiP2pDevice.FAILED) {
                        PTPLog.d(TAG, "onPeersAvailable: Peer status is failed " + d.deviceName);
                        fragmentDetails.resetViews();
                    }
                }
            }
        });
    }

    /**
     * handle p2p connection available, update UI.
     */
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
                fragmentDetails.onConnectionInfoAvailable(info);
                mInfo = info;
                //Start main container here
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        bindService();
                    }
                });

            }
        });
    }

    /**
     * Create JADE Main Container here
     */
    public void bindService() {
        //Check runtime service
        if (runtimeServiceBinder == null) {
            //Create Runtime Service Binder here
            serviceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder service) {
                    runtimeServiceBinder = (RuntimeServiceBinder) service;
                    Log.i(TAG, "@@@Gateway successfully bound to RuntimeService");
                    startMainContainer();
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    Log.i(TAG, "@@@Gateway unbound from RuntimeService");
                }
            };
            Log.i(TAG, "@@@Binding Gateway to RuntimeService...");
            bindService(new Intent(getApplicationContext(), RuntimeService.class), serviceConnection, Context.BIND_AUTO_CREATE);
        } else {
            startMainContainer();
        }
    }

    private void startMainContainer() {
        if (mainContainerHandler == null) {
            runtimeServiceBinder.createMainAgentContainer(new RuntimeCallback<AgentContainerHandler>() {
                @Override
                public void onSuccess(final AgentContainerHandler agentContainerHandler) {
                    mainContainerHandler = agentContainerHandler;
                    Log.i(TAG, "@@@Main-Container created...");
                    Log.i(TAG, "@@@Container:" + agentContainerHandler.getAgentContainer().getName());
                    Log.i(TAG, "@@@mainContainerHandler:" + mainContainerHandler);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mainContainerStatus.setText(agentContainerHandler.getAgentContainer().getName());
                        }
                    });

                    //Create Manager agent if it is not existed
                    createAgent("manager", ManagerAgent.class.getName());
                }

                @Override
                public void onFailure(Throwable throwable) {
                    Log.i(TAG, "@@@Failed to create Main Container");
                }
            });
        } else {
            Toast.makeText(this, "main-container already existed", Toast.LENGTH_SHORT).show();
        }
    }


    private void createAgent(String name, String className) {
        if (mainContainerHandler != null) {
            mainContainerHandler.createNewAgent(name, className, new Object[]{WiFiDirectActivity.this}, new RuntimeCallback<AgentHandler>() {
                @Override
                public void onSuccess(AgentHandler agentHandler) {
                    try {
                        Log.i(TAG, "@@@Success to create agent: " + agentHandler.getAgentController().getName());
                        mManagerAgentController = agentHandler.getAgentController();
                        mManagerAgentController.start();
                        shouldSendMessageToManager = true;

                        Log.i(TAG, "@@@end ");
                    } catch (StaleProxyException e) {
                        e.printStackTrace();
                        Log.i(TAG, "exception:" + ((e != null) ? e.getMessage() : ""));
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    Log.i(TAG, "@@@Failed to created an Agent");
                    shouldSendMessageToManager = false;
                    throwable.printStackTrace();
                }
            });

        } else {
            Toast.makeText(this, "manager agent already existed", Toast.LENGTH_SHORT).show();
        }
        //Send message to manager
        sendMessageToManager();
        shouldSendMessageToManager = false;
        Log.d(TAG, "end of create agent");
    }

    private void sendMessageToManager() {
        if (mInfo != null && mManagerAgentController != null) {
            try {
                ManagerInterface managerInterface = mManagerAgentController.getO2AInterface(ManagerInterface.class);
                if (managerInterface != null) {
                    Log.i(TAG, "@@@!null");
                    managerInterface.sendMessageToOtherManager(mInfo);
                } else {
                    Log.i(TAG, "@@@null");
                    Toast.makeText(this, "ManagerInterface == null", Toast.LENGTH_SHORT).show();
                }
            } catch (StaleProxyException e) {
                e.printStackTrace();
                Toast.makeText(this, "Exception: Can not send message to other manager", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Can not send message to manager", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_items, menu);
        return true;
    }

    /**
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:   // using app icon for navigation up or home:
                Log.d(TAG, " navigating up or home clicked.");
                // startActivity(new Intent(home.class, Intent.FLAG_ACTIVITY_CLEAR_TOP));
                return true;

            case R.id.atn_direct_enable:
                if (!mApp.isP2pEnabled()) {
                    // Since this is the system wireless settings activity, it's
                    // not going to send us a result. We will be notified by
                    // WiFiDeviceBroadcastReceiver instead.
                    AnalyticsUtils.getInstance(mApp).trackEvent(CAT_LOCATION, ACT_CREATE, LAB_HOME, 1);
                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                } else {
                    Log.e(TAG, " WiFi direct support : already enabled. ");
                }
                return true;
            case R.id.atn_direct_discover:
//                if( !mApp.isP2pEnabled() ){
//                    Toast.makeText(WiFiDirectActivity.this, R.string.p2p_off_warning, Toast.LENGTH_LONG).show();
//                    return true;
//                }

                // show progressbar when discoverying.
                final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
                fragment.onInitiateDiscovery();

                PTPLog.d(TAG, "onOptionsItemSelected : start discoverying ");
                AnalyticsUtils.getInstance(mApp).trackEvent(CAT_LOCATION, ACT_CREATE, LAB_HOME, 2);
                mApp.mP2pMan.discoverPeers(mApp.mP2pChannel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(WiFiDirectActivity.this, "Discovery Initiated", Toast.LENGTH_SHORT).show();
                        PTPLog.d(TAG, "onOptionsItemSelected : discovery succeed... ");
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        PTPLog.d(TAG, "onOptionsItemSelected : discovery failed !!! " + reasonCode);
                        fragment.clearPeers();
                        Toast.makeText(WiFiDirectActivity.this, "Discovery Failed, try again... ", Toast.LENGTH_SHORT).show();
                    }
                });
                return true;

            case R.id.disconnect:
                PTPLog.d(TAG, "onOptionsItemSelected : disconnect all connections and stop server ");
                ConnectionService.getInstance().mConnMan.closeClient();
                ConnectionService.getInstance().mConnMan.closeServer();
                return true;

            case R.id.about:
                PTPLog.d(TAG, "onOptionsItemSelected : about ");
                Toast.makeText(this, "Free China using Peer-Peer", Toast.LENGTH_LONG).show();
                return true;

            case R.id.help:
                PTPLog.d(TAG, "onOptionsItemSelected : help ");
                Toast.makeText(this, "learn to use Peer-Peer to fight against censorship", Toast.LENGTH_LONG).show();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * user taps on peer from discovered list of peers, show this peer's detail.
     */
    public void showDetails(WifiP2pDevice device) {
        DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
        fragment.showDetails(device);
    }

    /**
     * user clicked connect button after discover peers.
     */
    public void connect(WifiP2pConfig config) {
        PTPLog.d(TAG, "connect : connect to server : " + config.deviceAddress);
        // perform p2p connect upon users click the connect button. after connection, manager request connection info.
        mApp.mP2pMan.connect(mApp.mP2pChannel, config, new ActionListener() {
            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
                Toast.makeText(WiFiDirectActivity.this, "Connect success..", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(WiFiDirectActivity.this, "Connect failed. Retry.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * user clicked disconnect button, disconnect from group owner.
     */
    public void disconnect() {
        final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
        fragment.resetViews();
        PTPLog.d(TAG, "disconnect : removeGroup ");
        mApp.mP2pMan.removeGroup(mApp.mP2pChannel, new ActionListener() {
            @Override
            public void onFailure(int reasonCode) {
                PTPLog.d(TAG, "Disconnect failed. Reason : 1=error, 2=busy; " + reasonCode);
                Toast.makeText(WiFiDirectActivity.this, "disconnect failed.." + reasonCode, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess() {
                PTPLog.d(TAG, "Disconnect succeed. ");
                fragment.getView().setVisibility(View.GONE);
            }
        });
        //Stop main container
        onStopMainContainer();
    }

    /**
     * The channel to the framework(WiFi direct) has been disconnected.
     * This is diff than the p2p connection to group owner.
     */
    public void onChannelDisconnected() {
        Toast.makeText(this, "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.", Toast.LENGTH_LONG).show();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("WiFi Direct down, please re-enable WiFi Direct")
                .setCancelable(true)
                .setPositiveButton("Re-enable WiFi Direct", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                    }
                })
                .setNegativeButton("Cancle", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });

        AlertDialog info = builder.create();
        info.show();
    }

    @Override
    public void cancelDisconnect() {
        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (mApp.mP2pMan != null) {
            final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
            if (fragment.getDevice() == null || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE || fragment.getDevice().status == WifiP2pDevice.INVITED) {
                mApp.mP2pMan.cancelConnect(mApp.mP2pChannel, new ActionListener() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(WiFiDirectActivity.this, "Aborting connection", Toast.LENGTH_SHORT).show();
                        PTPLog.d(TAG, "cancelConnect : success canceled...");
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(WiFiDirectActivity.this, "cancelConnect: request failed. Please try again.. ", Toast.LENGTH_SHORT).show();
                        PTPLog.d(TAG, "cancelConnect : cancel connect request failed..." + reasonCode);
                    }
                });
            }
        }
    }

    /**
     * launch chat activity
     */
    public void startChatActivity(final String initMsg) {
        if (!mApp.mP2pConnected) {
            Log.d(TAG, "startChatActivity : p2p connection is missing, do nothng...");
            return;
        }

        PTPLog.d(TAG, "startChatActivity : start chat activity fragment..." + initMsg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent i = mApp.getLauchActivityIntent(MainActivity.class, initMsg);
                sendMessageToManager();
                startActivity(i);
            }
        });
    }

    @Override
    public void onStopMainContainer() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mainContainerHandler != null) {
                    mainContainerHandler.kill(new RuntimeCallback<Void>() {

                        @Override
                        public void onFailure(Throwable arg0) {

                        }

                        @Override
                        public void onSuccess(Void arg0) {
                            mainContainerStatus.setText("N/A");
                        }
                    });
                } else {
                    mainContainerStatus.setText("N/A");
                }
            }
        });
    }

    @Override
    public void onStartMainContainer() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                bindService();
            }
        });

    }
}