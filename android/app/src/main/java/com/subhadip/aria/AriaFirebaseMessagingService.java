package com.subhadip.aria;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Receives FCM while Aria is backgrounded or closed and shows a WhatsApp-style
 * high-priority message notification with preview, sound, vibration, badge,
 * grouping and a tap-to-open action.
 */
public class AriaFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "aria_messages_v3";
    private static final String GROUP_KEY = "com.subhadip.aria.MESSAGES";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
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
            if (message.getNotification().getTitle() != null) title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null) body = message.getNotification().getBody();
        }

        if (message.getData() != null) {
            if (message.getData().containsKey("title") && message.getData().get("title") != null) {
                title = message.getData().get("title");
            }
            if (message.getData().containsKey("body") && message.getData().get("body") != null) {
                body = message.getData().get("body");
            }
        }

        String id = message.getData() != null && message.getData().get("id") != null
                ? message.getData().get("id")
                : message.getMessageId();
        showNotification(title, body, id);
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
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audio = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(sound, audio);
            nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int requestCode = id == null || id.isEmpty() ? (int) System.currentTimeMillis() : id.hashCode();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Person aria = new Person.Builder()
                .setName("Aria")
                .build();

        NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(aria)
                .setConversationTitle("Aria")
                .addMessage(body, System.currentTimeMillis(), aria);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_aria)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(messagingStyle)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setGroup(GROUP_KEY)
                .setOnlyAlertOnce(false)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setNumber(1);

        nm.notify(requestCode, builder.build());
    }
}
