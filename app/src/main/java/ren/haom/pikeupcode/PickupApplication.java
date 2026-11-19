package ren.haom.pikeupcode;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

public final class PickupApplication extends Application {
    @Override public void onCreate() { super.onCreate(); CleanupJob.schedule(this); ParcelNotifications.createChannel(this); }
    public static void changed(Context context) {
        ParcelWidget.refresh(context);
        context.sendBroadcast(new Intent(ParcelStore.CHANGED).setPackage(context.getPackageName()));
    }
}
