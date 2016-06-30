package com.im.mqttdemo;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * MQTT接收推送主服务
 * Created by lzh12 on 2016/6/30.
 */
public class MQTTService extends Service{

    public static final String BROKER_URL = "tcp://openim.top:1883";
    public static final String TOPIC = "MQTT-Demo";
    private MqttClient mqttClient;
    private String deviceId;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        TelephonyManager telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        deviceId = telephonyManager.getDeviceId();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            mqttClient = new MqttClient(BROKER_URL, deviceId, new MemoryPersistence());
            mqttClient.setCallback(new PushCallback(this));
            mqttClient.connect();
            mqttClient.subscribe(TOPIC);
            MyLog.showLog("订阅成功");
        } catch (MqttException e) {
            e.printStackTrace();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        try {
            mqttClient.disconnect(0);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
