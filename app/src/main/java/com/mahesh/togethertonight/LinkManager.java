package com.mahesh.togethertonight;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Bluetooth Classic (RFCOMM) partner link for Together Tonight.
 *
 * Two phones running the same APK link once. After that, either phone can
 * send a one-line JSON suggestion to the other whenever they are in
 * Bluetooth range (~10 m). No internet, no Play Services, no account.
 *
 * The link is stored in SharedPreferences (partner address + name + date).
 * The server socket listens only while the app / LinkService is alive;
 * Android may pause it if the phone is locked for a long time — the Link
 * screen says so honestly, and reopening the app restarts listening.
 */
public class LinkManager {

    public static final UUID APP_UUID =
            UUID.fromString("8a3f1c2e-7b4a-4d9e-9f2a-1c5e6d7a8b9c");
    public static final String SERVICE_NAME = "TogetherTonight";
    public static final String PREFS = "tt_link";
    public static final String KEY_ADDR = "partner_address";
    public static final String KEY_NAME = "partner_name";
    public static final String KEY_AT = "linked_at";
    public static final String KEY_MY_NAME = "my_name";
    public static final String KEY_CODE = "pending_code";

    public static final String CHANNEL_SUGGEST = "tt_suggest";
    public static final String CHANNEL_SERVICE = "tt_service";
    public static final int NOTIF_SUGGEST_ID = 1001;
    public static final int NOTIF_SERVICE_ID = 1002;

    public interface Listener {
        void onLinkStatusChanged(String json);
        void onSuggestionReceived(String json);
        void onDevicesChanged(String json);
        void onDiscoveryFinished();
        void onSendResult(boolean ok, String message);
        void onLinkCode(String code);
        void onNearbyResult(boolean nearby);
    }

    public static volatile Listener listener;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile BluetoothServerSocket serverSocket;
    private static volatile Thread serverThread;
    private static volatile boolean serverRunning = false;

    private static void post(Runnable r) { MAIN.post(r); }

    // ---- stored link -----------------------------------------------------

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isLinked(Context ctx) {
        return prefs(ctx).getString(KEY_ADDR, null) != null;
    }

    public static String partnerAddress(Context ctx) {
        return prefs(ctx).getString(KEY_ADDR, null);
    }

    public static String partnerName(Context ctx) {
        return prefs(ctx).getString(KEY_NAME, null);
    }

    public static String myName(Context ctx) {
        String n = prefs(ctx).getString(KEY_MY_NAME, null);
        if (n != null && !n.trim().isEmpty()) return n.trim();
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a != null && a.getName() != null) return a.getName();
        } catch (Exception ignored) { }
        return "Partner";
    }

    public static void setMyName(Context ctx, String name) {
        prefs(ctx).edit().putString(KEY_MY_NAME, name == null ? "" : name.trim()).apply();
    }

    public static void storeLink(Context ctx, String address, String name) {
        prefs(ctx).edit()
                .putString(KEY_ADDR, address)
                .putString(KEY_NAME, name == null ? address : name)
                .putLong(KEY_AT, System.currentTimeMillis())
                .apply();
        notifyStatus(ctx);
    }

    public static void clearLink(Context ctx) {
        prefs(ctx).edit().remove(KEY_ADDR).remove(KEY_NAME).remove(KEY_AT).apply();
        stopServer();
        notifyStatus(ctx);
    }

    public static String getLinkStatusJson(Context ctx) {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            JSONObject o = new JSONObject();
            o.put("linked", isLinked(ctx));
            o.put("partnerName", partnerName(ctx) == null ? "" : partnerName(ctx));
            o.put("partnerAddress", partnerAddress(ctx) == null ? "" : partnerAddress(ctx));
            o.put("linkedAt", prefs(ctx).getLong(KEY_AT, 0));
            o.put("myName", myName(ctx));
            o.put("bluetoothOn", a != null && a.isEnabled());
            o.put("bluetoothSupported", a != null);
            o.put("serverRunning", serverRunning);
            return o.toString();
        } catch (Exception e) {
            return "{\"linked\":false,\"partnerName\":\"\",\"partnerAddress\":\"\",\"linkedAt\":0,\"myName\":\"Partner\",\"bluetoothOn\":false,\"bluetoothSupported\":false,\"serverRunning\":false}";
        }
    }

    public static void notifyStatus(final Context ctx) {
        final String json = getLinkStatusJson(ctx);
        if (listener != null) post(new Runnable() {
            @Override public void run() {
                if (listener != null) listener.onLinkStatusChanged(json);
            }
        });
    }

    // ---- permissions -----------------------------------------------------

    public static boolean hasConnectPermission(Context ctx) {
        if (Build.VERSION.SDK_INT >= 31) {
            return ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static boolean hasScanPermission(Context ctx) {
        if (Build.VERSION.SDK_INT >= 31) {
            return ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    // ---- server: listen for incoming hello / suggest ---------------------

    public static synchronized void startServer(final Context ctx) {
        if (serverRunning) return;
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return;
        if (!hasConnectPermission(ctx)) return;
        serverRunning = true;
        serverThread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID);
                } catch (Exception e) {
                    serverRunning = false;
                    return;
                }
                while (serverRunning) {
                    BluetoothSocket socket = null;
                    try {
                        socket = serverSocket.accept();
                    } catch (IOException e) {
                        break;
                    }
                    if (socket == null) continue;
                    final BluetoothSocket s = socket;
                    new Thread(new Runnable() {
                        @Override public void run() { handleConnection(ctx, s); }
                    }).start();
                }
            }
        }, "tt-bt-server");
        serverThread.start();
    }

    public static synchronized void stopServer() {
        serverRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) { }
        serverSocket = null;
    }

    private static void handleConnection(Context ctx, BluetoothSocket socket) {
        String remoteAddr = null;
        String remoteName = null;
        try {
            BluetoothDevice dev = socket.getRemoteDevice();
            if (dev != null) {
                remoteAddr = dev.getAddress();
                try { remoteName = dev.getName(); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    JSONObject msg = new JSONObject(line);
                    String type = msg.optString("type", "");
                    if ("hello".equals(type)) {
                        // A bonded Together Tonight device is saying hello.
                        // Bonding is the trust step; accept and store the link.
                        String sender = msg.optString("sender", remoteName == null ? "Partner" : remoteName);
                        if (remoteAddr != null) storeLink(ctx, remoteAddr, sender);
                        JSONObject ack = new JSONObject();
                        ack.put("type", "ack");
                        ack.put("app", "together-tonight");
                        ack.put("sender", myName(ctx));
                        out.write((ack.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    } else if ("suggest".equals(type)) {
                        // Only accept suggestions from the linked partner (or,
                        // if not linked yet, from a bonded device that just
                        // said hello in this same session — handled above).
                        String linkedAddr = partnerAddress(ctx);
                        if (linkedAddr == null || (remoteAddr != null && !linkedAddr.equals(remoteAddr))) {
                            // Unknown sender: ignore silently.
                            continue;
                        }
                        final String payload = msg.toString();
                        postSuggestionNotification(ctx, msg);
                        if (listener != null) post(new Runnable() {
                            @Override public void run() {
                                if (listener != null) listener.onSuggestionReceived(payload);
                            }
                        });
                    } else if ("ack".equals(type)) {
                        // Ack to our hello is handled on the client side.
                    }
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) {
        } finally {
            try { socket.close(); } catch (Exception ignored) { }
        }
    }

    // ---- client: send ------------------------------------------------------

    /** Send a one-line JSON message to the linked partner. Runs off the UI thread. */
    public static void sendJson(final Context ctx, final String json, final boolean expectAck) {
        new Thread(new Runnable() {
            @Override public void run() {
                final String addr = partnerAddress(ctx);
                if (addr == null) {
                    sendResult(false, "Not linked yet — link your partner first.");
                    return;
                }
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null || !adapter.isEnabled()) {
                    sendResult(false, "Bluetooth is off.");
                    return;
                }
                if (!hasConnectPermission(ctx)) {
                    sendResult(false, "Bluetooth permission not granted.");
                    return;
                }
                BluetoothSocket socket = null;
                try {
                    BluetoothDevice dev = adapter.getRemoteDevice(addr);
                    socket = dev.createRfcommSocketToServiceRecord(APP_UUID);
                    adapter.cancelDiscovery();
                    socket.connect();
                    OutputStream out = socket.getOutputStream();
                    out.write((json + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    sendResult(true, "Sent to your partner.");
                } catch (Exception e) {
                    sendResult(false, "Could not reach your partner — are they nearby with Bluetooth on?");
                } finally {
                    try { if (socket != null) socket.close(); } catch (Exception ignored) { }
                }
            }
        }, "tt-bt-send").start();
    }

    /** Link handshake: open RFCOMM to the chosen device, send hello + code, wait for ack. */
    public static void sendHello(final Context ctx, final String address, final String deviceName, final String code) {
        new Thread(new Runnable() {
            @Override public void run() {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null || !adapter.isEnabled()) {
                    sendResult(false, "Bluetooth is off.");
                    return;
                }
                if (!hasConnectPermission(ctx)) {
                    sendResult(false, "Bluetooth permission not granted.");
                    return;
                }
                BluetoothSocket socket = null;
                try {
                    BluetoothDevice dev = adapter.getRemoteDevice(address);
                    socket = dev.createRfcommSocketToServiceRecord(APP_UUID);
                    adapter.cancelDiscovery();
                    socket.connect();
                    JSONObject hello = new JSONObject();
                    hello.put("type", "hello");
                    hello.put("app", "together-tonight");
                    hello.put("sender", myName(ctx));
                    hello.put("code", code);
                    OutputStream out = socket.getOutputStream();
                    out.write((hello.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    // Wait briefly for the ack (the other phone replies at once).
                    // BluetoothSocket has no setSoTimeout, so poll available().
                    try {
                        String line = null;
                        for (int i = 0; i < 80; i++) {
                            if (socket.getInputStream().available() > 0) {
                                BufferedReader in = new BufferedReader(
                                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                                line = in.readLine();
                                break;
                            }
                            try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
                        }
                        if (line != null && line.contains("\"ack\"")) {
                            storeLink(ctx, address, deviceName == null ? address : deviceName);
                            sendResult(true, "Linked! You can now send ideas to each other.");
                            return;
                        }
                    } catch (Exception ignored) { }
                    // No ack read, but the hello may still have landed (the
                    // receiver stores the link on hello). Treat a successful
                    // connect + write as linked if the receiver bonded us.
                    // Check by trying a short reconnect is unreliable, so we
                    // store the link locally and let the receiver do the same.
                    storeLink(ctx, address, deviceName == null ? address : deviceName);
                    sendResult(true, "Link request sent — ask your partner to open the Link screen to confirm.");
                } catch (Exception e) {
                    sendResult(false, "Could not connect to that phone — make sure it is discoverable and nearby.");
                } finally {
                    try { if (socket != null) socket.close(); } catch (Exception ignored) { }
                }
            }
        }, "tt-bt-hello").start();
    }

    /** On-demand nearby check: a short connect attempt to the stored partner. */
    public static void checkNearby(final Context ctx) {
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean[] nearby = {false};
                String addr = partnerAddress(ctx);
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (addr != null && adapter != null && adapter.isEnabled() && hasConnectPermission(ctx)) {
                    BluetoothSocket socket = null;
                    try {
                        BluetoothDevice dev = adapter.getRemoteDevice(addr);
                        socket = dev.createRfcommSocketToServiceRecord(APP_UUID);
                        adapter.cancelDiscovery();
                        socket.connect();
                        nearby[0] = true;
                    } catch (Exception ignored) {
                        nearby[0] = false;
                    } finally {
                        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
                    }
                }
                final boolean result = nearby[0];
                if (listener != null) post(new Runnable() {
                    @Override public void run() {
                        if (listener != null) listener.onNearbyResult(result);
                    }
                });
            }
        }, "tt-bt-nearby").start();
    }

    private static void sendResult(final boolean ok, final String message) {
        if (listener != null) post(new Runnable() {
            @Override public void run() {
                if (listener != null) listener.onSendResult(ok, message);
            }
        });
    }

    // ---- discreet notification --------------------------------------------

    public static void ensureChannels(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel suggest = new NotificationChannel(
                    CHANNEL_SUGGEST, "Partner ideas",
                    NotificationManager.IMPORTANCE_DEFAULT);
            suggest.setDescription("A new idea from your partner");
            suggest.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            suggest.setShowBadge(false);
            nm.createNotificationChannel(suggest);

            NotificationChannel service = new NotificationChannel(
                    CHANNEL_SERVICE, "Partner link",
                    NotificationManager.IMPORTANCE_LOW);
            service.setDescription("Keeps the partner link listening while linked");
            service.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            service.setShowBadge(false);
            nm.createNotificationChannel(service);
        }
    }

    /**
     * The suggestion notification is ALWAYS generic: title "Together Tonight",
     * text "You have a new idea waiting — open to see". It never contains
     * the activity title, category, or question text — not even for
     * non-spicy items. VISIBILITY_SECRET keeps it off the lock screen.
     */
    public static void postSuggestionNotification(Context ctx, JSONObject msg) {
        ensureChannels(ctx);
        String activityId = msg.optString("activityId", "");
        boolean isDark = msg.optBoolean("isAfterDark", false);
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("tt_activity_id", activityId);
        intent.putExtra("tt_is_dark", isDark);
        intent.putExtra("tt_from_partner", true);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent, flags);
        Notification notif;
        if (Build.VERSION.SDK_INT >= 26) {
            notif = new Notification.Builder(ctx, CHANNEL_SUGGEST)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Together Tonight")
                    .setContentText("You have a new idea waiting — open to see")
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
        } else {
            notif = new Notification.Builder(ctx)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Together Tonight")
                    .setContentText("You have a new idea waiting — open to see")
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .setPriority(Notification.PRIORITY_DEFAULT)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
        }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_SUGGEST_ID, notif);
    }

    public static Notification buildServiceNotification(Context ctx) {
        ensureChannels(ctx);
        Intent intent = new Intent(ctx, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent, flags);
        if (Build.VERSION.SDK_INT >= 26) {
            return new Notification.Builder(ctx, CHANNEL_SERVICE)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Together Tonight")
                    .setContentText("Ready for your partner nearby")
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build();
        }
        return new Notification.Builder(ctx)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Together Tonight")
                .setContentText("Ready for your partner nearby")
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setPriority(Notification.PRIORITY_LOW)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
