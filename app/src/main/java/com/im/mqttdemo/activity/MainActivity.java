package com.im.mqttdemo.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.im.mqttdemo.utils.MyToast;
import com.im.mqttdemo.R;
import com.im.mqttdemo.service.MQTTService;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void startService(View view) {
        MQTTService.actionStart(this);
        MyToast.showToast(this, "开启服务成功");
    }

    public void stopService(View view) {
        MQTTService.actionStop(this);
        MyToast.showToast(this, "关闭服务成功");
    }
}
