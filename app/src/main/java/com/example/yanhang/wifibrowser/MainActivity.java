package com.example.yanhang.wifibrowser;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Button;
import android.Manifest;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.atomic.AtomicBoolean;


public class MainActivity extends AppCompatActivity {

    ListView list_view_;
    ArrayAdapter<String> adapter_;

    Button btn_add_;
    TextView info_textview_;

    private PeriodicScan scanner_;
    private WifiManager wifi_manager_ref_;
    private BroadcastReceiver scan_receiver_ref_;

    boolean perm_access_wifi_ = false;
    boolean perm_change_wifi_ = false;
    boolean perm_coarse_location_ = false;
    boolean perm_internet_ = false;

    final static int REQUEST_CODE_ACCESS_WIFI = 1001;
    final static int REQUEST_CODE_CHANGE_WIFI = 1002;
    final static int REQUEST_CODE_COARSE_LOCATION = 1003;
    final static int REQUEST_CODE_INTERNET = 1004;

    ArrayList<String> wifi_info_list_ = new ArrayList<>();
    final static String LOG_TAG = "WifiBrowser";

    AtomicBoolean is_scanning_ = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        adapter_ = new ArrayAdapter<>(this, R.layout.list_row_item,
                R.id.list_item, wifi_info_list_);

        list_view_ = (ListView)findViewById(R.id.list_view);
        list_view_.setAdapter(adapter_);

        btn_add_ = (Button)findViewById(R.id.btn_add);
        info_textview_ = (TextView)findViewById(R.id.text_info);

        Runnable scanner_callback = new Runnable() {
            @Override
            public void run() {
                wifi_info_list_.clear();
                synchronized (this) {
                    for (String str : scanner_.getLatestScanResult()) {
                        wifi_info_list_.add(str);
                    }
                }
                adapter_.notifyDataSetChanged();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        info_textview_.setText("Scan: " + String.valueOf(scanner_.getRecordCount()));
                    }
                });
            }
        };

        scanner_ = new PeriodicScan(this, scanner_callback);
        scan_receiver_ref_ = scanner_.getBroadcastReceiver();
        wifi_manager_ref_ = scanner_.getWifiManager();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    public void onMenu1Clicked(MenuItem item){
        if(item.isChecked()){
            Toast.makeText(MainActivity.this, "Info hidden", Toast.LENGTH_SHORT).show();
            info_textview_.setVisibility(View.GONE);
            item.setChecked(false);
        }else{
            Toast.makeText(MainActivity.this, "Info shown", Toast.LENGTH_SHORT).show();
            info_textview_.setVisibility(View.VISIBLE);
            item.setChecked(true);
        }
    }

    private void initializeWifi(){
        if(!wifi_manager_ref_.isWifiEnabled()){
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Wifi is not enable. Click 'OK' to enable")
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            wifi_manager_ref_.setWifiEnabled(true);
                        }
                    }).show();
        }
        registerReceiver(scan_receiver_ref_, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    public void onStartStopClicked(View view){
        if(is_scanning_.get()){
            scanner_.terminate();
            info_textview_.setText("Scan paused");
            btn_add_.setText("Start");
            is_scanning_.set(false);
        }else {
            scanner_.start();
            info_textview_.setText("Scan initialized");
            btn_add_.setText("Stop");
            is_scanning_.set(true);
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        checkPermission();
        initializeWifi();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onPause(){
        super.onPause();
        unregisterReceiver(scan_receiver_ref_);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
        switch (requestCode){
            case REQUEST_CODE_ACCESS_WIFI:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    perm_access_wifi_ = true;
                    Log.i(LOG_TAG, "Access wifi granted");
                }
                break;
            case REQUEST_CODE_CHANGE_WIFI:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    perm_change_wifi_ = true;
                    Log.i(LOG_TAG, "Change wifi granted");
                }
                break;
            case REQUEST_CODE_COARSE_LOCATION:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    perm_coarse_location_ = true;
                    Log.i(LOG_TAG, "Coarse location granted");
                }
                break;
            case REQUEST_CODE_INTERNET:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    perm_internet_ = true;
                    Log.i(LOG_TAG, "Internet granted");
                }
                break;
        }
    }

    private void checkPermission(){
        int permissionCheck = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_WIFI_STATE);
        if(permissionCheck != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_WIFI_STATE}, REQUEST_CODE_ACCESS_WIFI);
        }else{
            Log.i(LOG_TAG, "Access wifi granted");
            perm_access_wifi_ = true;
        }

        permissionCheck = ContextCompat.checkSelfPermission(this, android.Manifest.permission.CHANGE_WIFI_STATE);
        if(permissionCheck != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CHANGE_WIFI_STATE}, REQUEST_CODE_CHANGE_WIFI);
        }else{
            Log.i(LOG_TAG, "Change wifi granted");
            perm_change_wifi_ = true;
        }

        permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
        if(permissionCheck != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE_CHANGE_WIFI);
        }else{
            Log.i(LOG_TAG, "Coarse location granted");
            perm_coarse_location_ = true;
        }

        permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET);
        if(permissionCheck != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, REQUEST_CODE_INTERNET);
        }else{
            Log.i(LOG_TAG, "Internet granted");
            perm_internet_ = true;
        }
    }
}
