package com.nuttawutmalee.RCTBluetoothSerial;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Set;
import javax.annotation.Nullable;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;
import android.util.Base64;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import zj.com.customize.sdk.Other;

import static com.nuttawutmalee.RCTBluetoothSerial.RCTBluetoothSerialPackage.TAG;

@SuppressWarnings("unused")
public class RCTBluetoothSerialModule extends ReactContextBaseJavaModule
        implements ActivityEventListener, LifecycleEventListener {

    // Debugging
    private static final boolean D = true;

    // Event names
    private static final String BT_ENABLED = "bluetoothEnabled";
    private static final String BT_DISABLED = "bluetoothDisabled";
    private static final String PR_SUCCESS = "pairingSuccess";
    private static final String PR_FAILED = "pairingFailed";
    private static final String UN_PR_SUCCESS = "unpairingSuccess";
    private static final String UN_PR_FAILED = "unpairingFailed";
    private static final String CONN_SUCCESS = "connectionSuccess";
    private static final String CONN_FAILED = "connectionFailed";
    private static final String CONN_LOST = "connectionLost";
    private static final String DEVICE_READ = "read";
    private static final String DATA_READ = "data";
    private static final String ERROR = "error";

    // Other stuff
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_PAIR_DEVICE = 2;

    // Members
    private BluetoothAdapter mBluetoothAdapter;
    private RCTBluetoothSerialService mBluetoothService;
    private ReactApplicationContext mReactContext;

    private StringBuffer mBuffer = new StringBuffer();

    // Promises
    private Promise mEnabledPromise;
    private Promise mConnectedPromise;
    private Promise mDeviceDiscoveryPromise;
    private Promise mPairDevicePromise;

    private String delimiter = "";

    public RCTBluetoothSerialModule(ReactApplicationContext reactContext) {
        super(reactContext);

        if (D) Log.d(TAG, "Bluetooth module started");

        mReactContext = reactContext;

        if (mBluetoothAdapter == null) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (mBluetoothService == null) {
            mBluetoothService = new RCTBluetoothSerialService(this);
        }

        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            sendEvent(BT_ENABLED, null);
        } else {
            sendEvent(BT_DISABLED, null);
        }

        mReactContext.addActivityEventListener(this);
        mReactContext.addLifecycleEventListener(this);
        registerBluetoothStateReceiver();
    }

    @Override
    public String getName() {
        return "RCTBluetoothSerial";
    }

    // @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (D) Log.d(TAG, "On activity result request: " + requestCode + ", result: " + resultCode);

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                if (D) Log.d(TAG, "User enabled Bluetooth");
                if (mEnabledPromise != null) {
                    mEnabledPromise.resolve(true);
                    mEnabledPromise = null;
                }
            } else {
                if (D) Log.d(TAG, "User did not enable Bluetooth");
                if (mEnabledPromise != null) {
                    mEnabledPromise.reject(new Exception("User did not enable Bluetooth"));
                    mEnabledPromise = null;
                }
            }
        }

        if (requestCode == REQUEST_PAIR_DEVICE) {
            if (resultCode == Activity.RESULT_OK) {
                if (D) Log.d(TAG, "Pairing ok");
            } else {
                if (D) Log.d(TAG, "Pairing failed");
            }
        }
    }

    // @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (D) Log.d(TAG, "On activity result request: " + requestCode + ", result: " + resultCode);

        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                if (D) Log.d(TAG, "User enabled Bluetooth");
                if (mEnabledPromise != null) {
                    mEnabledPromise.resolve(true);
                    mEnabledPromise = null;
                }
            } else {
                if (D) Log.d(TAG, "User did not enable Bluetooth");
                if (mEnabledPromise != null) {
                    mEnabledPromise.reject(new Exception("User did not enable Bluetooth"));
                    mEnabledPromise = null;
                }
            }
        }

        if (requestCode == REQUEST_PAIR_DEVICE) {
            if (resultCode == Activity.RESULT_OK) {
                if (D) Log.d(TAG, "Pairing ok");
            } else {
                if (D) Log.d(TAG, "Pairing failed");
            }
        }
    }

    // @Override
    public void onNewIntent(Intent intent) {
        if (D) Log.d(TAG, "On new intent");
    }

    @Override
    public void onHostResume() {
        if (D) Log.d(TAG, "Host resume");
    }

    @Override
    public void onHostPause() {
        if (D) Log.d(TAG, "Host pause");
    }

    @Override
    public void onHostDestroy() {
        if (D) Log.d(TAG, "Host destroy");
        mBluetoothService.stop();
    }

    @Override
    public void onCatalystInstanceDestroy() {
        if (D) Log.d(TAG, "Catalyst instance destroyed");
        super.onCatalystInstanceDestroy();
        mBluetoothService.stop();
    }

    /*******************************/
    /** Methods Available from JS **/
    /*******************************/

    /*************************************/
    /** Bluetooth state related methods **/
    /*************************************/

    @ReactMethod
    /**
     * Request user to enable bluetooth
     */
    public void requestEnable(Promise promise) {
        if (mBluetoothAdapter != null) {
            if (mBluetoothAdapter.isEnabled()) {
                // If bluetooth is already enabled resolve promise immediately
                promise.resolve(true);
            } else {
                // Start new intent if bluetooth is note enabled
                Activity activity = getCurrentActivity();
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

                if (activity != null) {
                    mEnabledPromise = promise;
                    activity.startActivityForResult(intent, REQUEST_ENABLE_BLUETOOTH);
                } else {
                    Exception e = new Exception("Cannot start activity");
                    Log.e(TAG, "Cannot start activity", e);
                    promise.reject(e);
                    onError(e);
                }
            }
        } else {
            rejectNullBluetoothAdapter(promise);
        }
    }

    @ReactMethod
    /**
     * Enable bluetooth
     */
    public void enable(Promise promise) {
        if (mBluetoothAdapter != null) {
            if (mBluetoothAdapter.isEnabled()) {
                if (D) Log.d(TAG, "Bluetooth enabled");
                promise.resolve(true);
            } else {
                try {
                    mBluetoothAdapter.enable();
                    if (D) Log.d(TAG, "Bluetooth enabled");
                    promise.resolve(true);
                } catch (Exception e) {
                    Log.e(TAG, "Cannot enable bluetooth");
                    promise.reject(e);
                    onError(e);
                }
            }
        } else {
            rejectNullBluetoothAdapter(promise);
        }
    }

    @ReactMethod
    /**
     * Disable bluetooth
     */
    public void disable(Promise promise) {
        if (mBluetoothAdapter != null) {
            if (!mBluetoothAdapter.isEnabled()) {
                if (D) Log.d(TAG, "Bluetooth disabled");
                promise.resolve(true);
            } else {
                try {
                    mBluetoothAdapter.disable();
                    if (D) Log.d(TAG, "Bluetooth disabled");
                    promise.resolve(true);
                } catch (Exception e) {
                    Log.e(TAG, "Cannot disable bluetooth");
                    promise.reject(e);
                    onError(e);
                }
            }
        } else {
            rejectNullBluetoothAdapter(promise);
        }
    }

    @ReactMethod
    /**
     * Check if bluetooth is enabled
     */
    public void isEnabled(Promise promise) {
        if (mBluetoothAdapter != null) {
            promise.resolve(mBluetoothAdapter.isEnabled());
        } else {
            rejectNullBluetoothAdapter(promise);
        }
    }

    @ReactMethod
    /**
     * Set string delimiter to split buffer when read from the device
     */
    public void withDelimiter(String delimiter, Promise promise) {
        if (D) Log.d(TAG, "Set delimiter to " + delimiter);
        this.delimiter = delimiter;
        promise.resolve(true);
    }

    /**************************************/
    /** Bluetooth device related methods **/
    /**************************************/

    @ReactMethod
    /**
     * List paired bluetooth devices
     */
    public void list(Promise promise) {
        if (D) Log.d(TAG, "List paired called");

        if (mBluetoothAdapter != null) {
            WritableArray deviceList = Arguments.createArray();
            Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();

            for (BluetoothDevice rawDevice : bondedDevices) {
                WritableMap device = deviceToWritableMap(rawDevice);
                deviceList.pushMap(device);
            }

            promise.resolve(deviceList);
        } else {
            rejectNullBluetoothAdapter(promise);
        }
    }

    @ReactMethod
    /**
     * Discover unpaired bluetooth devices
     */
    public void discoverUnpairedDevices(Promise promise) {
        if (D) Log.d(TAG, "Discover unpaired called");

        if (mBluetoothAdapter != null) {
            mDeviceDiscoveryPromise = promise;
            registerBluetoothDeviceDiscoveryReceiver();
            mBluetoothAdapter.startDiscovery();
        } else {
            rejectNullBluetoothAdapter(promise);
        }
    }

    @ReactMethod
    /**
     * Is discovering?
     */
    public void isDiscovering(Promise promise) {
        if (D) Log.d(TAG, "Is discovering called");

        if (mBluetoothAdapter != null) {
            promise.resolve(mBluetoothAdapter.isDiscovering());
        } else {
            rejectNullBluetoothAdapter(promise);
        }
    }


    @ReactMethod
    /**
     * Cancel discovery
     */
    public void cancelDiscovery(Promise promise) {
        if (D) Log.d(TAG, "Cancel discovery called");

        if (mBluetoothAdapter != null) {
            if (mBluetoothAdapter.isDiscovering()) {
                mBluetoothAdapter.cancelDiscovery();
            }
            promise.resolve(true);
        } else {
            rejectNullBluetoothAdapter(promise);
        }
    }

    @ReactMethod
    /**
     * Pair device
     */
    public void pairDevice(String id, Promise promise) {
        if (D) Log.d(TAG, "Pair device: " + id);

        if (mBluetoothAdapter != null) {
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(id);

            if (device != null) {
                mPairDevicePromise = promise;
                pairDevice(device);
            } else {
                promise.reject(new Exception("Could not pair device " + id));
                sendEvent(PR_FAILED, null);
            }
        } else {
            rejectNullBluetoothAdapter(promise);
        }
    }

    @ReactMethod
    /**
     * Unpair device
     */
    public void unpairDevice(String id, Promise promise) {
        if (D) Log.d(TAG, "Unpair device: " + id);

        if (mBluetoothAdapter != null) {
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(id);

            if (device != null) {
                mPairDevicePromise = promise;
                unpairDevice(device);
            } else {
                promise.reject(new Exception("Could not unpair device " + id));
                sendEvent(UN_PR_FAILED, null);
            }
        } else {
            rejectNullBluetoothAdapter(promise);
        }
    }

    /********************************/
    /** Connection related methods **/
    /********************************/

    @ReactMethod
    /**
     * Connect to device by id
     */
    public void connect(String id, Promise promise) {
        if (mBluetoothAdapter != null) {
            mConnectedPromise = promise;
            BluetoothDevice rawDevice = mBluetoothAdapter.getRemoteDevice(id);

            if (rawDevice != null) {
                mBluetoothService.connect(rawDevice);
                if (mConnectedPromise != null) {
                    WritableMap device = deviceToWritableMap(rawDevice);
                    mConnectedPromise.resolve(device);
                }
            } else {
                promise.reject(new Exception("Could not connect to " + id));
            }
        } else {
            rejectNullBluetoothAdapter(promise);
        }
    }

    @ReactMethod
    /**
     * Disconnect from device
     */
    public void disconnect(Promise promise) {
        mBluetoothService.stop();
        promise.resolve(true);
    }

    @ReactMethod
    /**
     * Check if device is connected
     */
    public void isConnected(Promise promise) {
        promise.resolve(mBluetoothService.isConnected());
    }

    /*********************/
    /** Write to device **/
    /*********************/

    @ReactMethod
    /**
     * Write to device over serial port
     */
    public void writeToDevice(String message, Promise promise) {
        if (D) Log.d(TAG, "Write " + message);
        byte[] data = Base64.decode(message, Base64.DEFAULT);
        mBluetoothService.write(data);
        promise.resolve(true);
    }

    @ReactMethod
    /**
     * Write base64 image string to device over serial port
     */
    public void writeBase64ImageToDevice(String imageBase64, Promise promise) {
        if (D) Log.d(TAG, "Write base64 image " + imageBase64);
        String base64Image = imageBase64.split(",")[1];
        Bitmap bitmap = base64ToBitmap(base64Image);
        byte[] data = POSPrintBitmap(bitmap, 384, 0);
        mBluetoothService.write(data);
        promise.resolve(true);
    }


    /**********************/
    /** Read from device **/
    /**********************/

    @ReactMethod
    /**
     * Read from device over serial port
     */
    public void readFromDevice(Promise promise) {
        if (D) Log.d(TAG, "Read");
        int length = mBuffer.length();
        String data = mBuffer.substring(0, length);
        mBuffer.delete(0, length);
        promise.resolve(data);
    }

    @ReactMethod
    public void readUntilDelimiter(String delimiter, Promise promise) {
        promise.resolve(readUntil(delimiter));
    }

    /***********/
    /** Other **/
    /***********/

    @ReactMethod
    /**
     * Clear data in buffer
     */
    public void clear(Promise promise) {
        mBuffer.setLength(0);
        promise.resolve(true);
    }

    @ReactMethod
    /**
     * Get length of data available to read
     */
    public void available(Promise promise) {
        promise.resolve(mBuffer.length());
    }

    @ReactMethod
    /**
     * Set bluetooth adapter name
     */
    public void setAdapterName(String newName, Promise promise) {
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.setName(newName);
            promise.resolve(mBluetoothAdapter.getName());
        } else {
            rejectNullBluetoothAdapter(promise);
        }
    }

    /****************************************/
    /** Methods available to whole package **/
    /****************************************/

    /**
     * Handle connection success
     * 
     * @param msg Additional message
     * @param connectedDevice Connected device
     */
    void onConnectionSuccess(String msg, BluetoothDevice connectedDevice) {
        WritableMap params = Arguments.createMap();
        WritableMap device  = deviceToWritableMap(connectedDevice);

        params.putMap("device", device);
        params.putString("message", msg);
        sendEvent(CONN_SUCCESS, params);

        if (mConnectedPromise != null) {
            mConnectedPromise.resolve(params);
        }
    }

    /**
     * handle connection failure
     * 
     * @param msg Additional message
     * @param connectedDevice Connected device
     */
    void onConnectionFailed(String msg, BluetoothDevice connectedDevice) {
        WritableMap params = Arguments.createMap();
        WritableMap device  = deviceToWritableMap(connectedDevice);

        params.putMap("device", device);
        params.putString("message", msg);
        sendEvent(CONN_FAILED, params);

        if (mConnectedPromise != null) {
            mConnectedPromise.reject(new Exception(msg));
        }
    }

    /**
     * Handle lost connection
     * 
     * @param msg Message
     * @param connectedDevice Connected device
     */
    void onConnectionLost(String msg, BluetoothDevice connectedDevice) {
        WritableMap params = Arguments.createMap();
        WritableMap device  = deviceToWritableMap(connectedDevice);

        params.putMap("device", device);
        params.putString("message", msg);
        sendEvent(CONN_LOST, params);

        if (mConnectedPromise != null) {
            mConnectedPromise.reject(new Exception(msg));
        }
    }

    /**
     * Handle error
     * 
     * @param e Exception
     */
    void onError(Exception e) {
        WritableMap params = Arguments.createMap();
        params.putString("message", e.getMessage());
        sendEvent(ERROR, params);
    }

    /**
     * Handle read
     * 
     * @param data Message
     */
    void onData(String data) {
        mBuffer.append(data);

        String completeData = readUntil(this.delimiter);
        if (completeData != null && completeData.length() > 0) {
            WritableMap params = Arguments.createMap();
            params.putString("data", completeData);
            sendEvent(DEVICE_READ, params);
            sendEvent(DATA_READ, params);
        }
    }

    private String readUntil(String delimiter) {
        String data = "";
        int index = mBuffer.indexOf(delimiter, 0);
        if (index > -1) {
            data = mBuffer.substring(0, index + delimiter.length());
            mBuffer.delete(0, index + delimiter.length());
        }
        return data;
    }

    /*********************/
    /** Private methods **/
    /*********************/

    /**
     * Check if is api level 19 or above
     * 
     * @return is above api level 19
     */
    private boolean isKitKatOrAbove() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    /**
     * Send event to javascript
     * 
     * @param eventName Name of the event
     * @param params    Additional params
     */
    private void sendEvent(String eventName, @Nullable WritableMap params) {
        if (mReactContext.hasActiveCatalystInstance()) {
            if (D) Log.d(TAG, "Sending event: " + eventName);
            mReactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
        }
    }

    /**
     * Convert BluetoothDevice into WritableMap
     * 
     * @param device Bluetooth device
     */
    private WritableMap deviceToWritableMap(BluetoothDevice device) {
        if (D) Log.d(TAG, "device" + device.toString());

        WritableMap params = Arguments.createMap();

        if (device != null) {
            params.putString("name", device.getName());
            params.putString("address", device.getAddress());
            params.putString("id", device.getAddress());

            if (device.getBluetoothClass() != null) {
                params.putInt("class", device.getBluetoothClass().getDeviceClass());
            }
        }

        return params;
    }

    /**
     * Pair device before kitkat
     * 
     * @param device Device
     */
    private void pairDevice(BluetoothDevice device) {
        try {
            if (D) Log.d(TAG, "Start Pairing...");
            Method m = device.getClass().getMethod("createBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
            registerDevicePairingReceiver(device, BluetoothDevice.BOND_BONDED);
        } catch (Exception e) {
            Log.e(TAG, "Cannot pair device", e);
            if (mPairDevicePromise != null) {
                mPairDevicePromise.reject(e);
                mPairDevicePromise = null;
            }
            sendEvent(PR_FAILED, null);
            onError(e);
        }
    }

    /**
     * Unpair device
     * 
     * @param device Device
     */
    private void unpairDevice(BluetoothDevice device) {
        try {
            if (D) Log.d(TAG, "Start Unpairing...");
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
            registerDevicePairingReceiver(device, BluetoothDevice.BOND_NONE);
        } catch (Exception e) {
            Log.e(TAG, "Cannot unpair device", e);
            if (mPairDevicePromise != null) {
                mPairDevicePromise.reject(e);
                mPairDevicePromise = null;
            }
            sendEvent(UN_PR_FAILED, null);
            onError(e);
        }
    }

    /**
     * Register receiver for device pairing
     * 
     * @param rawDevice Bluetooth device
     * @param requiredState State that we require
     */
    private void registerDevicePairingReceiver(final BluetoothDevice rawDevice, final int requiredState) {
        final WritableMap device = deviceToWritableMap(rawDevice);
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

        final BroadcastReceiver devicePairingReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                    final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                    final int prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                            BluetoothDevice.ERROR);

                    if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                        if (D) Log.d(TAG, "Device paired");
                        sendEvent(PR_SUCCESS, device);
                        if (mPairDevicePromise != null) {
                            mPairDevicePromise.resolve(device);
                            mPairDevicePromise = null;
                        }
                        try {
                            mReactContext.unregisterReceiver(this);
                        } catch (Exception e) {
                            Log.e(TAG, "Cannot unregister receiver", e);
                            onError(e);
                        }
                    } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED) {
                        if (D) Log.d(TAG, "Device unpaired");
                        sendEvent(UN_PR_SUCCESS, device);
                        if (mPairDevicePromise != null) {
                            mPairDevicePromise.resolve(device);
                            mPairDevicePromise = null;
                        }
                        try {
                            mReactContext.unregisterReceiver(this);
                        } catch (Exception e) {
                            Log.e(TAG, "Cannot unregister receiver", e);
                            onError(e);
                        }
                    }
                }
            }
        };

        mReactContext.registerReceiver(devicePairingReceiver, intentFilter);
    }

    /**
     * Register receiver for bluetooth device discovery
     */
    private void registerBluetoothDeviceDiscoveryReceiver() {
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        final BroadcastReceiver deviceDiscoveryReceiver = new BroadcastReceiver() {
            private WritableArray unpairedDevices = Arguments.createArray();

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (D) Log.d(TAG, "onReceive called");

                if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                    if (D) Log.d(TAG, "Discovery started");
                } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice rawDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    if (D) Log.d(TAG, "Discovery extra device (device id: " + rawDevice.getAddress() + ")");

                    WritableMap device = deviceToWritableMap(rawDevice);
                    unpairedDevices.pushMap(device);
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    if (D) Log.d(TAG, "Discovery finished");

                    if (mDeviceDiscoveryPromise != null) {
                        mDeviceDiscoveryPromise.resolve(unpairedDevices);
                        mDeviceDiscoveryPromise = null;
                    }

                    try {
                        mReactContext.unregisterReceiver(this);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to unregister receiver", e);
                        onError(e);
                    }
                }
            }
        };

        mReactContext.registerReceiver(deviceDiscoveryReceiver, intentFilter);
    }

    /**
     * Register receiver for bluetooth state change
     */
    private void registerBluetoothStateReceiver() {
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        if (D) Log.d(TAG, "Bluetooth was disabled");
                        sendEvent(BT_DISABLED, null);
                        break;
                    case BluetoothAdapter.STATE_ON:
                        if (D) Log.d(TAG, "Bluetooth was enabled");
                        sendEvent(BT_ENABLED, null);
                        break;
                    default:
                        break;
                    }
                }
            }
        };

        mReactContext.registerReceiver(bluetoothStateReceiver, intentFilter);
    }

    /**
     * Return reject promise for null bluetooth adapter
     * @param promise
     */
    private void rejectNullBluetoothAdapter(Promise promise) {
        Exception e = new Exception("Bluetooth adapter not found");
        Log.e(TAG, "Bluetooth adapter not found");
        promise.reject(e);
        onError(e);
    }

    /**
     * Convert base64 string to bitmap
     * @param content Base64 string
     */
    private Bitmap base64ToBitmap(String content) {
        byte[] imageAsBytes = Base64.decode(content.getBytes(), Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(imageAsBytes, 0, imageAsBytes.length);
    }

    /**
     * Convert bitmap to base64 string to
     * @param bitmap Bitmap image
     */
    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream .toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    /**
     * Print bitmap bytes
     * @param mBitmap
     * @param nWidth
     * @param nMode
     */
    private byte[] POSPrintBitmap(Bitmap mBitmap, int nWidth, int nMode) {
        int width = (nWidth + 7) / 8 * 8;
        int height = mBitmap.getHeight() * width / mBitmap.getWidth();
        height = (height + 7) / 8 * 8;

        Bitmap rszBitmap = mBitmap;
        if(mBitmap.getWidth() != width) {
            rszBitmap = Other.resizeImage(mBitmap, width, height);
        }

        Bitmap grayBitmap = Other.toGrayscale(rszBitmap);

        byte[] dithered = Other.thresholdToBWPic(grayBitmap);
        byte[] data = Other.eachLinePixToCmd(dithered, width, nMode);

        return data;
    }
}