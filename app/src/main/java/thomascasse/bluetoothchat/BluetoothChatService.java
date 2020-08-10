package thomascasse.bluetoothchat;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

public class BluetoothChatService
{
    private static final String TAG = "BluetoothChatService";
    private static final String appName = "MYAPP";
    private static final UUID MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private final BluetoothAdapter bluetoothAdapter;
    Context context;

    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private BluetoothDevice device;
    private UUID deviceUUID;
    private ProgressDialog progressDialog;


    public BluetoothChatService(Context context)
    {
        this.context = context;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        start();
    }

    //Start Connection Service
    public synchronized void start()
    {
        //Cancel any current connections
        if(connectThread != null)
        {
            connectThread.cancel();
            connectThread = null;
        }

        if(acceptThread == null)
        {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }

    public void startClient(BluetoothDevice device, UUID uuid)
    {
        Log.d(TAG, "Start-Client: Started");

        //Init Progress Dialog
        progressDialog = ProgressDialog.show(context,
                "Connecting Bluetooth", "Please Wait...", true);

        connectThread = new ConnectThread(device, uuid);
        connectThread.start();
    }

    public void connected(BluetoothSocket socket, BluetoothDevice device)
    {
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
    }

    //Write to ConnectedThread unsynchronized
    public void write(byte[] out)
    {
        connectedThread.write(out);
    }

    // Thread for listening to incoming connections
    private class AcceptThread extends Thread
    {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread()
        {
            BluetoothServerSocket tmp = null;

            try
            {
                tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, MY_UUID_INSECURE);
                Log.d(TAG, "AcceptThread: Setting up Server using: " + MY_UUID_INSECURE);
            }
            catch (IOException e)
            {
                Log.d(TAG, "AcceptThread: IOException:" + e.getMessage());
            }

            serverSocket = tmp;
        }

        public void run()
        {
            Log.d(TAG, "AcceptThread-Run: AcceptThread Running");
            BluetoothSocket socket = null;

            try
            {
                Log.d(TAG, "AcceptThread-Run: RFCOM server socket start....");
                socket = serverSocket.accept();
                Log.d(TAG, "AcceptThread-Run: Server socket accepted connection");
            }
            catch(IOException e)
            {
                Log.d(TAG, "AcceptThread-Run: IOException:" + e.getMessage());
            }

            if(socket != null)
                connected(socket, device);
        }

        public void cancel()
        {
            Log.d(TAG, "AcceptThread-Cancel: Canceling AcceptThread");

            try
            {
                serverSocket.close();
            }
            catch(IOException e)
            {
                Log.d(TAG, "AcceptThread-Cancel: IOException:" + e.getMessage());
            }
        }
    }

    //Thread for attempting to make outgoing connections
    private class ConnectThread extends Thread
    {
        private BluetoothSocket socket;

        public ConnectThread(BluetoothDevice _device, UUID uuid)
        {
            Log.d(TAG, "ConnectThread: started");
            device = _device;
            deviceUUID = uuid;
        }

        public void run()
        {
            BluetoothSocket tmp = null;
            Log.i(TAG, "run ConnectThread");

            try
            {
                tmp = device.createInsecureRfcommSocketToServiceRecord(deviceUUID);
            }
            catch (IOException e)
            {
                Log.d(TAG, "ConnectThread-Run: IOException:" + e.getMessage());
            }

            socket = tmp;

            //discovery will slow connection
            bluetoothAdapter.cancelDiscovery();

            //Connect to bluetooth socket
            try
            {
                socket.connect();
                Log.d(TAG, "ConnectThread-Run: ConnectThread connected");
            }
            catch (IOException e)
            {
                Log.d(TAG, "ConnectThread-Run: IOException:" + e.getMessage());
                //Close socket
                try
                {
                    socket.close();
                    Log.d(TAG, "ConnectThread-Run: Socket Closed");
                }
                catch (IOException e1)
                {
                    Log.d(TAG, "ConnectThread-Run: IOException:" + e1.getMessage());
                }
            }

            connected(socket, device);
        }

        public void cancel()
        {
            try
            {
                Log.d(TAG, "ConnectThread-Cancel: Closing Client Socket.");
                socket.close();
            }
            catch (IOException e)
            {
                Log.d(TAG, "ConnectThread-Cancel: IOException:" + e.getMessage());
            }
        }
    }

    private class ConnectedThread extends Thread
    {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket)
        {
            this.socket = socket;
            InputStream tmpInput = null;
            OutputStream tmpOutput = null;

            //Dismiss progress dialog when connection is established
            try
            {
                progressDialog.dismiss();
            }
            catch(NullPointerException e){ }

            try
            {
                tmpInput = socket.getInputStream();
                tmpOutput = socket.getOutputStream();
            }
            catch(IOException e)
            {
                Log.d(TAG, "ConnectedThread: IOException:" + e.getMessage());
            }

            inputStream = tmpInput;
            outputStream = tmpOutput;
        }

        public void run()
        {
            byte[] buffer = new byte[1024]; //buffer store for the stream
            int bytes; //bytes returned from read()

            //Listen to InputStream until exception
            while(true)
            {
                try
                {
                    bytes = inputStream.read(buffer);
                    String incomingMessage = new String(buffer, 0, bytes);
                    Log.d(TAG, "InputStream: " + incomingMessage);

                    Intent intent = new Intent("incomingMessage");
                    intent.putExtra("thomascasse.message", incomingMessage);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                }
                catch (IOException e)
                {
                    Log.d(TAG, "InputStream: IOException:" + e.getMessage());
                }
            }
        }

        public void write(byte[] bytes)
        {
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG, "Write: Writing to OutputStream: " + text);

            try
            {
                outputStream.write(bytes);
            }
            catch (IOException e)
            {
                Log.d(TAG, "ConnectedThread-Write: IOException:" + e.getMessage());
            }
        }

        public void cancel()
        {
            try
            {
                socket.close();
            }
            catch (IOException e)
            {
                Log.d(TAG, "ConnectedThread-Cancel: IOException:" + e.getMessage());
            }
        }
    }
}
















