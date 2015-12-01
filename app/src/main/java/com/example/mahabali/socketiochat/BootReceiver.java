package com.example.mahabali.socketiochat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.mahabali.socketioservice.SocketIOService;

/**
 * Created by Mahabali on 11/14/15.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(SocketIOService.class.getName());
        context.startService(serviceIntent);
    }
}
