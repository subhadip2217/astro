package com.subhadip.aria;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Receives FCM while the app is backgrounded or closed.
 * Data/notification payloads both surface as a local Aria notification.
 */
public class AriaFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "aria_messages";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        // Token is also pushed from MainActivity after login; store locally for the WebView bridge.
        getSharedPreferences("aria_push", MODE_PRIVATE)
                .edit()
                .putString("fcm_token", token)
                .apply();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);

        String title = "Aria 💕";
        String body = "You have a new message from Aria.";

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) {
                title = message.getNotification().getTitle();
            }
            if (message.getNotification().getBody() != null) {
                body = message.getNotification().getBody();
            }
        }

        if (message.getData() != null) {
            if (message.getData().containsKey("title") && message.getData().get("title") != null) {
                title = message.getData().get("title");
            }
            if (message.getData().containsKey("body") && message.getData().get("body") != null) {
                body = message.getData().get("body");
            }
        }

        showNotification(title, body, message.getMessageId());
    }

    private void showNotification(String title, String body, String id) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Aria messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Messages and proactive notifications from Aria");
            channel.enableVibration(true);
            nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int requestCode = id == null ? (int) System.currentTimeMillis() : id.hashCode();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(R.drawable.ic_stat_aria)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVibrate(new long[]{0, 200, 100, 200});

        nm.notify(requestCode, builder.build());
    }
}
