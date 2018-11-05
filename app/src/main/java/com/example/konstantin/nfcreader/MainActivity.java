package com.example.konstantin.nfcreader;

import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcV;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Parcelable;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.konstantin.nfcreader.record.ParsedNdefRecord;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    private static final String PREFERENCES = "sets";
    private static final String PREFERENCES_IP="ip";

    private float currentGlucose = 0f;

    SharedPreferences preferences;
    SharedPreferences.Editor editor;

    String address;

    TextView text, textView;
    EditText etIP,etText;
    NfcAdapter nfcAdapter;
    PendingIntent pendingIntent;

    private static final String TAG = "MainActivity";
    private static final UUID myUUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    BluetoothAdapter mBluetoothAdapter;
    Button btnEnableDisable_Discoverable, btnSend, btnGet;
    BluetoothConnectionService mBluetoothConnection;
    BluetoothDevice device;

    public ArrayList<BluetoothDevice> mBTDevices = new ArrayList<>();

    public DeviceListAdapter mDeviceListAdapter;

    ListView lvNewDevices;

    HttpConnection httpConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        text = findViewById(R.id.text);
        textView = findViewById(R.id.textView11) ;
        textView.setMovementMethod(new ScrollingMovementMethod());
        etIP = findViewById(R.id.etIP);
        etText = findViewById(R.id.etText);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        Button btnONOFF = findViewById(R.id.btnONOFF);
        btnEnableDisable_Discoverable = findViewById(R.id.btnDiscoverable_on_off);
        btnSend = findViewById(R.id.buttonSend);
        btnGet = findViewById(R.id.buttonGet);
        lvNewDevices = findViewById(R.id.lvNewDevices);

        preferences = getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE);
        if (preferences.contains(PREFERENCES_IP)) {
            etIP.setText(preferences.getString(PREFERENCES_IP,null));
        }

        mBTDevices = new ArrayList<>();

        //Broadcasts when bond state changes (ie:pairing)
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBroadcastReceiver4, filter);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        lvNewDevices.setOnItemClickListener(MainActivity.this);

        btnGet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                httpConnection = new HttpConnection(MainActivity.this);
                httpConnection.execute("http://" + etIP.getText().toString());
            }
        });


        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                httpConnection = new HttpConnection(MainActivity.this);
                httpConnection.execute("https://" + etIP.getText().toString(), etText.getText().toString());
            }
        });

        btnONOFF.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: enabling/disabling bluetooth.");
                enableDisableBT();
            }
        });

        if (nfcAdapter == null) {
            Toast.makeText(this, "No NFC", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, this.getClass())
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
    }

    public void changeText(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText(text);
            }
        });

    }

    private String dumpTagData(Tag tag) {
        StringBuilder sb = new StringBuilder();
        byte[] id = tag.getId();
        sb.append("ID (hex): ").append(toHex(id)).append('\n');
       // sb.append("ID (reversed hex): ").append(toReversedHex(id)).append('\n');
       // sb.append("ID (dec): ").append(toDec(id)).append('\n');
       // sb.append("ID (reversed dec): ").append(toReversedDec(id)).append('\n');

        String prefix = "android.nfc.tech.";
        sb.append("Technologies: ");
        for (String tech : tag.getTechList()) {
            sb.append(tech.substring(prefix.length()));
            sb.append(", ");
        }

        sb.delete(sb.length() - 2, sb.length());

        for (String tech : tag.getTechList()) {
            if (tech.equals(MifareClassic.class.getName())) {
                sb.append('\n');
                String type = "Unknown";

                try {
                    MifareClassic mifareTag = MifareClassic.get(tag);

                    switch (mifareTag.getType()) {
                        case MifareClassic.TYPE_CLASSIC:
                            type = "Classic";
                            break;
                        case MifareClassic.TYPE_PLUS:
                            type = "Plus";
                            break;
                        case MifareClassic.TYPE_PRO:
                            type = "Pro";
                            break;
                    }
                    sb.append("Mifare Classic type: ");
                    sb.append(type);
                    sb.append('\n');

                    sb.append("Mifare size: ");
                    sb.append(mifareTag.getSize() + " bytes");
                    sb.append('\n');

                    sb.append("Mifare sectors: ");
                    sb.append(mifareTag.getSectorCount());
                    sb.append('\n');

                    sb.append("Mifare blocks: ");
                    sb.append(mifareTag.getBlockCount());
                } catch (Exception e) {
                    sb.append("Mifare classic error: " + e.getMessage());
                }
            }

            if (tech.equals(MifareUltralight.class.getName())) {
                sb.append('\n');
                MifareUltralight mifareUlTag = MifareUltralight.get(tag);
                String type = "Unknown";
                switch (mifareUlTag.getType()) {
                    case MifareUltralight.TYPE_ULTRALIGHT:
                        type = "Ultralight";
                        break;
                    case MifareUltralight.TYPE_ULTRALIGHT_C:
                        type = "Ultralight C";
                        break;
                }
                sb.append("Mifare Ultralight type: ");
                sb.append(type);
            }
        }

        return sb.toString();
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; --i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
            if (i > 0) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private String toReversedHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; ++i) {
            if (i > 0) {
                sb.append(" ");
            }
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
        }
        return sb.toString();
    }

    private long toDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (byte aByte : bytes) {
            long value = aByte & 0xffL;
            result += value * factor;
            factor *= 256L;
        }
        return result;
    }

    private long toReversedDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = bytes.length - 1; i >= 0; --i) {
            long value = bytes[i] & 0xffL;
            result += value * factor;
            factor *= 256L;
        }
        return result;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (nfcAdapter != null) {
            if (!nfcAdapter.isEnabled())
                showWirelessSettings();
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
        }
    }

    private void showWirelessSettings() {
        Toast.makeText(this, "You need to enable NFC", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        startActivity(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        resolveIntent(intent);
    }

    Intent intentt;
    private void resolveIntent(Intent intent) {
        String action = intent.getAction();
        intentt = intent;

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] msgs;

            if (rawMsgs != null) {
                msgs = new NdefMessage[rawMsgs.length];
                for (int i = 0; i < rawMsgs.length; i++) {
                    msgs[i] = (NdefMessage) rawMsgs[i];
                }

            } else {
                byte[] empty = new byte[0];
                byte[] id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
                Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
                String[] techList = tag.getTechList();
                String searchedTech = NfcV.class.getName();
                for (String tech : techList) {
                    if (searchedTech.equals(tech)) {
                        new NfcVReaderTask().execute(tag);
                        break;
                    }
                }

               // new NfcVReaderTask().execute(tag);
            }

            //displayMsgs(msgs);
        }
    }

    private class NdefReaderTask extends AsyncTask<Tag,Void,String> {

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];
            NdefFormatable ndefFormatable = NdefFormatable.get(tag);
            if (ndefFormatable == null) {
                return null;
            }
            NdefMessage[] msgs;
            byte[] empty = new byte[0];
            byte[] id = intentt.getByteArrayExtra(NfcAdapter.EXTRA_ID);
            byte[] payload = dumpTagData(tag).getBytes();
            NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN, empty, id, payload);
            NdefMessage msg = new NdefMessage(new NdefRecord[] {record});
            msgs = new NdefMessage[] {msg};
            try {
                ndefFormatable.connect();
                ndefFormatable.format(msg);
            } catch (FormatException e) {
                StringWriter errors = new StringWriter();
                e.printStackTrace(new PrintWriter(errors));
                changeText(errors.toString());
            } catch (IOException e) {
                StringWriter errors = new StringWriter();
                e.printStackTrace(new PrintWriter(errors));
                changeText(errors.toString());
            }

            NdefRecord[] records = msg.getRecords();
            for (NdefRecord ndefRecord:records) {
                if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN
                        && Arrays.equals(ndefRecord.getType(),NdefRecord.RTD_TEXT)) {
                    try {
                        return readText(ndefRecord);
                    } catch (UnsupportedEncodingException e) {
                        StringWriter errors = new StringWriter();
                        e.printStackTrace(new PrintWriter(errors));
                        changeText(errors.toString());
                    }
                }

            }
            return null;
        }

        private String readText (NdefRecord record) throws UnsupportedEncodingException {
            byte[] payload = record.getPayload();
            String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";
            int languageCodeLength = payload[0] & 0063;
            return new String (payload
                    ,languageCodeLength + 1
                    ,payload.length - languageCodeLength - 1
                    ,textEncoding);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                changeText("Content: " + result);
            }
        }

    }

    private void displayMsgs(NdefMessage[] msgs) {
        if (msgs == null || msgs.length == 0)
            return;

        StringBuilder builder = new StringBuilder();
        List<ParsedNdefRecord> records = NdefMessageParser.parse(msgs[0]);
        final int size = records.size();

        for (int i = 0; i < size; i++) {
            ParsedNdefRecord record = records.get(i);
            String str = record.str();
            builder.append(str).append("\n");
        }

        text.setText(builder.toString());
    }

    private float glucoseReading(int val) {
        int bitmask = 0x0FFF;
        return (val & bitmask) / 6 - 37;
    }

    private final BroadcastReceiver mBroadcastReceiver1 = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch(state){
                    case BluetoothAdapter.STATE_OFF:
                        Log.d(TAG, "onReceive: STATE OFF");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "mBroadcastReceiver1: STATE TURNING OFF");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "mBroadcastReceiver1: STATE ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "mBroadcastReceiver1: STATE TURNING ON");
                        break;
                }
            }
        }
    };

    /**
     * Broadcast Receiver for changes made to bluetooth states such as:
     * 1) Discoverability mode on/off or expire.
     */
    private final BroadcastReceiver mBroadcastReceiver2 = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {

                int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR);

                switch (mode) {
                    //Device is in Discoverable Mode
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Enabled.");
                        break;
                    //Device not in discoverable mode
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Able to receive connections.");
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Not able to receive connections.");
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.d(TAG, "mBroadcastReceiver2: Connecting....");
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.d(TAG, "mBroadcastReceiver2: Connected.");
                        break;
                }

            }
        }
    };
    /**
     * Broadcast Receiver for listing devices that are not yet paired
     * -Executed by btnDiscover() method.
     */
    private BroadcastReceiver mBroadcastReceiver3 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "onReceive: ACTION FOUND.");

            if (action.equals(BluetoothDevice.ACTION_FOUND)){
                BluetoothDevice device = intent.getParcelableExtra (BluetoothDevice.EXTRA_DEVICE);
                mBTDevices.add(device);
                Log.d(TAG, "onReceive: " + device.getName() + ": " + device.getAddress());
                mDeviceListAdapter = new DeviceListAdapter(context, R.layout.device_adapter_view, mBTDevices);
                lvNewDevices.setAdapter(mDeviceListAdapter);
            }
        }
    };

    /**
     * Broadcast Receiver that detects bond state changes (Pairing status changes)
     */
    private final BroadcastReceiver mBroadcastReceiver4 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "BroadcastReceiver: 123.");

            if(action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)){
                BluetoothDevice mDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(TAG, "BroadcastReceiver: choosing cases.");
                //3 cases:
                //case1: bonded already
                if (mDevice.getBondState() == BluetoothDevice.BOND_BONDED){
                    Log.d(TAG, "BroadcastReceiver: BOND_BONDED.");
                    Toast.makeText(context,"BroadcastReceiver: BOND_BONDED.",Toast.LENGTH_SHORT).show();
                    device=mDevice;
                }
                //case2: creating a bone
                if (mDevice.getBondState() == BluetoothDevice.BOND_BONDING) {
                    Log.d(TAG, "BroadcastReceiver: BOND_BONDING.");
                    Toast.makeText(context,"BroadcastReceiver: BOND_BONDING.",Toast.LENGTH_SHORT).show();
                }
                //case3: breaking a bond
                if (mDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                    Log.d(TAG, "BroadcastReceiver: BOND_NONE.");
                    Toast.makeText(context,"BroadcastReceiver: BOND_NONE.",Toast.LENGTH_SHORT).show();
                }
            }
        }
    };



    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: called.");
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver1);
        unregisterReceiver(mBroadcastReceiver2);
        unregisterReceiver(mBroadcastReceiver3);
        unregisterReceiver(mBroadcastReceiver4);
        address = etIP.getText().toString();
        editor = preferences.edit();
        editor.putString(PREFERENCES_IP,address);
        editor.apply();
        //mBluetoothAdapter.cancelDiscovery();
    }

    public void enableDisableBT(){
        if(mBluetoothAdapter == null){
            Log.d(TAG, "enableDisableBT: Does not have BT capabilities.");
        }
        if(!mBluetoothAdapter.isEnabled()){
            Log.d(TAG, "enableDisableBT: enabling BT.");
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBTIntent);

            IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mBroadcastReceiver1, BTIntent);
        }
        if(mBluetoothAdapter.isEnabled()){
            Log.d(TAG, "enableDisableBT: disabling BT.");
            mBluetoothAdapter.disable();

            IntentFilter BTIntent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            registerReceiver(mBroadcastReceiver1, BTIntent);
        }

    }


    public void btnEnableDisable_Discoverable(View view) {
        Log.d(TAG, "btnEnableDisable_Discoverable: Making device discoverable for 300 seconds.");

        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);

        IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        registerReceiver(mBroadcastReceiver2,intentFilter);

    }

    public void btnDiscover(View view) {
        Log.d(TAG, "btnDiscover: Looking for unpaired devices.");

        if(mBluetoothAdapter.isDiscovering()){
            mBluetoothAdapter.cancelDiscovery();
            Log.d(TAG, "btnDiscover: Canceling discovery.");
            Toast.makeText(this,"btnDiscover: Canceling discovery",Toast.LENGTH_SHORT).show();

            //check BT permissions in manifest
            checkBTPermissions();

            mBluetoothAdapter.startDiscovery();
            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
        }
        if(!mBluetoothAdapter.isDiscovering()){

            //check BT permissions in manifest
            checkBTPermissions();

            mBluetoothAdapter.startDiscovery();
            IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent);
        }
    }

    /**
     * This method is required for all devices running API23+
     * Android must programmatically check the permissions for bluetooth. Putting the proper permissions
     * in the manifest is not enough.
     *
     * NOTE: This will only execute on versions > LOLLIPOP because it is not needed otherwise.
     */
    private void checkBTPermissions() {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP){
            int permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
            permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
            if (permissionCheck != 0) {

                this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001); //Any number
            }
        }else{
            Log.d(TAG, "checkBTPermissions: No need to check permissions. SDK version < LOLLIPOP.");
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        //first cancel discovery because its very memory intensive.
        mBluetoothAdapter.cancelDiscovery();

        Log.d(TAG, "onItemClick: You Clicked on a device.");
        String deviceName = mBTDevices.get(i).getName();
        String deviceAddress = mBTDevices.get(i).getAddress();

        Log.d(TAG, "onItemClick: deviceName = " + deviceName);
        Log.d(TAG, "onItemClick: deviceAddress = " + deviceAddress);

        //create the bond.
        //NOTE: Requires API 17+? I think this is JellyBean
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2){
            Log.d(TAG, "Trying to pair with " + deviceName);
            mBTDevices.get(i).createBond();

            device = mBTDevices.get(i);
            mBluetoothConnection = new BluetoothConnectionService(MainActivity.this);

            mBluetoothConnection.startClient(device,myUUID);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        address = etIP.getText().toString();
        editor = preferences.edit();
        editor.putString(PREFERENCES_IP,address);
        editor.apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        address = etIP.getText().toString();
        editor = preferences.edit();
        editor.putString(PREFERENCES_IP,address);
        editor.apply();
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }


    private class NfcVReaderTask extends AsyncTask<Tag, Void, String> {

        String lectura = "";
        @Override
        protected void onPostExecute(String result) {
            Vibrator vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(1000);
        }

        @Override
        protected String doInBackground(Tag... params) {
            Tag tag = params[0];
            NfcV nfcvTag = NfcV.get(tag);

            Log.d("socialdiabetes", "Enter NdefReaderTask: " + nfcvTag.toString());
            Log.d("socialdiabetes", "Tag ID: "+ Arrays.toString(tag.getId()));
            try {
                nfcvTag.connect();
            } catch (IOException e) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Error opening NFC connection!", Toast.LENGTH_SHORT).show();
                    }
                });

                return null;
            }

            byte[][] bloques = new byte[40][8];
            byte[] allBlocks = new byte[40*8];




            Log.d("socialdiabetes", "---------------------------------------------------------------");
            try {
                // Get system information (0x2B)
                byte[] tagUid = tag.getId();
                byte[] cmd = new byte[] {
                        (byte)0x20,
                        (byte)0x2B,
                        (byte)0x00,
                        (byte)0x00,
                        (byte)0x00,
                        (byte)0x00,
                        (byte)0x00,
                        (byte)0x00,
                        (byte)0x00,
                        (byte)0x00// Command: Get system information
                };
                System.arraycopy(tagUid,0,cmd,2,8);
                byte[] systeminfo = nfcvTag.transceive(cmd);
                Log.d("socialdiabetes", "SYSTEMINFO");

                systeminfo = Arrays.copyOfRange(systeminfo, 2, systeminfo.length - 1);

                byte[] memorySize = { systeminfo[6], systeminfo[5]};
                Log.d("socialdiabetes", "Memory Size: "+bytesToHex(memorySize)+" / "+ Integer.parseInt(bytesToHex(memorySize).trim(), 16 ));

                byte[] blocks = { systeminfo[8]};
                Log.d("socialdiabetes", "blocks: "+bytesToHex(blocks)+" / "+ Integer.parseInt(bytesToHex(blocks).trim(), 16 ));

                int totalBlocks = Integer.parseInt(bytesToHex(blocks).trim(), 16);

                int blockz = 2;
              /*  for(int i=3; i <= 23; i++) { // Leer solo los bloques que nos interesan
                    cmd = new byte[] {
                            (byte)0x00, // Flags
                            (byte)0x23,
                            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,// Command: Read multiple blocks
                            (byte)(i & 0x0ff), // First block (offset)
                            (byte)((blockz - 1) & 0x0ff)  // Number of blocks
                    };

                    cmd = new byte[]{
                            (byte) 0x00, // Flags
                            (byte) 0x23,
                            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,// Command: Read multiple blocks
                            (byte) (i & 0x0ff), // First block (offset)
                            (byte) ((blockz - 1) & 0x0ff)
                    };
                    System.arraycopy(tagUid,0,cmd,2,8);
                    byte[] oneBlock = nfcvTag.transceive(cmd);

                    Log.d("socialdiabetes", "userdata: "+ Arrays.toString(oneBlock) +" - "+oneBlock.length);
                    oneBlock = Arrays.copyOfRange(oneBlock, 1, oneBlock.length);
                    bloques[i-3] = Arrays.copyOf(oneBlock, 8);

                    Log.d("socialdiabetes", "userdata HEX: "+bytesToHex(oneBlock));

                    lectura += bytesToHex(oneBlock) + "\r\n";
                } */

                cmd = new byte[]{
                        (byte) 0x00, // Flags
                        (byte) 0x23, // Command: Read multiple blocks
                        (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                        (byte) (3 & 0x0ff), // First block (offset)
                        (byte) ((40 - 1) & 0x0ff) //Number of blocks
                };
                System.arraycopy(tagUid,0,cmd,2,8);
                byte[] oneBlock = nfcvTag.transceive(cmd);

                Log.d("socialdiabetes", "userdata: "+ Arrays.toString(oneBlock) +" - "+oneBlock.length);
                oneBlock = Arrays.copyOfRange(oneBlock, 1, oneBlock.length);
                bloques[40-3] = Arrays.copyOf(oneBlock, 8);

                Log.d("socialdiabetes", "userdata HEX: "+bytesToHex(oneBlock));

                lectura += bytesToHex(oneBlock) + "\r\n";

                String s = "";
                for(int i=0;i<40;i++) {
                    Log.d("socialdiabetes", bytesToHex(bloques[i]));
                    s += bytesToHex(bloques[i]);
                }

                Log.d("socialdiabetes", "S: "+s);

                Log.d("socialdiabetes", "Next read: "+s.substring(4,6));
                int current = Integer.parseInt(s.substring(4, 6), 16);
                Log.d("socialdiabetes", "Next read: "+current);
                Log.d("socialdiabetes", "Next historic read "+s.substring(6,8));

                String[] bloque1 = new String[16];
                String[] bloque2 = new String[32];
                Log.d("socialdiabetes", "--------------------------------------------------");
                int ii=0;
                for (int i=8; i< 8+15*12; i+=12)
                {
                    Log.d("socialdiabetes", s.substring(i,i+12));
                    bloque1[ii] = s.substring(i,i+12);

                    final String g = s.substring(i+2,i+4)+s.substring(i,i+2);

                    if (current == ii) {
                        currentGlucose = glucoseReading(Integer.parseInt(g,16));
                    }
                    ii++;


                }
                lectura = lectura + "Current approximate glucose " + currentGlucose;
                Log.d("socialdiabetes", "Current approximate glucose "+currentGlucose);

                Log.d("socialdiabetes", "--------------------------------------------------");
                ii=0;
                for (int i=188; i< 188+31*12; i+=12)
                {
                    Log.d("socialdiabetes", s.substring(i,i+12));
                    bloque2[ii] = s.substring(i,i+12);
                    ii++;
                }
                Log.d("socialdiabetes", "--------------------------------------------------");

            } catch (IOException e) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Error reading NFC!", Toast.LENGTH_SHORT).show();
                    }
                });
                StringWriter errors = new StringWriter();
                e.printStackTrace(new PrintWriter(errors));
                changeText(errors.toString());

                return null;
            }

            changeText(lectura);

            try {
                nfcvTag.close();
            } catch (IOException e) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Error closing NFC connection!", Toast.LENGTH_SHORT).show();
                    }
                });
                return null;
            }


            MediaPlayer mp;
            mp = MediaPlayer.create(MainActivity.this, R.raw.notification);
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mp.reset();
                    mp.release();
                }
            });
            mp.start();

            Date date = new Date() ;
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss") ;
            File myFile = new File(Environment.getExternalStorageDirectory().getPath()
                    + "/fsl_" + dateFormat.format(date) + ".log");
            try {
                myFile.createNewFile();
                FileOutputStream fOut = new FileOutputStream(myFile);
                OutputStreamWriter myOutWriter =new OutputStreamWriter(fOut);
                myOutWriter.append(lectura);
                myOutWriter.close();
                fOut.close();
            }
            catch (Exception e)
            {
            }
            return null;
        }


    }
}
