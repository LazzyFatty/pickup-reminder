package ren.haom.pikeupcode;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class ParcelNotificationsTest {
    @Test public void incomingSmsStoresCodesAndPostsSeparateNotificationsWithoutRepeating() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(ParcelNotifications.enabled(context));
        ParcelStore store = ParcelStore.get(context);
        String body = "包裹已到松风路8号云杉驿站，取件码3-2-7001、3-2-7002。测试批次" + java.util.UUID.randomUUID();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        List<Parcel> items = new ArrayList<>();
        Parcel manual = null;
        long now = System.currentTimeMillis();
        try {
            SmsReceiver.process(context, body, "106991", now);
            for (Parcel p : store.list(false)) if (p.body.equals(body)) items.add(p);
            assertEquals(2, items.size());
            for (Parcel parcel : items) {
                Notification notification = awaitNotification(manager, parcel.id);
                assertEquals(parcel.code, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString());
                String location = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
                assertTrue(location.contains("松风路8号")); assertTrue(location.contains("云杉驿站"));
                assertTrue(notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().startsWith(parcel.code));
                assertNotNull(notification.contentIntent);
            }
            for (Parcel parcel : items) ParcelNotifications.cancel(context, parcel.id);
            long deadline = System.currentTimeMillis() + 5000;
            while (items.stream().anyMatch(p -> find(manager, p.id) != null) && System.currentTimeMillis() < deadline) Thread.sleep(50);
            SmsReceiver.process(context, body, "106991", now);
            for (Parcel parcel : items) assertNull(find(manager, parcel.id));
            manual = store.ingest("包裹取件码7-1-9000。测试批次" + java.util.UUID.randomUUID(), "手动粘贴测试", now).get(0);
            assertNull(find(manager, manual.id));
        } finally {
            for (Parcel parcel : items) { ParcelNotifications.cancel(context, parcel.id); store.delete(parcel.id); }
            if (manual != null) store.delete(manual.id);
            PickupApplication.changed(context);
        }
    }

    @Test public void blockedNotificationsDoNotLoseIncomingParcel() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED);
        ParcelStore store = ParcelStore.get(context);
        String body = "包裹取件码3-7-8111。测试批次" + java.util.UUID.randomUUID();
        try {
            SmsReceiver.process(context, body, "106992", System.currentTimeMillis());
            Parcel saved = null;
            for (Parcel p : store.list(false)) if (p.body.equals(body)) saved = p;
            assertNotNull(saved);
            assertNull(find(context.getSystemService(NotificationManager.class), saved.id));
        } finally { store.getWritableDatabase().delete("parcels", "body=?", new String[]{body}); PickupApplication.changed(context); }
    }

    private static StatusBarNotification find(NotificationManager manager, long id) {
        for (StatusBarNotification item : manager.getActiveNotifications()) if (("parcel:" + id).equals(item.getTag())) return item;
        return null;
    }
    private static Notification awaitNotification(NotificationManager manager, long id) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        StatusBarNotification result;
        while ((result = find(manager, id)) == null && System.currentTimeMillis() < deadline) Thread.sleep(50);
        assertNotNull(result);
        return result.getNotification();
    }
}
