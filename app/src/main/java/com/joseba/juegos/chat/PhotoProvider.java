package com.joseba.juegos.chat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/** Proveedor mínimo para que la app de cámara guarde la foto a resolución completa. */
public class PhotoProvider extends ContentProvider {
    public static final String AUTHORITY = "com.joseba.juegos.fotos";
    public static final Uri PHOTO_URI = Uri.parse("content://" + AUTHORITY + "/foto.jpg");

    public static File photoFile(android.content.Context c) {
        File dir = new File(c.getCacheDir(), "fotos");
        dir.mkdirs();
        return new File(dir, "foto.jpg");
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(photoFile(getContext()), ParcelFileDescriptor.parseMode(mode));
    }

    @Override public String getType(Uri uri) { return "image/jpeg"; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
