package ren.haom.pikeupcode;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import androidx.core.content.ContextCompat;

public final class ParcelNotifications {
    static final String CHANNEL = "pickup_arrivals";

    private ParcelNotifications() {}

    static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "取件提醒", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("新短信识别出的取件码和驿站位置");
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    static boolean enabled(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return manager.areNotificationsEnabled() && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    static void show(Context context, Parcel parcel) {
        if (!enabled(context)) return;
        String location = parcel.address.isEmpty() ? parcel.place() : parcel.address;
        if (!parcel.station.isEmpty() && !parcel.address.isEmpty() && !parcel.address.contains(parcel.station)) location = parcel.station + " · " + parcel.address;
        SpannableString expanded = new SpannableString(parcel.code + "\n" + location);
        expanded.setSpan(new StyleSpan(Typeface.BOLD), 0, parcel.code.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        expanded.setSpan(new RelativeSizeSpan(1.5f), 0, parcel.code.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Intent open = new Intent(context, MainActivity.class).setData(Uri.parse("pickup-notification://parcel/" + parcel.id))
                .putExtra("parcel_id", parcel.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent click = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(parcel.code).setContentText(location)
                .setStyle(new Notification.BigTextStyle().bigText(expanded))
                .setContentIntent(click).setAutoCancel(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("有包裹待取").build())
                .build();
        try { context.getSystemService(NotificationManager.class).notify("parcel:" + parcel.id, 0, notification); }
        catch (SecurityException e) { Log.w("Pickup", "Notification permission is unavailable"); }
    }

    static void cancel(Context context, long id) {
        context.getSystemService(NotificationManager.class).cancel("parcel:" + id, 0);
    }
}
