package com.mahesh.togethertonight;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single-Activity wrapper around the Together Tonight web app.
 *
 * The entire UI is the self-contained web app bundled at
 * assets/index.html (couples activities, questions and After Dark,
 * all embedded; fully offline, no account, no network). This Activity
 * hosts it in a full-screen WebView, and adds the v1.1 Bluetooth
 * partner link: a JS bridge (window.TogetherBridge), classic-discovery
 * handling, and a discreet suggestion notification that opens straight
 * to the suggested activity without ever naming it on the lock screen.
 */
public class MainActivity extends Activity implements LinkManager.Listener {

    private static final int REQ_PERMS = 101;
    private static final int REQ_DISCOVERABLE = 102;

    private WebView webView;
    private String pendingActivityId;
    private boolean pendingIsDark;
    private boolean pageLoaded = false;

    private final List<JSONObject> foundDevices = new ArrayList<>();
    private final Set<String> foundAddresses = new HashSet<>();
    private String pendingLinkAddress;
    private String pendingLinkName;
    private String pendingLinkCode;

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (dev == null || dev.getAddress() == null) return;
                if (!foundAddresses.add(dev.getAddress())) return;
                try {
                    JSONObject o = new JSONObject();
                    o.put("address", dev.getAddress());
                    String name = null;
                    try { name = dev.getName(); } catch (Exception ignored) { }
                    o.put("name", name == null ? dev.getAddress() : name);
                    try { o.put("bonded", dev.getBondState() == BluetoothDevice.BOND_BONDED); }
                    catch (Exception ignored) { o.put("bonded", false); }
                    foundDevices.add(o);
                } catch (Exception ignored) { }
                sendDevicesToJs();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (LinkManager.listener != null) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { callJs("window.onDiscoveryFinished&&window.onDiscoveryFinished()"); }
                    });
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                if (dev != null && state == BluetoothDevice.BOND_BONDED
                        && pendingLinkAddress != null && pendingLinkAddress.equals(dev.getAddress())) {
                    String addr = pendingLinkAddress;
                    String name = pendingLinkName;
                    String code = pendingLinkCode;
                    pendingLinkAddress = null;
                    LinkManager.sendHello(MainActivity.this, addr, name, code);
                }
            }
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinkManager.listener = this;
        LinkManager.ensureChannels(this);

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        // v1.2: index.html loads data.json from the same local asset folder.
        // Local-file XHR only — still no network, no remote content.
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.addJavascriptInterface(new TogetherBridge(this), "TogetherBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                deliverPendingActivity();
                LinkManager.notifyStatus(MainActivity.this);
            }
        });

        handleIntent(getIntent());

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html");
        }

        requestAllPermissions();
        registerBtReceiver();
    }

    private void registerBtReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        try { registerReceiver(btReceiver, f); } catch (Exception ignored) { }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        if (pageLoaded) deliverPendingActivity();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String id = intent.getStringExtra("tt_activity_id");
        if (id != null && !id.isEmpty()) {
            pendingActivityId = id;
            pendingIsDark = intent.getBooleanExtra("tt_is_dark", false);
        }
    }

    private void deliverPendingActivity() {
        if (pendingActivityId == null || webView == null) return;
        final String id = pendingActivityId;
        final boolean isDark = pendingIsDark;
        pendingActivityId = null;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                callJs("window.openPartnerActivity&&window.openPartnerActivity('"
                        + id.replace("'", "") + "'," + (isDark ? "true" : "false") + ")");
            }
        });
    }

    // ---- permissions -----------------------------------------------------

    public void requestAllPermissions() {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.BLUETOOTH);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.BLUETOOTH_ADMIN);
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), REQ_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        LinkManager.notifyStatus(this);
    }

    // ---- discovery / linking, called from the JS bridge ------------------

    public void startDiscovery() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            callJs("window.onSendResult&&window.onSendResult(false,'This phone has no Bluetooth.')");
            return;
        }
        if (!adapter.isEnabled()) {
            callJs("window.onSendResult&&window.onSendResult(false,'Bluetooth is off — turn it on first.')");
            return;
        }
        if (!LinkManager.hasScanPermission(this)) {
            requestAllPermissions();
            callJs("window.onSendResult&&window.onSendResult(false,'Please allow the Bluetooth permission, then tap Find again.')");
            return;
        }
        foundDevices.clear();
        foundAddresses.clear();
        // Also list already-bonded devices straight away — tapping a
        // bonded partner links even without a fresh scan.
        try {
            for (BluetoothDevice dev : adapter.getBondedDevices()) {
                if (dev == null || dev.getAddress() == null) continue;
                if (!foundAddresses.add(dev.getAddress())) continue;
                JSONObject o = new JSONObject();
                o.put("address", dev.getAddress());
                String name = null;
                try { name = dev.getName(); } catch (Exception ignored) { }
                o.put("name", name == null ? dev.getAddress() : name);
                o.put("bonded", true);
                foundDevices.add(o);
            }
        } catch (Exception ignored) { }
        sendDevicesToJs();
        try {
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
            adapter.startDiscovery();
        } catch (Exception e) {
            callJs("window.onSendResult&&window.onSendResult(false,'Could not start Bluetooth scan.')");
        }
    }

    public void makeDiscoverable() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        try {
            startActivityForResult(intent, REQ_DISCOVERABLE);
        } catch (Exception e) {
            callJs("window.onSendResult&&window.onSendResult(false,'Could not open discoverable mode.')");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DISCOVERABLE) {
            callJs("window.onSendResult&&window.onSendResult("
                    + (resultCode > 0 ? "true" : "false") + ",'"
                    + (resultCode > 0 ? "You are discoverable for 5 minutes — ask your partner to tap Find partner." : "Discoverable mode was not turned on.") + "')");
        }
    }

    public void linkDevice(String address) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || address == null) return;
        String name = address;
        for (JSONObject o : foundDevices) {
            if (address.equals(o.optString("address"))) { name = o.optString("name", address); break; }
        }
        // 4-digit code for the pair to compare out loud.
        String code = String.format("%04d", (int) (Math.random() * 10000));
        pendingLinkCode = code;
        final String codeJs = code;
        runOnUiThread(new Runnable() {
            @Override public void run() { callJs("window.onLinkCode&&window.onLinkCode('" + codeJs + "')"); }
        });
        BluetoothDevice dev;
        try {
            dev = adapter.getRemoteDevice(address);
        } catch (Exception e) {
            return;
        }
        boolean bonded = false;
        try { bonded = dev.getBondState() == BluetoothDevice.BOND_BONDED; } catch (Exception ignored) { }
        if (bonded) {
            LinkManager.sendHello(this, address, name, code);
        } else {
            pendingLinkAddress = address;
            pendingLinkName = name;
            try {
                dev.createBond();
            } catch (Exception e) {
                pendingLinkAddress = null;
                callJs("window.onSendResult&&window.onSendResult(false,'Could not start pairing — try again from the list.')");
            }
        }
    }

    public void sendSuggestion(String activityJson) {
        // Handled in TogetherBridge; kept for symmetry / future use.
        new TogetherBridge(this).sendSuggestion(activityJson);
    }

    // ---- link service ------------------------------------------------------

    public void startLinkService() {
        if (!LinkManager.isLinked(this)) return;
        try {
            Intent i = new Intent(this, LinkService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) { }
    }

    public void stopLinkService() {
        try { stopService(new Intent(this, LinkService.class)); } catch (Exception ignored) { }
        LinkManager.stopServer();
    }

    // ---- LinkManager.Listener -> JS callbacks ------------------------------

    private void callJs(final String js) {
        if (webView == null) return;
        runOnUiThread(new Runnable() {
            @Override public void run() {
                try { webView.evaluateJavascript(js, null); } catch (Exception ignored) { }
            }
        });
    }

    private static String jsString(String s) {
        if (s == null) return "''";
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ") + "'";
    }

    @Override
    public void onLinkStatusChanged(final String json) {
        callJs("window.onLinkStatusChanged&&window.onLinkStatusChanged(" + json + ")");
    }

    @Override
    public void onSuggestionReceived(final String json) {
        callJs("window.onPartnerSuggestion&&window.onPartnerSuggestion(" + json + ")");
    }

    @Override
    public void onDevicesChanged(final String json) {
        callJs("window.onDevicesFound&&window.onDevicesFound(" + json + ")");
    }

    @Override
    public void onDiscoveryFinished() {
        callJs("window.onDiscoveryFinished&&window.onDiscoveryFinished()");
    }

    @Override
    public void onSendResult(final boolean ok, final String message) {
        callJs("window.onSendResult&&window.onSendResult(" + (ok ? "true" : "false") + "," + jsString(message) + ")");
    }

    @Override
    public void onLinkCode(final String code) {
        callJs("window.onLinkCode&&window.onLinkCode('" + code + "')");
    }

    @Override
    public void onNearbyResult(final boolean nearby) {
        callJs("window.onNearbyResult&&window.onNearbyResult(" + (nearby ? "true" : "false") + ")");
    }

    private void sendDevicesToJs() {
        try {
            JSONArray arr = new JSONArray();
            for (JSONObject o : foundDevices) arr.put(o);
            JSONObject wrap = new JSONObject();
            wrap.put("devices", arr);
            final String json = wrap.toString();
            runOnUiThread(new Runnable() {
                @Override public void run() { callJs("window.onDevicesFound&&window.onDevicesFound(" + json + ")"); }
            });
        } catch (Exception ignored) { }
    }

    // ---- lifecycle ---------------------------------------------------------

    @Override
    protected void onResume() {
        super.onResume();
        LinkManager.listener = this;
        if (webView != null) webView.onResume();
        if (LinkManager.isLinked(this)) {
            LinkManager.startServer(this);
            startLinkService();
        }
        LinkManager.notifyStatus(this);
    }

    @Override
    protected void onPause() {
        // v1.2: leaving the app locks After Dark again — the PIN is
        // required on the next entry, even if the page was still open.
        callJs("window.onAppBackground&&window.onAppBackground()");
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(btReceiver); } catch (Exception ignored) { }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (webView != null) webView.restoreState(savedInstanceState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
