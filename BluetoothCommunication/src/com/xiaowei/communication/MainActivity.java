package com.xiaowei.communication;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
	
	private static final String TAG = "MainActivity";
	
	private Button btSend;
	
	private BluetoothAdapter bluetoothAdapter;
	
	private BroadcastReceiver receiver;
	
	private static final int REQUEST_OPEN_BT = 0x01; 

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		btSend = (Button) findViewById(R.id.bt_send);
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(bluetoothAdapter == null) {
			showToast("该设备不支持蓝牙");
			Log.d(TAG, "该设备不支持蓝牙");
			return;
		}
		if(!bluetoothAdapter.isEnabled()) {
			Intent enabler = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			enabler.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivityForResult(enabler, REQUEST_OPEN_BT);	
		}
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
		intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		intentFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
		intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if(BluetoothDevice.ACTION_FOUND.equals(action)) {
					BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					Log.d(TAG, "fond device:" + device.getName() + "," + device.getAddress());
				}
			}
		};
		registerReceiver(receiver, intentFilter);
		bluetoothAdapter.startDiscovery();
		btSend.setOnClickListener(new OnClickListener() {		
			@Override
			public void onClick(View v) { 
				showToast("发送");
			}
		});
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode == REQUEST_OPEN_BT) {
			if(resultCode == RESULT_CANCELED) {
				showToast("打开蓝牙失败");
			}else {
				showToast("打开蓝牙成功");
			}
		}
	}
	
	public void showToast(String msg) {
		Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 0);
		toast.show();
	}
}
