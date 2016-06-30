package com.im.mqttdemo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.PowerManager;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class PushCallback implements MqttCallback {

	private ContextWrapper context;
	private NotificationManager mNotifMan;
	private PowerManager.WakeLock wakeLock;

	public PushCallback(ContextWrapper context) {

		this.context = context;
		mNotifMan = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	@Override
	public void connectionLost(Throwable cause) {
		// We should reconnect here
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void messageArrived(String topic, MqttMessage message)
			throws Exception {
			newMsgNotify(new String(message.getPayload(),"UTF-8"));
	}

	/**
	 * 新消息通知
	 */
	private void newMsgNotify(String messageBody) {
		CharSequence tickerText = "MQTT新通知！";
		// 收到单人消息时，亮屏
		acquireWakeLock();
		Intent intent = new Intent(context, MainActivity.class);
		// 必须添加
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 77,
				intent, PendingIntent.FLAG_UPDATE_CURRENT);
		Notification notification = new Notification.Builder(context)
				.setContentTitle("MQTT").setContentText(messageBody)
				.setContentIntent(contentIntent).setTicker(tickerText)
				.setSmallIcon(R.mipmap.ic_launcher).build();
		// 设置默认声音
		notification.defaults |= Notification.DEFAULT_SOUND;
		// 设定震动(需加VIBRATE权限)
		notification.defaults |= Notification.DEFAULT_VIBRATE;
		notification.vibrate = new long[] { 0, 100, 200, 300 };
		// 设置LED闪烁
		notification.defaults |= Notification.DEFAULT_LIGHTS;
		notification.ledARGB = 0xff00ff00;
		notification.ledOnMS = 300;
		notification.ledOffMS = 1000;
		notification.flags |= Notification.FLAG_SHOW_LIGHTS;

		// 点击通知后 通知栏消失
		notification.flags = Notification.FLAG_AUTO_CANCEL;
		mNotifMan.notify(5555, notification);
	}

	/**
	 * 方法 点亮屏幕 要加权限 <uses-permission
	 * android:name="android.permission.WAKE_LOCK"></uses-permission>
	 */
	private void acquireWakeLock() {
		if (wakeLock == null) {
			PowerManager powerManager = (PowerManager) context
					.getSystemService(Context.POWER_SERVICE);
			wakeLock = powerManager.newWakeLock(
					PowerManager.ACQUIRE_CAUSES_WAKEUP
							| PowerManager.SCREEN_DIM_WAKE_LOCK, "lzh");
		}
		wakeLock.acquire();
		wakeLock.release();
	}
}
