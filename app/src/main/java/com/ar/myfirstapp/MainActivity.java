package com.ar.myfirstapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;

    private boolean mScanning;

    private static final int RQS_ENABLE_BLUETOOTH = 1;

    Button btnScan;
    ListView listViewLE;

    List<BluetoothDevice> listBluetoothDevice;
    ListAdapter adapterLeScanResult;

    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check if BLE is supported on the device.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this,
                    "BLUETOOTH_LE not supported in this device!",
                    Toast.LENGTH_SHORT).show();
            finish();
        }

        getBluetoothAdapterAndLeScanner();

        // Checks if Bluetooth is supported on the device.

        if (mBluetoothAdapter == null) {
            Toast.makeText(this,
                    "bluetoothManager.getAdapter()==null",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnScan = (Button)findViewById(R.id.scan);
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanLeDevice(true);
            }
        });
        listViewLE = (ListView)findViewById(R.id.lelist);

        listBluetoothDevice = new ArrayList<>();
        adapterLeScanResult = new ArrayAdapter<BluetoothDevice>(
                this, android.R.layout.simple_list_item_1, listBluetoothDevice);
        listViewLE.setAdapter(adapterLeScanResult);
        listViewLE.setOnItemClickListener(scanResultOnItemClickListener);

        mHandler = new Handler();

    }

    AdapterView.OnItemClickListener scanResultOnItemClickListener =
            new AdapterView.OnItemClickListener(){

                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    final BluetoothDevice device = (BluetoothDevice) parent.getItemAtPosition(position);

                    String msg = device.getAddress() + "\n"
                            + device.getBluetoothClass().toString() + "\n"
                            + getBTDevieType(device);

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(device.getName())
                            .setMessage(msg)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                }
                            })
                            .show();

                }
            };

    private String getBTDevieType(BluetoothDevice d){
        String type = "";

        switch (d.getType()){
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                type = "DEVICE_TYPE_CLASSIC";
                break;
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                type = "DEVICE_TYPE_DUAL";
                break;
            case BluetoothDevice.DEVICE_TYPE_LE:
                type = "DEVICE_TYPE_LE";
                break;
            case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
                type = "DEVICE_TYPE_UNKNOWN";
                break;
            default:
                type = "unknown...";
        }

        return type;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, RQS_ENABLE_BLUETOOTH);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == RQS_ENABLE_BLUETOOTH && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }

        getBluetoothAdapterAndLeScanner();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this,
                    "bluetoothManager.getAdapter()==null",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void getBluetoothAdapterAndLeScanner(){
        // Get BluetoothAdapter and BluetoothLeScanner.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        mScanning = false;
    }

    /*
    to call startScan (ScanCallback callback),
    Requires BLUETOOTH_ADMIN permission.
    Must hold ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION permission to get results.
     */
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            listBluetoothDevice.clear();
            listViewLE.invalidateViews();

            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothLeScanner.stopScan(scanCallback);
                    listViewLE.invalidateViews();

                    Toast.makeText(MainActivity.this,
                            "Scan timeout",
                            Toast.LENGTH_LONG).show();

                    mScanning = false;
                    btnScan.setEnabled(true);
                }
            }, SCAN_PERIOD);

            this.readObdDevices();
            mBluetoothLeScanner.startScan(scanCallback);
            mScanning = true;
            btnScan.setEnabled(false);
        } else {
            mBluetoothLeScanner.stopScan(scanCallback);
            mScanning = false;
            btnScan.setEnabled(true);
        }
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            addBluetoothDevice(result.getDevice());
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for(ScanResult result : results){
                addBluetoothDevice(result.getDevice());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Toast.makeText(MainActivity.this,
                    "onScanFailed: " + String.valueOf(errorCode),
                    Toast.LENGTH_LONG).show();
        }

        private void addBluetoothDevice(BluetoothDevice device){
            if(!listBluetoothDevice.contains(device)){
                listBluetoothDevice.add(device);
                listViewLE.invalidateViews();
            }
        }
    };


    public void readObdDevices(){
        ArrayList deviceStrs = new ArrayList();
        final ArrayList devices = new ArrayList();

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        String deviceAddress = new String();
        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("AR")) {
                    deviceAddress = device.getAddress();
                    try {
                        connect(deviceAddress);
                    }catch (IOException e){
                        boolean connected = false;
                    }
                }
                deviceStrs.add(device.getName() + "\n" + device.getAddress());
                devices.add(device.getAddress());
            }
        }

        // show list
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

        ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.select_dialog_singlechoice,
                deviceStrs.toArray(new String[deviceStrs.size()]));

        alertDialog.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.dismiss();
                int position = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                String deviceAddress = (String) devices.get(position);

                // TODO save deviceAddress
            }
        });

        alertDialog.setTitle("Choose Bluetooth device");
        alertDialog.show();

        try {
        connect("");
        }catch (IOException e){
            Log.getStackTraceString(e);
        }
    }


    public static void connect(String deviceAddress) throws IOException{
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = btAdapter.getRemoteDevice(deviceAddress);
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        BluetoothSocket socket = null;
        BluetoothSocket sockFallback = null;

        try {
            socket = device.createRfcommSocketToServiceRecord(uuid);
        socket.connect();
        }
        catch (IOException e){
            String TAG = BluetoothManager.class.getName();
            Log.e(TAG, "There was an error while establishing Bluetooth connection. Falling back..", e);
            Class<?> clazz = socket.getRemoteDevice().getClass();
            Class<?>[] paramTypes = new Class<?>[]{Integer.TYPE};
            try {
                Method m = clazz.getMethod("createRfcommSocket", paramTypes);
                Object[] params = new Object[]{Integer.valueOf(1)};
                sockFallback = (BluetoothSocket) m.invoke(socket.getRemoteDevice(), params);
                sockFallback.connect();
                socket = sockFallback;
            } catch (Exception e2) {
                Log.e(TAG, "Couldn't fallback while establishing Bluetooth connection.", e2);
                throw new IOException(e2.getMessage());
            }
        }
    }
}
