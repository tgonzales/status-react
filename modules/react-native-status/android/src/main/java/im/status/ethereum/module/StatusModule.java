package im.status.ethereum.module;

import android.app.Activity;
import android.os.*;
import android.view.WindowManager;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebStorage;

import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.github.status_im.status_go.cmd.Statusgo;

import java.io.File;
import java.util.HashMap;
import java.util.UUID;

class StatusModule extends ReactContextBaseJavaModule implements LifecycleEventListener, ConnectorHandler {

    private static final String TAG = "StatusModule";

    private StatusConnector status = null;

    private HashMap<String, Callback> callbacks = new HashMap<>();

    StatusModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addLifecycleEventListener(this);
    }

    @Override
    public String getName() {
        return "Status";
    }

    @Override
    public void onHostResume() {  // Actvity `onResume`
        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            Log.d(TAG, "On host Activity doesn't exist");
            return;
        }
        if (status == null) {
            status = new StatusConnector(currentActivity, StatusService.class);
            status.registerHandler(this);
        }

        status.bindService();

        WritableMap params = Arguments.createMap();
        Log.d(TAG, "Send module.initialized event");
        params.putString("jsonEvent", "{\"type\":\"module.initialized\"}");
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("gethEvent", params);
    }

    @Override
    public void onHostPause() {  // Actvity `onPause`
        if (status != null) {
            status.unbindService();
        }
    }

    @Override
    public void onHostDestroy() {  // Actvity `onDestroy`
        if (status != null) {
            status.stopNode(null);
        }
    }

    @Override
    public void onConnectorConnected() {
    }

    @Override
    public void onConnectorDisconnected() {
    }

    @Override
    public boolean handleMessage(Message message) {

        Log.d(TAG, "Received message: " + message.toString());
        boolean isClaimed = true;
        Bundle bundle = message.getData();
        String callbackIdentifier = bundle.getString(StatusConnector.CALLBACK_IDENTIFIER);
        String data = bundle.getString("data");
        Callback callback = callbacks.remove(callbackIdentifier);
        switch (message.what) {
            case StatusMessages.MSG_START_NODE:
            case StatusMessages.MSG_STOP_NODE:
            case StatusMessages.MSG_LOGIN:
            case StatusMessages.MSG_CREATE_ACCOUNT:
            case StatusMessages.MSG_RECOVER_ACCOUNT:
            case StatusMessages.MSG_COMPLETE_TRANSACTION:
            case StatusMessages.MSG_JAIL_INIT:
            case StatusMessages.MSG_JAIL_PARSE:
            case StatusMessages.MSG_JAIL_CALL:
                if (callback == null) {
                    Log.d(TAG, "Could not find callback: " + callbackIdentifier);
                } else {
                    callback.invoke(data);
                }
                break;
            case StatusMessages.MSG_GETH_EVENT:
                String event = bundle.getString("event");
                WritableMap params = Arguments.createMap();
                params.putString("jsonEvent", event);
                getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("gethEvent", params);
                break;
            default:
                isClaimed = false;
        }

        return isClaimed;
    }

    private boolean checkAvailability() {

        Activity currentActivity = getCurrentActivity();
        if (currentActivity == null) {
            Log.d(TAG, "Activity doesn't exist");
            return false;
        }

        if (status == null) {
            Log.d(TAG, "Status connector is null");
            return false;
        }

        return true;
    }

    // Geth

    public void doStartNode() {

        Activity currentActivity = getCurrentActivity();

        File extStore = Environment.getExternalStorageDirectory();
        String dataFolder = extStore.exists() ?
                extStore.getAbsolutePath() + "/ethereum" :
                currentActivity.getApplicationInfo().dataDir + "/ethereum";
        Log.d(TAG, "Starting Geth node in folder: " + dataFolder);

        try {
            final File newFile = new File(dataFolder);
            // todo handle error?
            newFile.mkdir();
        } catch (Exception e) {
            Log.e(TAG, "error making folder: " + dataFolder, e);
        }

        Statusgo.StartNode(dataFolder);
        Log.d(TAG, "Geth node started");
        Log.w(TAG, "adding peer");

        Statusgo.AddPeer("enode://e19d89e6faf2772e2f250e9625478ee7f313fcc0bb5e9310d5d407371496d9d7d73ccecd9f226cc2a8be34484525f72ba9db9d26f0222f4efc3c6d9d995ee224@198.199.105.122:30303");
        Statusgo.AddPeer("enode://1ad53266faaa9258ae71eef4d162022ba0d39498e1a3488e6c65fd86e0fb528e2aa68ad0e199da69fd39f4a3a38e9e8e95ac53ba5cc7676dfeaacf5fd6c0ad27@139.59.212.114:30303");
    }

    @ReactMethod
    public void startNode(Callback callback) {
        Log.d(TAG, "startNode");
        if (!checkAvailability()) {
            callback.invoke(false);
            return;
        }

        doStartNode();

        callback.invoke(false);
    }

    @ReactMethod
    public void startNodeRPCServer() {
        Log.d(TAG, "startNodeRPCServer");
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }

        Statusgo.StartNodeRPCServer();
    }

    @ReactMethod
    public void stopNodeRPCServer() {
        Log.d(TAG, "stopNodeRPCServer");
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }

        Statusgo.StopNodeRPCServer();
    }

    @ReactMethod
    public void login(String address, String password, Callback callback) {
        Log.d(TAG, "login");
        if (!checkAvailability()) {
            callback.invoke(false);
            return;
        }
        String result = Statusgo.Login(address, password);

        callback.invoke(result);
    }

    @ReactMethod
    public void createAccount(String password, Callback callback) {
        Log.d(TAG, "createAccount");
        if (!checkAvailability()) {
            callback.invoke(false);
            return;
        }

        String res = Statusgo.CreateAccount(password);

        callback.invoke(res);
    }

    @ReactMethod
    public void recoverAccount(String passphrase, String password, Callback callback) {
        Log.d(TAG, "recoverAccount");
        if (!checkAvailability()) {
            callback.invoke(false);
            return;
        }

        String res = Statusgo.RecoverAccount(password, passphrase);

        callback.invoke(res);
    }

    private String createIdentifier() {
        return UUID.randomUUID().toString();
    }

    @ReactMethod
    public void completeTransaction(String hash, String password, Callback callback) {
        Log.d(TAG, "completeTransaction");
        if (!checkAvailability()) {
            callback.invoke(false);
            return;
        }
        String res = Statusgo.CompleteTransaction(hash, password);

        callback.invoke(res);
    }


    @ReactMethod
    public void discardTransaction(String id) {
        Log.d(TAG, "discardTransaction");
        if (!checkAvailability()) {
            return;
        }

        String res = Statusgo.DiscardTransaction(id);
    }

    // Jail

    @ReactMethod
    public void initJail(String js, Callback callback) {
        Log.d(TAG, "initJail");
        if (!checkAvailability()) {
            callback.invoke(false);
            return;
        }

        Statusgo.InitJail(js);

        callback.invoke(false);
    }

    @ReactMethod
    public void parseJail(String chatId, String js, Callback callback) {
        Log.d(TAG, "parseJail");
        if (!checkAvailability()) {
            callback.invoke(false);
            return;
        }

        String res = Statusgo.Parse(chatId, js);

        callback.invoke(res);
    }

    @ReactMethod
    public void callJail(String chatId, String path, String params, Callback callback) {
        Log.d(TAG, "callJail");
        if (!checkAvailability()) {
            callback.invoke(false);
            return;
        }

        String res = Statusgo.Call(chatId, path, params);

        callback.invoke(res);
    }

    @ReactMethod
    public void setAdjustResize() {
        Log.d(TAG, "setAdjustResize");
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        });
    }

    @ReactMethod
    public void setAdjustPan() {
        Log.d(TAG, "setAdjustPan");
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
            }
        });
    }

    @ReactMethod
    public void setSoftInputMode(final int mode) {
        Log.d(TAG, "setSoftInputMode");
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                activity.getWindow().setSoftInputMode(mode);
            }
        });
    }

    @SuppressWarnings("deprecation")
    @ReactMethod
    public void clearCookies() {
        Log.d(TAG, "clearCookies");
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } else {
            CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance(activity);
            cookieSyncManager.startSync();
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookie();
            cookieManager.removeSessionCookie();
            cookieSyncManager.stopSync();
            cookieSyncManager.sync();
        }
    }

    @ReactMethod
    public void clearStorageAPIs() {
        Log.d(TAG, "clearStorageAPIs");
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            return;
        }

        WebStorage storage = WebStorage.getInstance();
        if (storage != null) {
            storage.deleteAllData();
        }
    }
}
