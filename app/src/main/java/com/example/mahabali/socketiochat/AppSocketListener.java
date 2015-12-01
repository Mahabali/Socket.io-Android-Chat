package com.example.mahabali.socketiochat;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;


import com.example.mahabali.socketioservice.SocketEventConstants;
import com.example.mahabali.socketioservice.SocketIOService;
import com.example.mahabali.socketioservice.SocketListener;

import org.json.JSONException;
import org.json.JSONObject;

import io.socket.client.Ack;
import io.socket.emitter.Emitter;

/**
 * Created by Mahabali on 11/14/15.
 */
public class AppSocketListener implements SocketListener{
    private static AppSocketListener sharedInstance;
    private SocketIOService socketServiceInterface;
    public SocketListener activeSocketListener;

    public void setActiveSocketListener(SocketListener activeSocketListener) {
        this.activeSocketListener = activeSocketListener;
        if (socketServiceInterface != null && socketServiceInterface.isSocketConnected()){
            onSocketConnected();
        }
    }

    public static AppSocketListener getInstance(){
        if (sharedInstance==null){
            sharedInstance = new AppSocketListener();
        }
        return sharedInstance;
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            socketServiceInterface = ((SocketIOService.LocalBinder)service).getService();
            socketServiceInterface.setServiceBinded(true);
            socketServiceInterface.setSocketListener(sharedInstance);
            if (socketServiceInterface.isSocketConnected()){
                onSocketConnected();
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

            socketServiceInterface.setServiceBinded(false);
            socketServiceInterface=null;
            onSocketDisconnected();
        }
    };


    public void initialize(){
        Intent intent = new Intent(AppContext.getAppContext(), SocketIOService.class);
        AppContext.getAppContext().startService(intent);
        AppContext.getAppContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(AppContext.getAppContext()).
                registerReceiver(socketConnectionReceiver, new IntentFilter(SocketEventConstants.
                        socketConnection));
        LocalBroadcastManager.getInstance(AppContext.getAppContext()).
                registerReceiver(connectionFailureReceiver, new IntentFilter(SocketEventConstants.
                        connectionFailure));
        LocalBroadcastManager.getInstance(AppContext.getAppContext()).
                registerReceiver(newMessageReceiver, new IntentFilter(SocketEventConstants.
                        newMessage));
    }

    private BroadcastReceiver socketConnectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
           boolean connected = intent.getBooleanExtra("connectionStatus",false);
            if (connected){
                Log.i("AppSocketListener","Socket connected");
                onSocketConnected();
            }
            else{
                onSocketDisconnected();
            }
        }
    };

    private BroadcastReceiver connectionFailureReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast toast = Toast.
                    makeText(AppContext.getAppContext(), "Please check your network connection",
                            Toast.LENGTH_SHORT);
            toast.show();
        }
    };

    private BroadcastReceiver newMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String userName = intent.getStringExtra("username");
            String message = intent.getStringExtra("message");
            onNewMessageReceived(userName,message);
        }
    };

    public void destroy(){
        socketServiceInterface.setServiceBinded(false);
        AppContext.getAppContext().unbindService(serviceConnection);
        LocalBroadcastManager.getInstance(AppContext.getAppContext()).
                unregisterReceiver(socketConnectionReceiver);
        LocalBroadcastManager.getInstance(AppContext.getAppContext()).
                unregisterReceiver(newMessageReceiver);
    }

    @Override
    public void onSocketConnected() {
        if (activeSocketListener != null) {
            activeSocketListener.onSocketConnected();
        }
    }

    @Override
    public void onSocketDisconnected() {
        if (activeSocketListener != null) {
            activeSocketListener.onSocketDisconnected();
        }
    }

    @Override
    public void onNewMessageReceived(String username, String message) {
        if (activeSocketListener != null) {
            activeSocketListener.onNewMessageReceived(username, message);
        }
    }

    public void addOnHandler(String event,Emitter.Listener listener){
        socketServiceInterface.addOnHandler(event, listener);
    }
    public void emit(String event,Object[] args,Ack ack){
        socketServiceInterface.emit(event, args, ack);
    }

    public void emit (String event,Object... args){
        socketServiceInterface.emit(event, args);
    }

     void connect(){
        socketServiceInterface.connect();
    }

    public void disconnect(){
        socketServiceInterface.disconnect();
    }
    public void off(String event) {
        if (socketServiceInterface != null) {
            socketServiceInterface.off(event);
        }
    }

    public boolean isSocketConnected(){
        if (socketServiceInterface == null){
            return false;
        }
        return socketServiceInterface.isSocketConnected();
    }

    public void setAppConnectedToService(Boolean status){
        if ( socketServiceInterface != null){
            socketServiceInterface.setAppConnectedToService(status);
        }
    }

    public void restartSocket(){
        if (socketServiceInterface != null){
         socketServiceInterface.restartSocket();
        }
    }
    public void addNewMessageHandler(){
        if (socketServiceInterface != null){
            socketServiceInterface.addNewMessageHandler();
        }
    }

    public void removeNewMessageHandler(){
        if (socketServiceInterface != null){
            socketServiceInterface.removeMessageHandler();
        }
    }

    public void signOutUser(){
        AppSocketListener.getInstance().disconnect();
        removeNewMessageHandler();
        AppSocketListener.getInstance().connect();
    }
}
