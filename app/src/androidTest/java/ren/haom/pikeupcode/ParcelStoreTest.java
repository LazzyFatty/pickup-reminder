package ren.haom.pikeupcode;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ParcelStoreTest {
    private Context context;
    private ParcelStore store;
    private static final String BODY = "【顺丰速运】包裹已到云杉驿站，取件码8-2-1456，运单号SF123456789012。";
    private final long now = System.currentTimeMillis();

    @Before public void setup() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context = new ContextWrapper(target) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("instrumented-test-" + name, mode);
            }
        };
        context.deleteDatabase("instrumented-test.db");
        store = new ParcelStore(context, "instrumented-test.db");
        store.settings().edit().clear().commit();
    }
    @After public void cleanup() {
        store.close(); context.deleteDatabase("instrumented-test.db"); store.settings().edit().clear().commit();
    }
    @Test public void duplicateImportAndReminderDoNotCreateDuplicates() {
        assertEquals(1, store.ingest(BODY, "106000", now).size());
        assertEquals(0, store.ingest(BODY, "106000", now).size());
        assertEquals(0, store.ingest(BODY, "106000", now + 3600000L).size());
        assertEquals(1, store.list(false).size());
    }
    @Test public void pickedHistoryCanBeRestoredAndSurvivesReopening() {
        store.ingest(BODY, "106000", now);
        long id = store.list(false).get(0).id;
        store.setPicked(id, true);
        assertTrue(store.list(false).isEmpty()); assertEquals(1, store.list(true).size());
        store.close(); store = new ParcelStore(context, "instrumented-test.db");
        assertEquals(1, store.list(true).size());
        store.setPicked(id, false);
        assertEquals(1, store.list(false).size()); assertTrue(store.list(true).isEmpty());
    }
    @Test public void retentionDeletesOnlyPickedAndReimportCannotResurrectIt() {
        store.settings().edit().putInt("retention_days", 1).commit();
        store.ingest(BODY, "106000", now);
        long id = store.list(false).get(0).id; store.setPicked(id, true);
        store.ingest("包裹取件码4-2-9988，已到云杉驿站", "106001", now);
        assertEquals(1, store.cleanup(now + 2L * 86400000L));
        assertEquals(1, store.list(false).size()); assertTrue(store.list(true).isEmpty());
        assertEquals(0, store.ingest(BODY, "106000", now).size());
    }
    @Test public void zeroRetentionNeverDeletesAndFutureCodeReuseIsAllowed() {
        store.settings().edit().putInt("retention_days", 0).commit();
        store.ingest(BODY, "106000", now); store.setPicked(store.list(false).get(0).id, true);
        assertEquals(0, store.cleanup(now + 365L * 86400000L));
        assertEquals(1, store.list(true).size());
        assertEquals(1, store.ingest(BODY, "106000", now + 2L * 86400000L).size());
        assertEquals(1, store.list(false).size());
    }
    @Test public void multipartBodyMultipleCodesAndOptionalFieldsAreStored() {
        assertEquals(2, store.ingest("【中通快递】快递已到松风路8号云杉驿站，取件码123456、654321，运单号ZT123456789012，联系电话13800138000，https://example.org/p", "106000", now).size());
        Parcel p = store.list(false).get(0);
        assertEquals("中通快递", p.company); assertEquals("云杉驿站", p.station);
        assertEquals("松风路8号云杉驿站", p.address); assertEquals("ZT123456789012", p.tracking);
        assertEquals("13800138000", p.phone); assertEquals("https://example.org/p", p.url);
    }
    @Test public void pipeSeparatedLockerAndAddressPreserveLeadingZeroAfterReopening() {
        String body = "取件码 01234567\n云杉快递柜 | 松风路88号\n";
        Parcel added = store.ingest(body, "106000", now).get(0);
        store.close(); store = new ParcelStore(context, "instrumented-test.db");
        Parcel saved = store.find(added.id);
        assertEquals("01234567", saved.code); assertEquals("云杉快递柜", saved.station);
        assertEquals("松风路88号", saved.address); assertEquals("", saved.company);
        assertEquals(body, saved.body); assertEquals(0L, saved.pickedAt);
        assertTrue(store.ingest(body, "106000", now).isEmpty());
        assertEquals(1, store.list(false).size());
    }
    @Test public void deleteOneRecordAndRestoreAllItsFields() {
        Parcel original = store.ingest(BODY, "106000", now).get(0);
        Parcel other = store.ingest("包裹取件码4-2-9988，已到云杉驿站", "106001", now).get(0);
        Parcel removed = store.delete(original.id);
        assertNotNull(removed); assertNull(store.find(original.id));
        assertNotNull(store.find(other.id));
        assertTrue(store.ingest(BODY, "106000", now).isEmpty());
        store.restore(removed);
        Parcel restored = store.find(original.id);
        assertEquals(original.code, restored.code); assertEquals(original.station, restored.station);
        assertEquals(original.company, restored.company); assertEquals(original.address, restored.address);
        assertEquals(original.tracking, restored.tracking); assertEquals(original.phone, restored.phone);
        assertEquals(original.url, restored.url); assertEquals(original.body, restored.body);
        assertEquals(original.sender, restored.sender); assertEquals(original.receivedAt, restored.receivedAt);
        assertEquals(0L, restored.pickedAt); assertEquals(2, store.list(false).size());
    }
    @Test public void deletingHistoryPersistsAndUndoPreservesPickedTime() {
        Parcel original = store.ingest(BODY, "106000", now).get(0);
        store.setPicked(original.id, true);
        Parcel removed = store.delete(original.id);
        assertTrue(removed.pickedAt > 0);
        store.close(); store = new ParcelStore(context, "instrumented-test.db");
        assertNull(store.find(original.id)); assertTrue(store.list(true).isEmpty());
        assertTrue(store.ingest(BODY, "106000", now).isEmpty());
        store.restore(removed);
        assertEquals(removed.pickedAt, store.find(original.id).pickedAt);
        assertEquals(1, store.list(true).size()); assertTrue(store.list(false).isEmpty());
        assertNull(store.delete(Long.MAX_VALUE));
    }
}
