package com.example.mahabali.socketioservice;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.example.mahabali.Constants;
import com.example.mahabali.socketiochat.Dummy;
import com.example.mahabali.socketiochat.MainActivity;
import com.example.mahabali.socketiochat.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class SocketIOService extends Service {



    private  SocketListener socketListener;
    private Boolean appConnectedToService;

    public void setAppConnectedToService(Boolean appConnectedToService) {
        Log.i("AppStatus","Status "+appConnectedToService);
        this.appConnectedToService = appConnectedToService;
    }

    public void setSocketListener(SocketListener socketListener) {
        this.socketListener = socketListener;
    }
    private  Socket mSocket;
    private boolean serviceBinded = false;
    private final LocalBinder mBinder = new LocalBinder();


    public class LocalBinder extends Binder{
       public SocketIOService getService(){
            return SocketIOService.this;
        }
    }

    public SocketIOService() {
    }

    public void setServiceBinded(boolean serviceBinded) {

        this.serviceBinded = serviceBinded;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
       return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializeSocket();
        addSocketHandlers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeSocketSession();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return serviceBinded;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void initializeSocket() {
        try{
            IO.Options options = new IO.Options();
            options.forceNew = true;
            mSocket = IO.socket(Constants.CHAT_SERVER_URL,options);
        }
        catch (Exception e){
            Log.e("Error", "Exception in socket creation");
            throw new RuntimeException(e);
        }
    }

    private void closeSocketSession(){
        mSocket.disconnect();
        Log.i("AppService","Closing socket session");
        mSocket.off();
    }
    private void addSocketHandlers(){

        mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i("Info","Socket connected");
                Intent intent = new Intent(SocketEventConstants.socketConnection);
                intent.putExtra("connectionStatus",true);
                broadcastEvent(intent);

            }
        });

        mSocket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Log.i("Info", "Socket disconnected");
                Intent intent = new Intent(SocketEventConstants.socketConnection);
                intent.putExtra("connectionStatus",false);
                broadcastEvent(intent);
            }
        });


        mSocket.on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
        @Override
        public void call (Object...args){
            Intent intent = new Intent(SocketEventConstants.connectionFailure);
            broadcastEvent(intent);
            Log.i("Failed", "Failed to connect");
        }
        });

        mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, new Emitter.Listener() {
            @Override
            public void call (Object...args){
                Intent intent = new Intent(SocketEventConstants.connectionFailure);
                broadcastEvent(intent);
                Log.i("Failed", "Failed to connect");
            }
        });

       addNewMessageHandler();



        
        mSocket.connect();
    }

    public void addNewMessageHandler(){
        mSocket.on(SocketEventConstants.newMessage, new Emitter.Listener() {
            @Override
            public void call(final Object... args) {

                JSONObject data = (JSONObject) args[0];
                String username;
                String message;
                try {
                    username = data.getString("username");
                    message = data.getString("message");
                    Log.i("Message", " " + username + " " + message);
                } catch (JSONException e) {
                    return;
                }


                if (isForeground("com.example.mahabali.socketiochat")) {
                    Intent intent = new Intent(SocketEventConstants.newMessage);
                    intent.putExtra("username", username);
                    intent.putExtra("message", message);
                    broadcastEvent(intent);
                } else {
                    showNotificaitons(username,message);
                }
            }
        });
    }
    public void emit(String event,Object[] args,Ack ack){
        mSocket.emit(event, args, ack);
    }
    public void emit (String event,Object... args) {
        mSocket.emit(event,args);
    }

    public void addOnHandler(String event,Emitter.Listener listener){
//        if (event.equals(SocketEventConstants.newMessage)) {
//            mSocket.off(SocketEventConstants.newMessage);
//        }
            mSocket.on(event, listener);
    }

    public void connect(){
        mSocket.connect();
    }

    public void disconnect(){
        mSocket.disconnect();
    }

    public void off(String event){
        Log.i("Service","offing "+event);
        mSocket.off(event);
//        if (event.equals(SocketEventConstants.newMessage)) {
//            addNewMessageHandler();
//        }
    }

    private void broadcastEvent(Intent intent){
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public boolean isSocketConnected(){
        if (mSocket == null){
            return false;
        }
        return mSocket.connected();
    }

    public void showNotificaitons(String username, String message){

        Intent toLaunch = new Intent(getApplicationContext(), Dummy.class);
        toLaunch.putExtra("username",message);
        toLaunch.putExtra("message",message);
// You'd need this line only if you had shown the notification from a Service
        toLaunch.setAction("android.intent.action.MAIN");

        PendingIntent pIntent = PendingIntent.getActivity(getApplicationContext(), 0,toLaunch, PendingIntent.FLAG_UPDATE_CURRENT);


// build notification
// the addAction re-use the same intent to keep the example short

        Notification n  = new NotificationCompat.Builder(this)
                .setContentTitle("You have pending new messages")
                .setContentText("New Message")
                .setContentIntent(pIntent)
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_launcher_hdp)
                .build();

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
        notificationManager.notify(0, n);
    }

    public boolean isForeground(String myPackage) {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runningTaskInfo = manager.getRunningTasks(1);
        ComponentName componentInfo = runningTaskInfo.get(0).topActivity;
        return componentInfo.getPackageName().equals(myPackage);
    }
}
