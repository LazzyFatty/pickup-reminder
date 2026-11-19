package ren.haom.pikeupcode;

import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.appbar.MaterialToolbar;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {
    private final String BODY = "【顺丰速运】包裹已到松风路88号云杉驿站，取件码T-826-9912，运单号SF123456789012，联系电话13800138000，https://example.org/p 测试批次" + java.util.UUID.randomUUID();

    @Test public void pasteMarkRestoreAndSaveRetention() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        ParcelStore store = ParcelStore.get(context);
        int oldDays = store.retentionDays();
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            onView(withContentDescription("粘贴短信")).perform(click());
            onView(isAssignableFrom(TextInputEditText.class)).perform(replaceText(BODY), closeSoftKeyboard());
            onView(withText("识别并添加")).perform(click());
            awaitCode();
            onView(withText("待取包裹")).check(matches(isDescendantOfA(isAssignableFrom(MaterialToolbar.class))));
            onView(withText("短信在本机识别")).check(doesNotExist());
            onView(allOf(withText(startsWith("SF123456789012 · ")), isDisplayed(), inTestCard())).check(matches(isDisplayed()));
            onView(withText("T-826-9912")).perform(longClick());
            onView(withText("删除这个包裹？")).check(matches(isDisplayed()));
            onView(withText("取消")).perform(click()); awaitCode();
            onView(withText("T-826-9912")).perform(longClick());
            onView(withText("删除")).perform(click());
            awaitText("恢复");
            onView(withText("恢复")).perform(click()); awaitCode();
            onView(allOf(withText("标记已取"), isDisplayed(), inTestCard())).perform(click());
            onView(withContentDescription("已取")).perform(click()); awaitCode();
            onView(withText("已取包裹")).check(matches(isDescendantOfA(isAssignableFrom(MaterialToolbar.class))));
            onView(withText("T-826-9912")).perform(longClick());
            onView(withText("删除")).perform(click());
            awaitText("恢复"); onView(withText("恢复")).perform(click()); awaitCode();
            onView(allOf(withText("恢复待取"), isDisplayed(), inTestCard())).perform(click());
            onView(allOf(withText("待取"), isDisplayed())).perform(click()); awaitCode();
            try { onView(withId(com.google.android.material.R.id.snackbar_text)).perform(swipeRight()); }
            catch (androidx.test.espresso.NoMatchingViewException ignored) { }
            onView(withContentDescription("设置")).perform(click());
            onView(isAssignableFrom(TextInputEditText.class)).perform(scrollTo(), replaceText("0"), closeSoftKeyboard());
            onView(isAssignableFrom(TextInputEditText.class)).check(matches(withText("0")));
            onView(withText("保存保留天数")).perform(scrollTo(), click());
            long deadline = System.currentTimeMillis() + 5000;
            while (store.retentionDays() != 0 && System.currentTimeMillis() < deadline) Thread.sleep(50);
            assertEquals(0, store.retentionDays());
        } finally {
            store.getWritableDatabase().delete("parcels", "body=?", new String[]{BODY});
            store.settings().edit().putInt("retention_days", oldDays).commit();
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private static void awaitCode() throws InterruptedException {
        awaitText("T-826-9912");
    }
    private static org.hamcrest.Matcher<android.view.View> inTestCard() {
        return isDescendantOfA(allOf(isAssignableFrom(MaterialCardView.class), hasDescendant(withText("T-826-9912"))));
    }
    private static void awaitText(String text) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (true) {
            try { onView(withText(text)).check(matches(isDisplayed())); return; }
            catch (AssertionError | androidx.test.espresso.NoMatchingViewException e) {
                if (System.currentTimeMillis() >= deadline) throw e;
                Thread.sleep(50);
            }
        }
    }
}
