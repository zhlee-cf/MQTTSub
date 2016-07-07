package com.im.mqttdemo.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;

import com.im.mqttdemo.R;
import com.im.mqttdemo.activity.MainActivity;
import com.im.mqttdemo.utils.MyLog;
import com.im.mqttdemo.utils.ThreadUtil;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MQTT接收推送主服务
 * Created by zhlee_cf on 2016/6/30.
 */
public class MQTTService extends Service implements MqttCallback {

    // 服务器地址及端口号
    public static final String BROKER_URL = "tcp://q.emqtt.com:1883";
    // 订阅的主题，与发送端一样
    public static final String TOPIC = "MQTT-Demo";
    // 接收端对象
    private MqttClient mqttClient;
    // 设备号，作为客户端唯一标识，获取需要权限
    private String deviceId;
    // 标识是否是连接状态
    private boolean mStarted;
    // 这个好像是什么文件持久化  暂时没研究
    private MqttDefaultFilePersistence mDataStore;
    // 闹钟管理者
    private AlarmManager mAlarmManager;
    // 网络连接管理者 需要权限
    private ConnectivityManager mConnectivityManager;
    // 用于发送心跳包的对象
    private MqttTopic mKeepAliveTopic;
    // 电源锁 需要权限
    private PowerManager.WakeLock wakeLock;
    // 当前服务实例
    private MQTTService ctx;
    // 通知栏管理者
    private NotificationManager notificationManager;

    // 下面这一堆ACTION 就是启动服务时用的一个ACTION 会在onStartCommand方法里判断ACTION进行不同的操作
    public static String ACTION_BASE = "MQTT-DEMO";
    // 启动连接
    private static final String ACTION_START = ACTION_BASE + ".START";
    // 关闭连接
    private static final String ACTION_STOP = ACTION_BASE + ".STOP";
    // 启动心跳机制
    private static final String ACTION_KEEPALIVE = ACTION_BASE + ".KEEPALIVE";
    // 重新连接
    private static final String ACTION_RECONNECT = ACTION_BASE + ".RECONNECT";

    // 心跳包间隔，毫秒
    private static final int MQTT_KEEP_ALIVE = 60 * 1000;
    // 心跳包发送内容
    private static final byte[] MQTT_KEEP_ALIVE_MESSAGE = {0};
    // 发送心跳包时订阅的主题
    private static final String MQTT_KEEP_ALIVE_TOPIC_FORMAT = "MQTT-KEEP-ALIVE";
    // 心跳包的发送级别默认最低
    private static final int MQTT_KEEP_ALIVE_QOS = 0;
    // 时间格式
    private SimpleDateFormat ft;

    /**
     * 启动推送服务
     */
    public static void actionStart(Context ctx) {
        Intent i = new Intent(ctx, MQTTService.class);
        i.setAction(ACTION_START);
        ctx.startService(i);
    }

    /**
     * 停止推送服务
     */
    public static void actionStop(Context ctx) {
        Intent i = new Intent(ctx, MQTTService.class);
        i.setAction(ACTION_STOP);
        ctx.startService(i);
    }

    /**
     * 发送心跳包
     */
    public static void actionKeepalive(Context ctx) {
        Intent i = new Intent(ctx, MQTTService.class);
        i.setAction(ACTION_KEEPALIVE);
        ctx.startService(i);
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化数据 获取到一些需要用到的对象和设备号
        initData();
    }

    /**
     * 初始化数据 获取到一些需要用到的对象和设备号
     * 获取设备号要加权限
     */
    private void initData() {
        ctx = this;
        ft = new SimpleDateFormat("HH:mm:ss", Locale.US);
        TelephonyManager telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        deviceId = telephonyManager.getDeviceId();
        mDataStore = new MqttDefaultFilePersistence(getCacheDir().getAbsolutePath());
        mAlarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    /**
     * 判断ACTION进行不同的操作
     */
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (action.equals(ACTION_START)) {
                startPush();
            } else if (action.equals(ACTION_STOP)) {
                stopPush();
            } else if (action.equals(ACTION_KEEPALIVE)) {
                keepAlive();
            } else if (action.equals(ACTION_RECONNECT)) {
                if (isNetworkAvailable()) {
                    reconnectIfNecessary();
                }
            }
        } else {
            MyLog.showLog("intent::" + intent);
            startPush();
        }
        // 粘性
        return super.onStartCommand(intent, START_STICKY, startId);
    }

    /**
     * 开启推送服务 并注册网络切换监听
     */
    private synchronized void startPush() {
        if (mStarted) {
            return;
        }
        // 如果已经有个发送心跳包的闹钟了，先关了
        if (hasScheduledKeepAlive()) {
            stopKeepAlive();
        }
        connectToServer();
        registerReceiver(mConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    /**
     * 停止推送服务
     */
    private synchronized void stopPush() {
        if (!mStarted) {
            return;
        }
        if (mqttClient != null) {
            try {
                mqttClient.disconnect();
                mqttClient = null;
                mStarted = false;
            } catch (MqttException e) {
                e.printStackTrace();
            }
            stopKeepAlive();
        }
        unregisterReceiver(mConnectivityReceiver);
    }

    /**
     * 连接到推送服务器与适当的数据存储
     */
    private synchronized void connectToServer() {
        ThreadUtil.runOnBackThread(new Runnable() {
            @Override
            public void run() {
                try {
                    mqttClient = new MqttClient(BROKER_URL, deviceId, mDataStore);
                    mqttClient.connect();
                    mqttClient.subscribe(TOPIC, 2);
                    mqttClient.setCallback(MQTTService.this);
                    mStarted = true;
                    MyLog.showLog("连接服务器成功" + "--当前线程--" + Thread.currentThread().getName());
                } catch (MqttException e) {
                    e.printStackTrace();
                }
                startKeepAlive();
            }
        });
    }

    /**
     * 启动心跳包闹钟
     */
    private void startKeepAlive() {
        Intent i = new Intent(this, MQTTService.class);
        i.setAction(ACTION_KEEPALIVE);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        long triggerAtTime = System.currentTimeMillis();
        mAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtTime, MQTT_KEEP_ALIVE, pi);
    }

    /**
     * 取消已经存在的闹钟
     */
    private void stopKeepAlive() {
        Intent i = new Intent(this, MQTTService.class);
        i.setAction(ACTION_KEEPALIVE);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        mAlarmManager.cancel(pi);
    }

    /**
     * 发送心跳数据到服务器
     */
    private synchronized void keepAlive() {
        MyLog.showLog("发送心跳包");
        // 因为有个闹铃发送心跳包，正好可以借这个闹铃唤醒CPU
        acquireCPU();
        sendKeepAlive();
    }

    /**
     * 如果需要重连则重连
     */
    private synchronized void reconnectIfNecessary() {
        if (mStarted && mqttClient == null) {
            connectToServer();
        }
    }

    /**
     * 通过ConnectivityManager查询网络连接状态
     *
     * @return 如果网络状态正常则返回true反之flase
     */
    private boolean isNetworkAvailable() {
        NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        return (info != null) && info.isConnected() && info.isAvailable();
    }

    /**
     * 判断推送服务是否连接
     *
     * @return 如果是连接的则返回true反之false
     */
    private boolean isConnected() {
        return mqttClient != null && (mStarted && mqttClient.isConnected());
    }

    /**
     * 网络状态发生变化接收器
     */
    private final BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isNetworkAvailable()) {
                reconnectIfNecessary();
            } else {
                stopKeepAlive();
                mqttClient = null;
                mKeepAliveTopic = null;
            }
        }
    };

    /**
     * 发送保持连接的指定的主题
     */
    private synchronized void sendKeepAlive() {
        ThreadUtil.runOnBackThread(new Runnable() {
            @Override
            public void run() {
                if (mKeepAliveTopic == null && mqttClient != null) {
                    mKeepAliveTopic = mqttClient.getTopic(String.format(Locale.US, MQTT_KEEP_ALIVE_TOPIC_FORMAT, deviceId));
                }
                MqttMessage message = new MqttMessage(MQTT_KEEP_ALIVE_MESSAGE);
                message.setQos(MQTT_KEEP_ALIVE_QOS);
                MyLog.showLog("isConnected::" + isConnected() + "--当前线程--" + Thread.currentThread().getName());
                try {
                    if (mKeepAliveTopic != null)
                        mKeepAliveTopic.publish(message);
                } catch (MqttException e) {
                    reconnectIfNecessary();
                    MyLog.showLog("发送心跳包失败::" + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 查询是否已经有一个心跳包的闹钟
     *
     * @return 如果已经有一个心跳包的闹钟则返回true反之false
     */
    private synchronized boolean hasScheduledKeepAlive() {
        Intent i = new Intent(this, MQTTService.class);
        i.setAction(ACTION_KEEPALIVE);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, PendingIntent.FLAG_NO_CREATE);
        return (pi != null);
    }


    @Override
    /**
     * 断开连接时，回调到此
     */
    public void connectionLost(Throwable throwable) {
        MyLog.showLog("MQTT断开连接");
        // TODO 断开链接时，关闭了心跳闹铃
        stopKeepAlive();
        mqttClient = null;
        mKeepAliveTopic = null;
        if (isNetworkAvailable()) {
            reconnectIfNecessary();
        }
    }

    @Override
    /**
     * 收到消息时，回调到此
     */
    public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
        String msg = new String(mqttMessage.getPayload(), "UTF-8");
        newMsgNotify(msg + "**" + ft.format(new Date()));
    }

    @Override
    /**
     * 这个字面意思应该是发送完成时回调到此，不知道干嘛的
     */
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

    }


    /**
     * 新消息通知
     */
    private void newMsgNotify(String messageBody) {
        CharSequence tickerText = "MQTT新通知！";
        // 收到单人消息时，亮屏
        acquireWakeLock();
        Intent intent = new Intent(ctx, MainActivity.class);
        // 必须添加
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent contentIntent = PendingIntent.getActivity(ctx, 77,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(ctx)
                .setContentTitle("MQTT").setContentText(messageBody)
                .setContentIntent(contentIntent).setTicker(tickerText)
                        // 注意 如果不设置icon，可能会不显示通知栏
                .setSmallIcon(R.mipmap.ic_launcher).build();

        // 设置默认声音
        notification.defaults |= Notification.DEFAULT_SOUND;
        // 设定震动(需加VIBRATE权限)
        notification.defaults |= Notification.DEFAULT_VIBRATE;
        notification.vibrate = new long[]{0, 100, 200, 300};
        // 设置LED闪烁  但是LED貌似没有闪烁
        notification.defaults |= Notification.DEFAULT_LIGHTS;
        notification.ledARGB = 0xff00ff00;
        notification.ledOnMS = 300;
        notification.ledOffMS = 1000;
        notification.flags |= Notification.FLAG_SHOW_LIGHTS;

        // 点击通知后 通知栏消失
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        notificationManager.notify(5555, notification);
    }

    /**
     * 方法 点亮屏幕 要加权限 <uses-permission
     * android:name="android.permission.WAKE_LOCK"></uses-permission>
     */
    private void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                    PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.SCREEN_DIM_WAKE_LOCK, "lzh");
        }
        wakeLock.acquire();
        wakeLock.release();
    }

    /**
     * 只唤醒CPU，目的是防止系统休眠
     */
    private void acquireCPU() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (!pm.isScreenOn()) {
            PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cpu_tag");
            if (!wakeLock.isHeld()) {
                wakeLock.acquire(30);
            }
        }
    }

//    @Override
//    public void onDestroy() {
//        try {
//            mqttClient.disconnect();
//        } catch (MqttException e) {
//            e.printStackTrace();
//        }
//    }
}
