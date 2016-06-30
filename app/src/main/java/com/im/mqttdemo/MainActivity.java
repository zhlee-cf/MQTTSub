package com.im.mqttdemo;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private Intent service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        service = new Intent(this,MQTTService.class);
    }

    public void startService(View view) {
        startService(service);
    }

    public void stopService(View view) {
        stopService(service);
    }
}
