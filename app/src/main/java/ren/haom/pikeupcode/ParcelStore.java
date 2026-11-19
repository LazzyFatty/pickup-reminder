package ren.haom.pikeupcode;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public final class ParcelStore extends SQLiteOpenHelper {
    public static final String CHANGED = "ren.haom.pikeupcode.CHANGED";
    private static ParcelStore instance;
    private final Context context;

    public static synchronized ParcelStore get(Context context) {
        if (instance == null) instance = new ParcelStore(context.getApplicationContext(), "parcels.db");
        return instance;
    }

    ParcelStore(Context context, String databaseName) { super(context, databaseName, null, 1); this.context = context; }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE parcels (_id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL, company TEXT NOT NULL, station TEXT NOT NULL, address TEXT NOT NULL, tracking TEXT NOT NULL, phone TEXT NOT NULL, url TEXT NOT NULL, body TEXT NOT NULL, sender TEXT NOT NULL, received_at INTEGER NOT NULL, picked_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX parcel_status ON parcels(picked_at, received_at)");
        db.execSQL("CREATE TABLE seen (digest TEXT PRIMARY KEY)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Missing database migration");
    }

    public SharedPreferences settings() { return context.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    public int retentionDays() { return settings().getInt("retention_days", 30); }

    public List<Parcel> ingest(String body, String sender, long receivedAt) {
        List<Parcel> parsed = SmsParser.parse(body);
        if (parsed.isEmpty()) return parsed;
        SQLiteDatabase db = getWritableDatabase();
        List<Parcel> added = new ArrayList<>();
        db.beginTransaction();
        try {
            for (Parcel p : parsed) {
                ContentValues seen = new ContentValues();
                // Keep only a digest after history cleanup so importing the inbox cannot resurrect deleted records.
                seen.put("digest", digest(sender + "\n" + body + "\n" + receivedAt / 60000L + "\n" + p.code));
                if (db.insertWithOnConflict("seen", null, seen, SQLiteDatabase.CONFLICT_IGNORE) == -1) continue;
                try (Cursor duplicate = db.rawQuery("SELECT _id FROM parcels WHERE picked_at=0 AND code=? AND sender=? AND (body=? OR (tracking<>'' AND tracking=? AND station=? AND address=?)) LIMIT 1",
                        new String[]{p.code, sender, body, p.tracking, p.station, p.address})) {
                    if (duplicate.moveToFirst()) continue;
                }
                p.sender = sender; p.receivedAt = receivedAt;
                p.id = db.insertOrThrow("parcels", null, values(p));
                added.add(p);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return added;
    }

    public List<Parcel> list(boolean picked) {
        cleanup(System.currentTimeMillis());
        List<Parcel> items = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("parcels", null, picked ? "picked_at>0" : "picked_at=0", null, null, null, picked ? "picked_at DESC" : "received_at DESC")) {
            while (c.moveToNext()) items.add(read(c));
        }
        return items;
    }

    public Parcel find(long id) {
        try (Cursor c = getReadableDatabase().query("parcels", null, "_id=?", new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    public void setPicked(long id, boolean picked) {
        ContentValues v = new ContentValues(); v.put("picked_at", picked ? System.currentTimeMillis() : 0L);
        getWritableDatabase().update("parcels", v, "_id=?", new String[]{String.valueOf(id)});
    }

    public Parcel delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Parcel parcel = find(id);
            if (parcel != null) db.delete("parcels", "_id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
            return parcel;
        } finally { db.endTransaction(); }
    }

    public void restore(Parcel parcel) {
        ContentValues v = values(parcel); v.put("_id", parcel.id);
        getWritableDatabase().insertOrThrow("parcels", null, v);
    }

    public int cleanup(long now) {
        int days = retentionDays();
        if (days == 0) return 0;
        return getWritableDatabase().delete("parcels", "picked_at>0 AND picked_at<=?", new String[]{String.valueOf(now - days * 86400000L)});
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte part : bytes) b.append(String.format(java.util.Locale.ROOT, "%02x", part & 255));
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static Parcel read(Cursor c) {
        Parcel p = new Parcel(); p.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        p.code = value(c, "code"); p.company = value(c, "company"); p.station = value(c, "station");
        p.address = value(c, "address"); p.tracking = value(c, "tracking"); p.phone = value(c, "phone");
        p.url = value(c, "url"); p.body = value(c, "body"); p.sender = value(c, "sender");
        p.receivedAt = c.getLong(c.getColumnIndexOrThrow("received_at"));
        p.pickedAt = c.getLong(c.getColumnIndexOrThrow("picked_at"));
        return p;
    }
    private static ContentValues values(Parcel p) {
        ContentValues v = new ContentValues();
        v.put("code", p.code); v.put("company", p.company); v.put("station", p.station);
        v.put("address", p.address); v.put("tracking", p.tracking); v.put("phone", p.phone);
        v.put("url", p.url); v.put("body", p.body); v.put("sender", p.sender);
        v.put("received_at", p.receivedAt); v.put("picked_at", p.pickedAt);
        return v;
    }
    private static String value(Cursor c, String key) { return c.getString(c.getColumnIndexOrThrow(key)); }
}
