package thomascasse.bluetoothchat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.UUID;

import thomascasse.bluetoothchat.R;

public class MainActivity extends AppCompatActivity
{
    private static final String TAG = "Main";

    Button enableDiscoverBtn;
    Button discoverBtn;

    Button startBtn;
    Button sendBtn;
    EditText sendText;

    StringBuilder messages;
    TextView chatBox;

    BluetoothDevice device;
    BluetoothChatService bluetoothService;

    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    BluetoothAdapter bluetoothAdapter;

    public ArrayList<BluetoothDevice> btDevices = new ArrayList<>();
    public DeviceListAdapter deviceListAdapter;
    ListView deviceList;

    private final BroadcastReceiver modeReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if(action.equals(bluetoothAdapter.ACTION_STATE_CHANGED))
            {
                final int mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, bluetoothAdapter.ERROR);

                switch(mode)
                {
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                        Log.d(TAG, "Discoverability Enabled");
                        break;
                    case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                        Log.d(TAG, "Discoverability Disabled, Can Receive Connections");
                        break;
                    case BluetoothAdapter.SCAN_MODE_NONE:
                        Log.d(TAG, "Discoverability Disabled, Can NOT Receive Connections");
                        break;
                    case BluetoothAdapter.STATE_CONNECTING:
                        Log.d(TAG, "Connecting...");
                        break;
                    case BluetoothAdapter.STATE_CONNECTED:
                        Log.d(TAG, "Connected");
                        break;
                }
            }
        }
    };

    private final BroadcastReceiver deviceReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();
            Log.d(TAG, "ACTION FOUND");

            if(action.equals(BluetoothDevice.ACTION_FOUND))
            {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                btDevices.add(device);
                Log.d(TAG, device.getName() + ": " + device.getAddress());
                deviceListAdapter = new DeviceListAdapter(context, R.layout.device_adapter_view, btDevices);
                deviceList.setAdapter(deviceListAdapter);
            }
        }
    };

    private final BroadcastReceiver bondReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            if(action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            {
                BluetoothDevice extraDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                switch (extraDevice.getBondState())
                {
                    //if bonded already
                    case BluetoothDevice.BOND_BONDED:
                        Log.d(TAG, "BOND BONDED");
                        device = extraDevice;
                        break;

                    //if creating bond
                    case BluetoothDevice.BOND_BONDING:
                        Log.d(TAG, "BOND BONDING");
                        break;

                    //if breaking bond
                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "BOND NONE");
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        enableDiscoverBtn = findViewById(R.id.enableDiscoverBtn);
        discoverBtn = findViewById(R.id.discoverBtn);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        deviceList = findViewById(R.id.deviceList);
        btDevices = new ArrayList<>();

        startBtn = findViewById(R.id.startBtn);
        sendBtn = findViewById(R.id.sendBtn);
        sendText = findViewById(R.id.sendText);

        messages = new StringBuilder();
        chatBox = findViewById(R.id.chatBox);

        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter("incomingMessage"));

        //Broadcast bond state changes
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(bondReceiver, intentFilter);

        // ----------- Start Button --------------
        startBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                startBTConnection(device, MY_UUID_INSECURE);
            }
        });

        // ----------- Send Button --------------
        sendBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                byte[] bytes = sendText.getText().toString().getBytes(Charset.defaultCharset());
                bluetoothService.write(bytes);

                sendText.setText("");
            }
        });

        // ----------- Enable Discovery Button --------------
        enableDiscoverBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Log.d(TAG, "Making device discoverable");

                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                startActivity(intent);

                IntentFilter intentFilter = new IntentFilter(bluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
                registerReceiver(modeReceiver, intentFilter);
            }
        });

        // ----------- Discover Button --------------
        discoverBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(bluetoothAdapter.isDiscovering())
                    bluetoothAdapter.cancelDiscovery();

                checkBTPermissions();

                bluetoothAdapter.startDiscovery();
                IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                registerReceiver(deviceReceiver, intentFilter);
            }
        });

        // ----------- Bond Button --------------
        deviceList.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int i, long id)
            {
                bluetoothAdapter.cancelDiscovery();

                String deviceName = btDevices.get(i).getName();
                String deviceAdress = btDevices.get(i).getAddress();

                Log.d(TAG, "ITEM CLICK - NAME: " + deviceName + ", ADRESS: " + deviceAdress);

                if(Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2)
                {
                    Log.d(TAG, "Trying to pair with " + deviceName);
                    btDevices.get(i).createBond();

                    device = btDevices.get(i);
                    bluetoothService = new BluetoothChatService(MainActivity.this);
                }
            }
        });
    }

    BroadcastReceiver receiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String text = intent.getStringExtra("thomascasse.message");

            messages.append(text + "\n");
            chatBox.setText(messages);
        }
    };

    public void startBTConnection(BluetoothDevice device, UUID uuid)
    {
        Log.d(TAG, "Starting Bluetooth Connection");

        bluetoothService.startClient(device, uuid);
    }

    private void checkBTPermissions()
    {
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP)
        {
            int permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
            permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");

            if(permissionCheck != 0)
            {
                this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
            }
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(modeReceiver);
        unregisterReceiver(bondReceiver);
        unregisterReceiver(deviceReceiver);
    }
}










