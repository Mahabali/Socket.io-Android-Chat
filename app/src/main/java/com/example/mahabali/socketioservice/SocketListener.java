package com.example.mahabali.socketioservice;

/**
 * Created by Mahabali on 11/16/15.
 */
public interface SocketListener {
    void onSocketConnected();
    void onSocketDisconnected();
    void onNewMessageReceived(String username,String message);
}
