package com.example.konstantin.nfcreader;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothConnectionService extends AppCompatActivity {

    private final BluetoothAdapter bluetoothAdapter;
    private Context mContext;
    private AcceptThread mInsecureAcceptThread;

    private ConnectThread mConnectThread;
    private UUID deviceUUID;
    private BluetoothDevice bluetoothDevice;
    ProgressDialog progressDialog;
    private ConnectedThread connectedThread;

    private static final String TAG = "BluetoothConnectionServ";
    private static final String appName = "NFCReader";
    private static final UUID myUUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    public BluetoothConnectionService(Context context) {
        mContext = context;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        start();
    }

    //It runs until a connection is accepted
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mBluetoothServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp  = null;

            //Create a new listening server socket
            try {
                tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName,myUUID);
                Log.d(TAG, "AcceptedThread: Setting up server using: " + myUUID);
            } catch (IOException e) {
                Log.e(TAG,"AcceptThread: IOexception: " + e.getMessage());
            }

            mBluetoothServerSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "run: AcceptThread running");
            BluetoothSocket socket = null;
            try {
                socket = mBluetoothServerSocket.accept();
                Log.d(TAG, "run: connection is successful");
            } catch (IOException e) {
                Log.e(TAG,"run: IOexception: " + e.getMessage());
            }
            if(socket != null){
                connected(socket,bluetoothDevice);
            }
            Log.i(TAG,"ends AcceptThread");
        }

        public void cancel() {
            try {
                mBluetoothServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG,"cancel: IOException " + e.getMessage());
            }
        }
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket bluetoothSocket;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            Log.d(TAG,"ConnectThread started");
            bluetoothDevice = device;
            deviceUUID = uuid;
        }

        public void run() {
            BluetoothSocket tmp = null;
            try {
                tmp = bluetoothDevice.createRfcommSocketToServiceRecord(deviceUUID);
            } catch (IOException e) {
                Log.e(TAG,"run: IOException" + e.getMessage());
            }
            bluetoothSocket = tmp;
            bluetoothAdapter.cancelDiscovery();
            try {
                bluetoothSocket.connect();
            } catch (IOException e) {
                try {
                    bluetoothSocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            Log.d(TAG, "Couldn't connect to device with uuid " + myUUID);
            connected(bluetoothSocket,bluetoothDevice);
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG,"cancel: IOException " + e.getMessage());
            }
        }
    }

    public synchronized void start() {
        Log.d(TAG,"start");

        if (mConnectThread!=null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mInsecureAcceptThread ==null) {
            mInsecureAcceptThread = new AcceptThread();
            mInsecureAcceptThread.start();
        }
    }

    public void startClient (BluetoothDevice device, UUID uuid) {
        progressDialog = ProgressDialog.show(mContext,"Connecting Bluetooth","Please wait..", true);
        mConnectThread = new ConnectThread(device,uuid);
        mConnectThread.start();
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG,"ConnectedThread");
            bluetoothSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                progressDialog.dismiss();
            } catch (NullPointerException e){
                e.printStackTrace();
            }


            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        String incomingMessage;

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    incomingMessage = new String(buffer,0,bytes);
                    ((MainActivity)mContext).changeText(incomingMessage);

                } catch (IOException e) {
                    Log.e(TAG,"incoming SMS: Error reading input");
                    break;
                }

            }
        }
        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void connected(BluetoothSocket socket, BluetoothDevice device) {
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
    }

}
