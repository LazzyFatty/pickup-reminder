package ren.haom.pikeupcode;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;

public final class ParcelWidget extends AppWidgetProvider {
    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        onUpdate(context, manager, new int[]{id});
    }
    public static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ParcelWidget.class));
        if (ids.length > 0) new ParcelWidget().onUpdate(context, manager, ids);
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        int count = ParcelStore.get(context).list(false).size();
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.parcel_widget);
            Bundle options = manager.getAppWidgetOptions(id);
            int width = options.getInt(context.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            views.setInt(R.id.widget_list, "setNumColumns", width >= 280 ? 2 : 1);
            views.setTextViewText(R.id.widget_count, count + " 个待取");
            Intent service = new Intent(context, ParcelWidgetService.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            service.setData(Uri.parse("pickup-widget://list/" + id));
            views.setRemoteAdapter(R.id.widget_list, service);
            views.setEmptyView(R.id.widget_list, R.id.widget_empty);
            Intent open = new Intent(context, MainActivity.class).setAction("widget-open").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            views.setOnClickPendingIntent(R.id.widget_count, PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            Intent item = new Intent(context, MainActivity.class).setAction("widget-parcel").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            views.setPendingIntentTemplate(R.id.widget_list, PendingIntent.getActivity(context, id, item, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));
            manager.updateAppWidget(id, views);
        }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
    }
}
