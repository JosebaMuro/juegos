package com.joseba.juegos.chat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.joseba.juegos.ImageUtil;
import com.joseba.juegos.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat con IA 100 % local: el modelo con visión se ejecuta en el propio móvil con llama.cpp.
 * Internet solo se usa una vez, para descargar el modelo.
 */
public class ChatActivity extends Activity {
    private static final int REQ_GALLERY = 1, REQ_CAMERA = 2;
    private static final int MAX_IMAGES = 4;

    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout list;
    private ScrollView scroll;
    private EditText input;
    private Button send;
    private LinearLayout pendingRow;
    private HorizontalScrollView pendingScroll;
    private final List<Bitmap> pending = new ArrayList<Bitmap>();
    private JSONArray messages = new JSONArray();   // historial en formato OpenAI (texto + imágenes)
    private boolean busy;
    private Hardware hw;
    private LocalEngine engine;
    private ModelStore store;
    private ModelCatalog.Model model;
    private boolean engineReady;
    private volatile boolean cancelDownload;

    static final String SYSTEM = "Eres un asistente de charla que funciona sin conexión dentro de la app Juegos Reunidos, "
            + "en un móvil Android. Responde en el idioma del usuario (normalmente español), de forma clara y breve. "
            + "Si el usuario envía una foto, obsérvala con atención y di lo que ves cuando ayude a responder.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("chat", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F1EC);

        scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        pendingScroll = new HorizontalScrollView(this);
        pendingRow = new LinearLayout(this);
        pendingRow.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), 0);
        pendingScroll.addView(pendingRow);
        pendingScroll.setVisibility(View.GONE);
        root.addView(pendingScroll);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 8));
        bar.setBackgroundColor(Color.WHITE);
        bar.addView(iconButton("🖼", new View.OnClickListener() {
            @Override public void onClick(View v) { pickGallery(); }
        }));
        bar.addView(iconButton("📷", new View.OnClickListener() {
            @Override public void onClick(View v) { takePhoto(); }
        }));
        input = new EditText(this);
        input.setHint("Escribe un mensaje…");
        input.setMaxLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(Ui.rounded(0xFFF0EDE8, Ui.dp(this, 18)));
        input.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ilp.leftMargin = ilp.rightMargin = Ui.dp(this, 4);
        bar.addView(input, ilp);
        send = iconButton("➤", new View.OnClickListener() {
            @Override public void onClick(View v) { sendMessage(); }
        });
        send.setBackground(Ui.rounded(0xFFD97757, Ui.dp(this, 22)));
        send.setTextColor(Color.WHITE);
        bar.addView(send);
        root.addView(bar);

        setContentView(root);
        hw = new Hardware(this);
        engine = new LocalEngine(this, hw);
        store = new ModelStore(this);
        model = ModelCatalog.byId(prefs.getString("model", ""));
        if (model == null) model = ModelCatalog.recommended(hw);
        bubble("IA local: todo se procesa en este móvil, sin enviar nada a Internet.\n\nTu hardware: " + hw.summary()
                + "\nModelo elegido: " + model.name, false, null);
        setBusy(true);
        prepare();
    }

    // ------------------------------------------------------------ modelo y motor

    private void prepare() {
        engineReady = false;
        setBusy(true);
        if (!store.isReady(model)) {
            askDownload();
            return;
        }
        final TextView status = bubble("Arrancando el motor…", false, null);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    engine.start(store.modelFile(model), store.mmprojFile(model), new LocalEngine.Listener() {
                        @Override public void onStatus(final String t) {
                            main.post(new Runnable() { @Override public void run() { status.setText(t); } });
                        }
                    });
                    main.post(new Runnable() {
                        @Override public void run() {
                            engineReady = true;
                            setBusy(false);
                            status.setText("Listo. Motor: llama.cpp en " + LocalEngine.describe(engine.variant())
                                    + " con " + hw.fastCores + " hilos. Escríbeme o mándame una foto.");
                            if (getActionBar() != null) getActionBar().setSubtitle(model.name);
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() { status.setText("⚠ " + e.getMessage()); }
                    });
                }
            }
        }).start();
    }

    private void askDownload() {
        new AlertDialog.Builder(this)
                .setTitle("Descargar " + model.name)
                .setMessage("Para funcionar sin conexión hay que descargar el modelo una vez (unos "
                        + String.format(java.util.Locale.ROOT, "%.1f", model.approxGb) + " GB). Mejor con Wi-Fi.\n\n"
                        + "Se ha elegido según la memoria de tu móvil (" + String.format(java.util.Locale.ROOT, "%.1f", hw.ramGb())
                        + " GB). Puedes cambiarlo en el menú.")
                .setPositiveButton("Descargar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { download(); }
                })
                .setNegativeButton("Ahora no", null)
                .show();
    }

    private void download() {
        cancelDownload = false;
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Descargando " + model.name);
        pd.setMessage("Conectando…");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(1000);
        pd.setCancelable(false);
        pd.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancelar", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int w) { cancelDownload = true; }
        });
        pd.show();
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final ModelCatalog.Model m = model;
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                try {
                    store.download(m, new ModelStore.Progress() {
                        @Override public void onProgress(final String what, final long done, final long total) {
                            main.post(new Runnable() {
                                @Override public void run() {
                                    pd.setMessage(what + ": " + (done >> 20) + " MB" + (total > 0 ? " de " + (total >> 20) + " MB" : ""));
                                    if (total > 0) pd.setProgress((int) (1000 * done / total));
                                }
                            });
                        }
                        @Override public boolean cancelled() { return cancelDownload; }
                    });
                } catch (Exception e) {
                    err = e.getMessage();
                }
                final String error = err;
                main.post(new Runnable() {
                    @Override public void run() {
                        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        pd.dismiss();
                        if (error != null) {
                            bubble("⚠ " + error + "\nLo ya descargado se conserva; vuelve a intentarlo desde el menú.", false, null);
                        } else {
                            prepare();
                        }
                    }
                });
            }
        }).start();
    }

    private Button iconButton(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(18);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(null);
        b.setOnClickListener(l);
        int s = Ui.dp(this, 44);
        b.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return b;
    }


    // ------------------------------------------------------------ interfaz

    private TextView bubble(String text, boolean user, List<Bitmap> images) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(user ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.bottomMargin = Ui.dp(this, 8);
        if (images != null) {
            for (Bitmap b : images) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(b);
                iv.setAdjustViewBounds(true);
                iv.setMaxHeight(Ui.dp(this, 200));
                iv.setMaxWidth(Ui.dp(this, 240));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = Ui.dp(this, 4);
                wrap.addView(iv, lp);
            }
        }
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextIsSelectable(true);
        tv.setTextColor(user ? Color.WHITE : 0xFF2B2B2B);
        tv.setBackground(Ui.rounded(user ? 0xFFD97757 : Color.WHITE, Ui.dp(this, 14)));
        tv.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        tv.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.82f));
        if (text.length() > 0) wrap.addView(tv);
        list.addView(wrap, wlp);
        scroll.post(new Runnable() { @Override public void run() { scroll.fullScroll(View.FOCUS_DOWN); } });
        return tv;
    }

    private void refreshPending() {
        pendingRow.removeAllViews();
        for (int i = 0; i < pending.size(); i++) {
            final int idx = i;
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(pending.get(i));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            int s = Ui.dp(this, 64);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.rightMargin = Ui.dp(this, 6);
            iv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    pending.remove(idx);
                    refreshPending();
                }
            });
            pendingRow.addView(iv, lp);
        }
        pendingScroll.setVisibility(pending.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------ imágenes

    private void pickGallery() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(i, "Elige fotos"), REQ_GALLERY);
        } catch (Exception e) {
            Toast.makeText(this, "No hay app de galería", Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        PhotoProvider.photoFile(this).delete();
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, PhotoProvider.PHOTO_URI);
        i.setClipData(ClipData.newRawUri("foto", PhotoProvider.PHOTO_URI));
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            Toast.makeText(this, "No hay app de cámara", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        final List<Uri> uris = new ArrayList<Uri>();
        if (requestCode == REQ_CAMERA) {
            uris.add(Uri.fromFile(PhotoProvider.photoFile(this)));
        } else if (requestCode == REQ_GALLERY && data != null) {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
        }
        new Thread(new Runnable() {
            @Override public void run() {
                for (Uri u : uris) {
                    try {
                        final Bitmap b = ImageUtil.load(ChatActivity.this, u, 768);
                        main.post(new Runnable() {
                            @Override public void run() {
                                if (pending.size() >= MAX_IMAGES) {
                                    Toast.makeText(ChatActivity.this, "Máximo " + MAX_IMAGES + " fotos por mensaje", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                pending.add(b);
                                refreshPending();
                            }
                        });
                    } catch (final Exception e) {
                        main.post(new Runnable() {
                            @Override public void run() {
                                Toast.makeText(ChatActivity.this, "No se pudo abrir la imagen: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private static String toBase64Jpeg(Bitmap b) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 85, bo);
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    // ------------------------------------------------------------ envío

    private void sendMessage() {
        if (busy || !engineReady) return;
        final String text = input.getText().toString().trim();
        if (text.length() == 0 && pending.isEmpty()) return;

        final List<Bitmap> imgs = new ArrayList<Bitmap>(pending);
        pending.clear();
        refreshPending();
        input.setText("");
        bubble(text, true, imgs);
        final TextView answer = bubble("…", false, null);
        setBusy(true);

        new Thread(new Runnable() {
            @Override public void run() {
                String shown;
                try {
                    JSONArray content = new JSONArray();
                    for (Bitmap b : imgs) {
                        content.put(new JSONObject().put("type", "image_url").put("image_url",
                                new JSONObject().put("url", "data:image/jpeg;base64," + toBase64Jpeg(b))));
                    }
                    content.put(new JSONObject().put("type", "text")
                            .put("text", text.length() > 0 ? text : "¿Qué ves en esta imagen?"));
                    JSONObject userMsg = new JSONObject().put("role", "user").put("content", content);
                    messages.put(userMsg);
                    final StringBuilder sb = new StringBuilder();
                    final long[] last = {0};
                    String full = engine.chat(buildRequest(), new LocalEngine.TokenSink() {
                        @Override public void onToken(String piece) {
                            sb.append(piece);
                            long now = System.currentTimeMillis();
                            if (now - last[0] > 120) {
                                last[0] = now;
                                final String cur = sb.toString();
                                main.post(new Runnable() { @Override public void run() { answer.setText(cur); } });
                            }
                        }
                    });
                    messages.put(new JSONObject().put("role", "assistant").put("content", full));
                    shown = full.trim().length() > 0 ? full.trim() : "(sin respuesta)";
                } catch (Exception e) {
                    // Quitar el mensaje del usuario para no dejar el historial a medias.
                    if (messages.length() > 0) messages.remove(messages.length() - 1);
                    shown = "⚠ " + e.getMessage();
                    if (!engine.isRunning()) shown += "\nEl motor se ha detenido. Usa el menú → Reiniciar motor.";
                }
                final String s = shown;
                main.post(new Runnable() {
                    @Override public void run() {
                        answer.setText(s);
                        setBusy(false);
                        scroll.post(new Runnable() { @Override public void run() { scroll.fullScroll(View.FOCUS_DOWN); } });
                    }
                });
            }
        }).start();
    }

    /**
     * El contexto del modelo local es pequeño (4096 tokens): se envían solo los últimos
     * mensajes y las fotos de los dos últimos turnos del usuario.
     */
    private JSONArray buildRequest() throws Exception {
        JSONArray out = new JSONArray();
        out.put(new JSONObject().put("role", "system").put("content", SYSTEM));
        int start = Math.max(0, messages.length() - 8);
        int userTurnsFromEnd = 0;
        JSONObject[] picked = new JSONObject[messages.length() - start];
        for (int i = messages.length() - 1; i >= start; i--) {
            JSONObject m = messages.getJSONObject(i);
            if ("user".equals(m.getString("role"))) {
                userTurnsFromEnd++;
                if (userTurnsFromEnd > 2 && m.get("content") instanceof JSONArray) {
                    JSONArray c = m.getJSONArray("content"), keep = new JSONArray();
                    for (int k = 0; k < c.length(); k++) {
                        JSONObject part = c.getJSONObject(k);
                        if ("text".equals(part.optString("type"))) keep.put(part);
                    }
                    m = new JSONObject().put("role", "user").put("content", keep);
                }
            }
            picked[i - start] = m;
        }
        // Debe empezar por un mensaje del usuario.
        int first = 0;
        while (first < picked.length && !"user".equals(picked[first].getString("role"))) first++;
        for (int i = first; i < picked.length; i++) out.put(picked[i]);
        return out;
    }

    private void setBusy(boolean b) {
        busy = b;
        send.setEnabled(!b);
        send.setAlpha(b ? 0.4f : 1f);
    }

    // ------------------------------------------------------------ ajustes

    private void chooseModel() {
        final ModelCatalog.Model rec = ModelCatalog.recommended(hw);
        final String[] labels = new String[ModelCatalog.ALL.size()];
        int checked = 0;
        for (int i = 0; i < labels.length; i++) {
            ModelCatalog.Model m = ModelCatalog.ALL.get(i);
            String l = m.name + String.format(java.util.Locale.ROOT, " · %.1f GB", m.approxGb);
            if (m == rec) l += " · recomendado";
            if (store.isReady(m)) l += " · descargado";
            else if (hw.ramGb() < m.minRamGb) l += " · puede ir justo de memoria";
            labels[i] = l;
            if (m == model) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Modelo de IA local")
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        d.dismiss();
                        ModelCatalog.Model m = ModelCatalog.ALL.get(which);
                        if (m == model && engineReady) return;
                        model = m;
                        prefs.edit().putString("model", m.id).apply();
                        engine.stop();
                        messages = new JSONArray();
                        if (getActionBar() != null) getActionBar().setSubtitle(m.name);
                        prepare();
                    }
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void showEngineInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Hardware: ").append(hw.summary()).append("\n\n");
        sb.append("Motor: llama.cpp ").append(engine.isRunning() ? "en " + LocalEngine.describe(engine.variant()) : "(parado)").append("\n");
        sb.append("Hilos: ").append(hw.fastCores).append(" (solo núcleos rápidos)\n");
        sb.append("Variantes posibles: ").append(hw.engineCandidates()).append("\n");
        sb.append("Modelo: ").append(model.name).append(store.isReady(model) ? " (descargado)" : " (sin descargar)").append("\n\n");
        sb.append("Últimas líneas del motor:\n").append(engine.tail(15));
        TextView tv = new TextView(this);
        tv.setText(sb.toString());
        tv.setTextIsSelectable(true);
        tv.setTextSize(12);
        tv.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), 0);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this).setTitle("Motor de IA").setView(sv).setPositiveButton("Cerrar", null).show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("¿Borrar " + model.name + "?")
                .setMessage("Libera espacio. Habrá que descargarlo otra vez para usarlo.")
                .setPositiveButton("Borrar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        engine.stop();
                        store.delete(model);
                        prepare();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        engine.stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Nueva conversación");
        menu.add(0, 2, 0, "Elegir modelo");
        menu.add(0, 3, 0, "Información del motor");
        menu.add(0, 4, 0, "Reiniciar motor");
        menu.add(0, 5, 0, "Borrar modelo descargado");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            if (busy) return true;
            messages = new JSONArray();
            list.removeAllViews();
            bubble("Conversación nueva. ¿En qué te ayudo?", false, null);
            return true;
        }
        if (item.getItemId() == 2) { chooseModel(); return true; }
        if (item.getItemId() == 3) { showEngineInfo(); return true; }
        if (item.getItemId() == 4) { engine.stop(); prepare(); return true; }
        if (item.getItemId() == 5) { confirmDelete(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
