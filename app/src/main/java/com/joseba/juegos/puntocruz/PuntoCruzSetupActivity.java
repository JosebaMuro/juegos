package com.joseba.juegos.puntocruz;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.joseba.juegos.Ui;

import java.io.InputStream;

/**
 * Pantalla de preparación: elegir imagen, ajustar tamaño y número de colores,
 * ver la vista previa del patrón y empezar a colorear.
 */
public class PuntoCruzSetupActivity extends Activity {
    private static final int REQ_PICK = 1;
    private static final int MIN_W = 20, MAX_W = 150;

    private Bitmap source;          // imagen reducida a como mucho 4 px por puntada
    private Pattern generated;
    private int generation = 0;

    private ImageView preview;
    private TextView info, widthLabel, colorsLabel;
    private SeekBar widthBar, colorsBar;
    private CheckBox cleanupBox;
    private Button startButton, rotateButton, continueButton;
    private ProgressBar progress;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = Ui.dp(this, 16);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF5F1EA);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        continueButton = button("Continuar el patrón guardado", 0xFF6D4C41);
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(PuntoCruzSetupActivity.this, PuntoCruzActivity.class));
            }
        });
        root.addView(continueButton);

        Button pick = button("Elegir imagen", 0xFFE91E63);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickImage(); }
        });
        root.addView(pick);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(Ui.rounded(0xFFE0DCD3, Ui.dp(this, 8)));
        preview.setMinimumHeight(Ui.dp(this, 200));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 300));
        plp.topMargin = Ui.dp(this, 12);
        root.addView(preview, plp);

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        info = new TextView(this);
        info.setText("Elige una foto o dibujo de tu galería para convertirlo en un patrón de punto de cruz.");
        info.setTextColor(0xFF5D4037);
        info.setGravity(Gravity.CENTER_HORIZONTAL);
        info.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        root.addView(info);

        rotateButton = button("Girar 90°", 0xFF8D6E63);
        rotateButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { rotate(); }
        });
        root.addView(rotateButton);

        widthLabel = label();
        root.addView(widthLabel);
        widthBar = new SeekBar(this);
        widthBar.setMax(MAX_W - MIN_W);
        widthBar.setProgress(60 - MIN_W);
        root.addView(widthBar);

        colorsLabel = label();
        root.addView(colorsLabel);
        colorsBar = new SeekBar(this);
        colorsBar.setMax(PatternGenerator.MAX_COLORS - 2);
        colorsBar.setProgress(12 - 2);
        root.addView(colorsBar);

        cleanupBox = new CheckBox(this);
        cleanupBox.setText("Quitar puntos sueltos (patrón más limpio)");
        cleanupBox.setChecked(true);
        root.addView(cleanupBox);

        startButton = button("Empezar a colorear", 0xFF43A047);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { start(); }
        });
        root.addView(startButton);

        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { updateLabels(); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) { regenerate(); }
        };
        widthBar.setOnSeekBarChangeListener(l);
        colorsBar.setOnSeekBarChangeListener(l);
        cleanupBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) { regenerate(); }
        });

        updateLabels();
        updateButtons();
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateButtons();
    }

    private Button button(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setBackground(Ui.rounded(color, Ui.dp(this, 10)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 50));
        lp.topMargin = Ui.dp(this, 8);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView label() {
        TextView t = new TextView(this);
        t.setTextColor(0xFF3E2723);
        t.setTextSize(15);
        t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 2));
        return t;
    }

    private int gridWidth() { return MIN_W + widthBar.getProgress(); }
    private int colors() { return 2 + colorsBar.getProgress(); }

    private void updateLabels() {
        String size = "";
        if (source != null) {
            int h = Math.max(1, Math.round(source.getHeight() * (float) gridWidth() / source.getWidth()));
            size = "  (" + gridWidth() + " × " + h + " puntadas)";
        }
        widthLabel.setText("Ancho: " + gridWidth() + " puntadas" + size);
        colorsLabel.setText("Colores máximos: " + colors());
    }

    private void updateButtons() {
        boolean has = source != null;
        startButton.setEnabled(generated != null);
        startButton.setAlpha(generated != null ? 1f : 0.4f);
        rotateButton.setVisibility(has ? View.VISIBLE : View.GONE);
        widthBar.setEnabled(has);
        colorsBar.setEnabled(has);
        continueButton.setVisibility(PatternStore.exists(this) ? View.VISIBLE : View.GONE);
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Elige una imagen"), REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "No hay ninguna app para elegir imágenes", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            loadImage(data.getData());
        }
    }

    private void loadImage(final Uri uri) {
        progress.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = decode(uri);
                main.post(new Runnable() {
                    @Override public void run() {
                        progress.setVisibility(View.GONE);
                        if (bmp == null) {
                            Toast.makeText(PuntoCruzSetupActivity.this, "No se pudo abrir la imagen", Toast.LENGTH_LONG).show();
                            return;
                        }
                        source = bmp;
                        updateLabels();
                        updateButtons();
                        regenerate();
                    }
                });
            }
        }).start();
    }

    /** Decodifica la imagen reduciéndola a ~600 px y aplicando la rotación EXIF. */
    private Bitmap decode(Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 600) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            in = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
            in.close();
            if (bmp == null) return null;
            int max = 600;
            if (Math.max(bmp.getWidth(), bmp.getHeight()) > max) {
                float f = max / (float) Math.max(bmp.getWidth(), bmp.getHeight());
                bmp = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(bmp.getWidth() * f)),
                        Math.max(1, Math.round(bmp.getHeight() * f)), true);
            }
            int deg = exifRotation(uri);
            if (deg != 0) bmp = rotated(bmp, deg);
            return bmp;
        } catch (Exception e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /** Lee la orientación EXIF (API 24+) por reflexión para seguir compilando con API 23. */
    private int exifRotation(Uri uri) {
        if (android.os.Build.VERSION.SDK_INT < 24) return 0;
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);
            Class<?> cls = Class.forName("android.media.ExifInterface");
            Object exif = cls.getConstructor(InputStream.class).newInstance(in);
            int o = (Integer) cls.getMethod("getAttributeInt", String.class, int.class)
                    .invoke(exif, "Orientation", 1);
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

    private static Bitmap rotated(Bitmap b, int deg) {
        Matrix m = new Matrix();
        m.postRotate(deg);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    private void rotate() {
        if (source == null) return;
        source = rotated(source, 90);
        updateLabels();
        regenerate();
    }

    private void regenerate() {
        if (source == null) return;
        final int gen = ++generation;
        final Bitmap src = source;
        final int gw = gridWidth();
        final int k = colors();
        final boolean clean = cleanupBox.isChecked();
        generated = null;
        updateButtons();
        progress.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override public void run() {
                // Reducir primero a 4 px por puntada para que el promedio sea rápido.
                int tw = Math.min(src.getWidth(), gw * 4);
                int th = Math.max(1, Math.round(src.getHeight() * (float) tw / src.getWidth()));
                Bitmap small = Bitmap.createScaledBitmap(src, tw, th, true);
                int[] px = new int[tw * th];
                small.getPixels(px, 0, tw, 0, 0, tw, th);
                final Pattern p = PatternGenerator.generate(px, tw, th, gw, k, clean);
                final Bitmap img = render(p);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (gen != generation) return;
                        progress.setVisibility(View.GONE);
                        generated = p;
                        preview.setImageBitmap(img);
                        info.setText(p.width + " × " + p.height + " puntadas · " + p.colorCount()
                                + " colores · " + p.totalStitches() + " puntos para colorear");
                        updateButtons();
                    }
                });
            }
        }).start();
    }

    /** Dibuja el patrón terminado (cruces de colores) para la vista previa. */
    private static Bitmap render(Pattern p) {
        int cell = Math.max(4, Math.min(12, 900 / Math.max(p.width, p.height)));
        Bitmap b = Bitmap.createBitmap(p.width * cell, p.height * cell, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFFFAF6EC);
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStrokeWidth(Math.max(1.5f, cell * 0.28f));
        s.setStrokeCap(Paint.Cap.ROUND);
        float in = cell * 0.15f;
        for (int y = 0; y < p.height; y++) {
            for (int x = 0; x < p.width; x++) {
                int col = p.cells[p.index(x, y)];
                if (col < 0) continue;
                s.setColor(p.palette[col]);
                float l = x * cell, t = y * cell;
                c.drawLine(l + in, t + in, l + cell - in, t + cell - in, s);
                c.drawLine(l + cell - in, t + in, l + in, t + cell - in, s);
            }
        }
        return b;
    }

    private void start() {
        if (generated == null) return;
        if (PatternStore.exists(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("¿Empezar un patrón nuevo?")
                    .setMessage("Se sustituirá el patrón guardado y su progreso.")
                    .setPositiveButton("Sí, empezar", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { launch(); }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        } else {
            launch();
        }
    }

    private void launch() {
        PatternStore.save(this, generated);
        startActivity(new Intent(this, PuntoCruzActivity.class));
    }
}
