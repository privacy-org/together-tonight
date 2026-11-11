package com.mahesh.togethertonight;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * JS bridge exposed to the bundled web app as window.TogetherBridge.
 * Every method is called from JS; results that arrive later (discovery,
 * send results, incoming suggestions) come back through the JS callbacks
 * window.onLinkStatusChanged / onPartnerSuggestion / onSendResult /
 * onDevicesFound / onDiscoveryFinished / onLinkCode / onNearbyResult.
 */
public class TogetherBridge {

    private final WeakReference<MainActivity> activityRef;

    public TogetherBridge(MainActivity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    private MainActivity activity() {
        return activityRef.get();
    }

    @JavascriptInterface
    public String getLinkStatus() {
        MainActivity a = activity();
        if (a == null) return "{\"linked\":false}";
        return LinkManager.getLinkStatusJson(a);
    }

    @JavascriptInterface
    public void setMyName(String name) {
        MainActivity a = activity();
        if (a == null) return;
        LinkManager.setMyName(a, name);
        LinkManager.notifyStatus(a);
    }

    @JavascriptInterface
    public String getMyName() {
        MainActivity a = activity();
        if (a == null) return "Partner";
        return LinkManager.myName(a);
    }

    @JavascriptInterface
    public void requestPermissions() {
        MainActivity a = activity();
        if (a != null) a.requestAllPermissions();
    }

    @JavascriptInterface
    public void startDiscovery() {
        MainActivity a = activity();
        if (a != null) a.startDiscovery();
    }

    @JavascriptInterface
    public void makeDiscoverable() {
        MainActivity a = activity();
        if (a != null) a.makeDiscoverable();
    }

    @JavascriptInterface
    public void linkDevice(String address) {
        MainActivity a = activity();
        if (a != null) a.linkDevice(address);
    }

    @JavascriptInterface
    public void sendSuggestion(String activityJson) {
        MainActivity a = activity();
        if (a == null) return;
        try {
            JSONObject in = new JSONObject(activityJson);
            JSONObject out = new JSONObject();
            out.put("type", "suggest");
            out.put("app", "together-tonight");
            out.put("sender", LinkManager.myName(a));
            out.put("activityId", in.optString("activityId", ""));
            out.put("title", in.optString("title", ""));
            out.put("category", in.optString("category", ""));
            out.put("question", in.optString("question", ""));
            out.put("isAfterDark", in.optBoolean("isAfterDark", false));
            out.put("ts", System.currentTimeMillis());
            LinkManager.sendJson(a, out.toString(), false);
        } catch (Exception e) {
            if (LinkManager.listener != null) {
                LinkManager.listener.onSendResult(false, "Could not send that — try again.");
            }
        }
    }

    @JavascriptInterface
    public void checkNearby() {
        MainActivity a = activity();
        if (a != null) LinkManager.checkNearby(a);
    }

    @JavascriptInterface
    public void unlink() {
        MainActivity a = activity();
        if (a == null) return;
        LinkManager.clearLink(a);
        a.stopLinkService();
    }

    @JavascriptInterface
    public boolean isBridgeAvailable() {
        return true;
    }

    // ---- After Dark PIN (v1.2) -------------------------------------------
    // The PIN itself is NEVER stored or logged. Only its SHA-256 hash
    // (PIN + fixed app salt) is kept in SharedPreferences. There is no
    // reset code and no backdoor: the only reset is clearing app data.
    private static final String PIN_PREFS = "tt_pin";
    private static final String PIN_KEY_HASH = "pin_hash";
    private static final String PIN_SALT = "TT-PIN-SALT-v1::TogetherTonight";

    private static String pinHash(String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((pin + PIN_SALT).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean validPin(String pin) {
        return pin != null && pin.length() == 4 && pin.matches("[0-9]{4}");
    }

    private SharedPreferences pinPrefs(MainActivity a) {
        return a.getSharedPreferences(PIN_PREFS, Context.MODE_PRIVATE);
    }

    @JavascriptInterface
    public boolean hasPin() {
        MainActivity a = activity();
        if (a == null) return false;
        return pinPrefs(a).getString(PIN_KEY_HASH, null) != null;
    }

    @JavascriptInterface
    public boolean setPinHash(String pin) {
        MainActivity a = activity();
        if (a == null || !validPin(pin)) return false;
        String hash = pinHash(pin);
        if (hash == null) return false;
        pinPrefs(a).edit().putString(PIN_KEY_HASH, hash).apply();
        return true;
    }

    @JavascriptInterface
    public boolean verifyPin(String pin) {
        MainActivity a = activity();
        if (a == null || !validPin(pin)) return false;
        String stored = pinPrefs(a).getString(PIN_KEY_HASH, null);
        if (stored == null) return false;
        String hash = pinHash(pin);
        return hash != null && hash.equals(stored);
    }

    @JavascriptInterface
    public void clearPin() {
        MainActivity a = activity();
        if (a == null) return;
        pinPrefs(a).edit().remove(PIN_KEY_HASH).apply();
    }
}
