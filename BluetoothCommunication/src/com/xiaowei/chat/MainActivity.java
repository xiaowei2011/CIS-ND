package com.xiaowei.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	
	private static final String TAG = "MainActivity";
	
	private Button btSend;
	
	private Button btSearch;
	
	private TextView tvMessage;
	
	private TextView tvDevices;
	
	private EditText etTargetDevice;
	
	private EditText etMessage;
	
	private BluetoothAdapter bluetoothAdapter;
	
	private List<BluetoothDevice> newFindDevices = new ArrayList<BluetoothDevice>();
	
	private static final int REQUEST_OPEN_BT = 0x01;
	
	private UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	
	@SuppressLint("SimpleDateFormat")
	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private BluetoothDevice targetDevice;
	
	private BroadcastReceiver receiver;
	
	private BluetoothSocket clientSocket;
	
	private BluetoothServerSocket serverSocket;
	
	private Handler tvMessageHandler;
	

	@SuppressLint("HandlerLeak")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		btSend = (Button) findViewById(R.id.bt_send);
		btSearch = (Button) findViewById(R.id.bt_search);
		tvMessage = (TextView) findViewById(R.id.tv_message);
		tvDevices = (TextView) findViewById(R.id.tv_devices);
		etTargetDevice = (EditText) findViewById(R.id.et_target_device);
		etMessage = (EditText) findViewById(R.id.et_message);
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
		
		receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if(BluetoothDevice.ACTION_FOUND.equals(action)) {
					BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					newFindDevices.add(device);
					String name = device.getName();
					name = name == null ? "未知设备" : name;
					tvDevices.append(name + "," + device.getAddress() + "\n");
					Log.d(TAG, "fond device:" + device.getName() + "," + device.getAddress());
				}else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
					showToast("搜索结束");
				}else if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
					Log.d(TAG, "开始搜索...");
				}else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
					BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					if(device.getBondState() == BluetoothDevice.BOND_BONDING) {
						Log.d(TAG, "配对中：" + device.getName() + "," + device.getAddress());
					}else if(device.getBondState() == BluetoothDevice.BOND_BONDED) {
						Log.d(TAG, "配对完成：" + device.getName() + "," + device.getAddress());
					}else if(device.getBondState() == BluetoothDevice.BOND_NONE) {
						Log.d(TAG, "配对取消：" + device.getName() + "," + device.getAddress());
					}
				}
			}
		};
		
		tvMessageHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				switch(msg.what) {
				case 0:
					tvMessage.append(msg.obj.toString());
					break;
				}
			}
		};
		new Thread(new Runnable() {	
			@Override
			public void run() {
				while (true) {
					try {
						serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("myServer", uuid);
						Log.d(TAG, "serverSocket创建成功，" + serverSocket);
						BluetoothSocket socket = serverSocket.accept();
						InputStream input = socket.getInputStream();
						BluetoothDevice device = socket.getRemoteDevice();
						Log.d(TAG, "接收到socket," + socket + "," + device.getName());
						BufferedReader bf = new BufferedReader(new InputStreamReader(input));
						char[] buf = new char[1024];
						int len = bf.read(buf);
						String message = String.copyValueOf(buf, 0, len);
						Message msg = new Message();
						msg.obj = device.getName() + " " + dateFormat.format(new Date()) + "\n";
						tvMessageHandler.sendMessage(msg);
						msg = new Message();
						msg.obj = message + "\n";
						tvMessageHandler.sendMessage(msg);
						bf.close();
						input.close();
						socket.close();
						serverSocket.close();
					} catch (IOException e) {
						Log.e(TAG, "socket创建失败", e);
						break;
					}
				}
			}
		}).start();
		
		etTargetDevice.setOnFocusChangeListener(new OnFocusChangeListener() {	
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if(!hasFocus) {
					getTarget(); 
				}
			}
		});
		
		
		btSend.setOnClickListener(new OnClickListener() {		
			@Override
			public void onClick(View v) {
				etTargetDevice.clearFocus();
				if (targetDevice == null) {
					showToast("目标设备不存在");
					return;
				}
				try {
					if (targetDevice.getBondState() == BluetoothDevice.BOND_NONE) {
						Method createBound = BluetoothDevice.class.getMethod("createBond");
						createBound.invoke(targetDevice);
					}
					clientSocket = targetDevice.createRfcommSocketToServiceRecord(uuid);
					Log.d(TAG, "获取到clientSocket," + clientSocket + ",connect:" + clientSocket.isConnected());
					clientSocket.connect();
					OutputStream output = clientSocket.getOutputStream();
					String message = etMessage.getText().toString().trim();
					output.write(message.getBytes());
//					output.close();
//					clientSocket.close();
				} catch (Exception e) {
					Log.e(TAG, "绑定失败", e);
				}
			}
		});
		
		btSearch.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				newFindDevices.clear();
				tvDevices.setText("");
				if(bluetoothAdapter.isDiscovering()) {
					Log.d(TAG, "正在搜索...");
					bluetoothAdapter.cancelDiscovery();
					Log.d(TAG, "取消搜索...");
				}else {
					IntentFilter intentFilter = new IntentFilter();
					intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
					intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
					intentFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
					intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
					intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
					intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
					registerReceiver(receiver, intentFilter);
					bluetoothAdapter.startDiscovery();
				}
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
	
	private void getTarget() {
		String name = etTargetDevice.getText().toString().trim();
		boolean flag = false;
		for(BluetoothDevice device : newFindDevices) {
			if(name.equalsIgnoreCase(device.getName())) {
				targetDevice = device;
				flag = true;
				showToast("目标设备获取成功");
				Log.d(TAG, "目标设备获取成功:" + device.getName() + "," + device.getAddress());
				break;
			}
		}
		if(!flag) {		
			Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
			for(BluetoothDevice device : bondedDevices) {
				if(name.equalsIgnoreCase(device.getName())) {
					targetDevice = device;
					flag = true;
					showToast("目标设备获取成功");
					Log.d(TAG, "目标设备获取成功:" + device.getName() + "," + device.getAddress());
					break;
				}
			}
		}
		if(!flag) {
			showToast("目标设备获取失败");
			Log.d(TAG, "目标设备获取失败。");
		}
	}
	
	public void showToast(String msg) {
		Toast toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.CENTER_HORIZONTAL, 0, 0);
		toast.show();
	}

}
