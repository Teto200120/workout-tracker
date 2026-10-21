package com.hector.workouttracker;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;

/** Test APK only. Framework-only dependencies also work in the provider's separate process. */
public class TestDocumentsProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/json"; }

    private File file(Uri uri) {
        String name = uri.getLastPathSegment();
        if (!"fixture.json".equals(name) && !"future.json".equals(name) && !"export.json".equals(name)) {
            throw new IllegalArgumentException("Unknown test document");
        }
        File file = new File(getContext().getFilesDir(), name);
        if (!"export.json".equals(name) && !file.exists()) {
            try (java.io.InputStream input = getContext().getAssets().open("baseline.json")) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                JSONObject backup = new JSONObject(bytes.toString("UTF-8")).getJSONObject("backup");
                if ("future.json".equals(name)) backup.put("backupFileVersion", 99);
                Files.write(file.toPath(), backup.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception error) {
                throw new IllegalStateException("Test document could not be prepared", error);
            }
        }
        return file;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode));
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        String[] columns = projection == null ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            values[i] = OpenableColumns.DISPLAY_NAME.equals(columns[i]) ? file(uri).getName() : file(uri).length();
        }
        MatrixCursor cursor = new MatrixCursor(columns);
        cursor.addRow(values);
        return cursor;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
}
