package com.axelsarassamit.gx12;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Standalone test-APK process: no dependency on the target APK's Kotlin runtime. */
public final class FailingAppearanceProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("probe".equals(method)) {
            Bundle result = new Bundle();
            result.putInt("pid", android.os.Process.myPid());
            return result;
        }
        if (!"appearance".equals(method)) throw new IllegalArgumentException(method);
        android.os.Process.killProcess(android.os.Process.myPid());
        return null;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
