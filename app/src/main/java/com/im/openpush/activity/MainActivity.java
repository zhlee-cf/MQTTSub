package com.im.openpush.activity;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.TextView;

import com.im.openpush.utils.MyToast;
import com.im.openpush.R;
import com.im.openpush.service.MQTTService;

public class MainActivity extends AppCompatActivity {

    private TextView tvVersion;
    private TextView tvTopic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        tvVersion = (TextView) findViewById(R.id.tv_version);
        PackageManager packageManager = getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), 0);
            String version = packageInfo.versionName;
            if (version != null){
                tvVersion.setText("版本号：" + version);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        tvTopic = (TextView) findViewById(R.id.tv_topic);
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String devId = telephonyManager.getDeviceId();
        String appId = getPackageName();
        String username = "lizh";
        String devType = "android";
        String topic = appId + "/" + username + "/" + devType + "/" + devId;
        tvTopic.setText("TOPIC：" + "\n" + topic);
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
