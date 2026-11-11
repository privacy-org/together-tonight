package com.mahesh.togethertonight;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * Lightweight foreground service, started only when the two phones are
 * linked. Its only job is to keep the Bluetooth RFCOMM listener alive
 * (via LinkManager) so a partner suggestion can arrive while the app
 * is in the background. The service notification itself is generic and
 * lock-screen-secret.
 *
 * Android may still pause it if the phone is locked for a long time —
 * the Link screen says so, and reopening the app restarts listening.
 */
public class LinkService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LinkManager.startServer(this);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(LinkManager.NOTIF_SERVICE_ID,
                        LinkManager.buildServiceNotification(this),
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(LinkManager.NOTIF_SERVICE_ID,
                        LinkManager.buildServiceNotification(this));
            }
        } catch (Exception e) {
            // If foreground start is refused, still listen while the
            // process is alive; the app restarts the server on open.
            LinkManager.startServer(this);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        LinkManager.stopServer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
