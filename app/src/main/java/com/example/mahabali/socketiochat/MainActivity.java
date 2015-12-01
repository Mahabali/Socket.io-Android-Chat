package com.example.mahabali.socketiochat;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.graphics.Rect;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AppSocketListener.getInstance().setAppConnectedToService(false);
    }

    @Override
    protected void onStart() {
        super.onStart();


    }
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }



    @Override
    public void onBackPressed() {
        MainFragment mainFragment = (MainFragment)getFragmentManager().
                findFragmentById(R.id.fragment_main);
        mainFragment.askForLogout();
    }
}
