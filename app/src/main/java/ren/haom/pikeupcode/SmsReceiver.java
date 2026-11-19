package ren.haom.pikeupcode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import java.util.List;

public final class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) return;
        StringBuilder body = new StringBuilder();
        for (SmsMessage message : messages) if (message != null && message.getMessageBody() != null) body.append(message.getMessageBody());
        String text = body.toString();
        String sender = messages[0].getOriginatingAddress();
        long timestamp = messages[0].getTimestampMillis();
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                process(context, text, sender == null ? "" : sender, timestamp);
            } catch (RuntimeException e) {
                // Do not log SMS contents; inbox import can recover the message after a storage failure.
                Log.e("Pickup", "Incoming SMS could not be saved", e);
                ParcelStore.get(context).settings().edit().putBoolean("receive_error", true).apply();
            } finally { pending.finish(); }
        }, "pickup-sms").start();
    }

    static void process(Context context, String body, String sender, long receivedAt) {
        ParcelStore store = ParcelStore.get(context);
        List<Parcel> added = store.ingest(body, sender, receivedAt);
        store.cleanup(System.currentTimeMillis());
        if (!added.isEmpty()) {
            PickupApplication.changed(context);
            for (Parcel parcel : added) ParcelNotifications.show(context, parcel);
        }
    }
}
