package com.joseba.juegos;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;

import java.io.InputStream;

/** Carga de imágenes reducidas y con la orientación EXIF corregida. */
public final class ImageUtil {
    private ImageUtil() {}

    public static Bitmap load(Context c, Uri uri, int maxSide) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = c.getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(in, null, o);
        in.close();
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        in = c.getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
        in.close();
        if (bmp == null) throw new Exception("Formato de imagen no reconocido");
        if (Math.max(bmp.getWidth(), bmp.getHeight()) > maxSide) {
            float f = maxSide / (float) Math.max(bmp.getWidth(), bmp.getHeight());
            bmp = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(bmp.getWidth() * f)),
                    Math.max(1, Math.round(bmp.getHeight() * f)), true);
        }
        int deg = exifRotation(c, uri);
        if (deg != 0) bmp = rotate(bmp, deg);
        return bmp;
    }

    public static Bitmap rotate(Bitmap b, int deg) {
        Matrix m = new Matrix();
        m.postRotate(deg);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    /** Lee la orientación EXIF (API 24+) por reflexión, para seguir compilando con la API 23. */
    public static int exifRotation(Context c, Uri uri) {
        if (Build.VERSION.SDK_INT < 24) return 0;
        InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(uri);
            Class<?> cls = Class.forName("android.media.ExifInterface");
            Object exif = cls.getConstructor(InputStream.class).newInstance(in);
            int o = (Integer) cls.getMethod("getAttributeInt", String.class, int.class).invoke(exif, "Orientation", 1);
            switch (o) {
                case 6: return 90;
                case 3: return 180;
                case 8: return 270;
                default: return 0;
            }
        } catch (Throwable t) {
            return 0;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
    }
}
