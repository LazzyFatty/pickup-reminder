package ren.haom.pikeupcode;

import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.util.Collections;
import java.util.List;

public final class ParcelWidgetService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            private volatile List<Parcel> items = Collections.emptyList();
            @Override public void onCreate() { onDataSetChanged(); }
            @Override public void onDataSetChanged() { items = ParcelStore.get(ParcelWidgetService.this).list(false); }
            @Override public void onDestroy() { items = Collections.emptyList(); }
            @Override public int getCount() { return items.size(); }
            @Override public RemoteViews getViewAt(int position) {
                List<Parcel> snapshot = items;
                if (position < 0 || position >= snapshot.size()) return null;
                Parcel p = snapshot.get(position);
                RemoteViews view = new RemoteViews(getPackageName(), R.layout.parcel_widget_row);
                view.setTextViewText(R.id.widget_code, p.code);
                view.setTextViewText(R.id.widget_place, p.place());
                view.setOnClickFillInIntent(R.id.widget_row, new Intent().putExtra("parcel_id", p.id));
                return view;
            }
            @Override public RemoteViews getLoadingView() { return null; }
            @Override public int getViewTypeCount() { return 1; }
            @Override public long getItemId(int position) { List<Parcel> snapshot = items; return position < snapshot.size() ? snapshot.get(position).id : position; }
            @Override public boolean hasStableIds() { return true; }
        };
    }
}
