package com.joseba.juegos.puntocruz;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Guarda y recupera el patrón en curso en el almacenamiento interno de la app. */
public final class PatternStore {
    private static final String FILE = "puntocruz_actual.dat";
    private PatternStore() {}

    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    public static boolean exists(Context c) { return file(c).exists(); }

    public static void save(Context c, Pattern p) {
        File tmp = new File(c.getFilesDir(), FILE + ".tmp");
        try {
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)));
            try {
                p.write(out);
            } finally {
                out.close();
            }
            if (!tmp.renameTo(file(c))) {
                file(c).delete();
                tmp.renameTo(file(c));
            }
        } catch (Exception e) {
            tmp.delete();
        }
    }

    public static Pattern load(Context c) {
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file(c))));
            try {
                return Pattern.read(in);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
