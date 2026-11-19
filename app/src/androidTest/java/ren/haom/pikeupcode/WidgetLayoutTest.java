package ren.haom.pikeupcode;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.RemoteViews;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WidgetLayoutTest {
    @Test public void gridSupportsOneAndTwoColumnsAndLongCodeFitsSmallCell() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        instrumentation.runOnMainSync(() -> {
            for (int columns : new int[]{1, 2}) {
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.parcel_widget);
                views.setInt(R.id.widget_list, "setNumColumns", columns);
                View root = views.apply(context, new FrameLayout(context));
                measure(root, context, 300);
                assertEquals(columns, ((GridView)root.findViewById(R.id.widget_list)).getNumColumns());
            }
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.parcel_widget_row);
            String code = "A1-2345-678901";
            row.setTextViewText(R.id.widget_code, code);
            View root = row.apply(context, new FrameLayout(context)); measure(root, context, 120);
            TextView text = root.findViewById(R.id.widget_code);
            assertTrue(text.getPaint().measureText(code) <= text.getWidth() - text.getPaddingLeft() - text.getPaddingRight() + 1);
        });
    }
    private static void measure(View view, Context context, int widthDp) {
        int width = Math.round(widthDp * context.getResources().getDisplayMetrics().density);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, width);
    }
}
