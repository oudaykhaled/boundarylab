package dev.boundarylab.nativeapp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Standalone instrumentation-APK process: use only Android/JDK classes. */
public final class DelayedFixtureProvider extends ContentProvider {
    private CountDownLatch release = new CountDownLatch(1);
    private volatile boolean opened;

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("reset".equals(method)) { release = new CountDownLatch(1); opened = false; }
        if ("release".equals(method)) release.countDown();
        Bundle result = new Bundle();
        result.putBoolean("opened", opened);
        return result;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"fixture".equals(uri.getLastPathSegment()) || !"r".equals(mode))
            throw new FileNotFoundException("Only the owned readonly fixture is supported");
        File file = new File(getContext().getCacheDir(), "delayed-owned-fixture.txt");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(new byte[] {97, 98, 99});
        } catch (IOException error) { throw new FileNotFoundException("Fixture could not be written"); }
        opened = true;
        try {
            if (!release.await(15, TimeUnit.SECONDS))
                throw new FileNotFoundException("Test provider release was not signalled");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new FileNotFoundException("Test provider interrupted");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode, CancellationSignal signal)
            throws FileNotFoundException { return openFile(uri, mode); }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[] {"owned-fixture.txt"});
        return cursor;
    }
    @Override public String getType(Uri uri) { return "text/plain"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
