package com.joseba.juegos.terminal;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.joseba.juegos.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Terminal sencilla sobre la shell del sistema. */
public class TerminalActivity extends Activity implements ShellSession.Listener {
    private static final int MAX_CHARS = 120000;
    private static final int GREEN = 0xFF7CFC7C, BLUE = 0xFF82B1FF, RED = 0xFFFF8A80;

    private final Handler main = new Handler(Looper.getMainLooper());
    private ShellSession shell;
    private TextView out;
    private ScrollView scroll;
    private EditText input;
    private TextView prompt;
    private final SpannableStringBuilder text = new SpannableStringBuilder();
    private final List<String> history = new ArrayList<String>();
    private int historyPos = 0;
    private String cwd = "~";
    private File home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        home = new File(getFilesDir(), "home");
        home.mkdirs();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101418);

        scroll = new ScrollView(this);
        out = new TextView(this);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextSize(13);
        out.setTextColor(0xFFE0E0E0);
        out.setTextIsSelectable(true);
        out.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        scroll.addView(out);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout line = new LinearLayout(this);
        line.setBackgroundColor(0xFF1A2026);
        line.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 4), 0);
        prompt = new TextView(this);
        prompt.setTypeface(Typeface.MONOSPACE);
        prompt.setTextColor(GREEN);
        prompt.setTextSize(13);
        prompt.setText("$ ");
        line.addView(prompt);
        input = new EditText(this);
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(14);
        input.setTextColor(0xFFFFFFFF);
        input.setSingleLine(true);
        input.setBackground(null);
        input.setHint("escribe una orden");
        input.setHintTextColor(0xFF607080);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {
                if (actionId == EditorInfo.IME_ACTION_SEND || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && e.getAction() == KeyEvent.ACTION_DOWN)) {
                    submit();
                    return true;
                }
                return false;
            }
        });
        line.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(line);

        // Fila de teclas difíciles de encontrar en el teclado del móvil.
        HorizontalScrollView keysScroll = new HorizontalScrollView(this);
        LinearLayout keys = new LinearLayout(this);
        keys.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 6));
        keys.addView(key("Ctrl+C", new View.OnClickListener() {
            @Override public void onClick(View v) { shell.interrupt(); append("^C\n", RED); }
        }));
        keys.addView(key("↑", new View.OnClickListener() { @Override public void onClick(View v) { historyMove(-1); } }));
        keys.addView(key("↓", new View.OnClickListener() { @Override public void onClick(View v) { historyMove(1); } }));
        for (final String s : new String[]{"|", "/", "-", "~", ">", "&", "*", "\"", "$"}) {
            keys.addView(key(s, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    int st = Math.max(0, input.getSelectionStart());
                    input.getText().insert(st, s);
                }
            }));
        }
        keys.addView(key("Limpiar", new View.OnClickListener() {
            @Override public void onClick(View v) { text.clear(); out.setText(text); }
        }));
        keysScroll.addView(keys);
        root.addView(keysScroll);

        setContentView(root);
        append("Terminal Linux · shell del sistema Android (" + System.getProperty("os.arch") + ")\n"
                + "Prueba: ls, cd, cat, ps, df, uname -a, getprop, ping -c 3 8.8.8.8\n"
                + "Carpeta personal: " + home.getAbsolutePath() + "\n\n", BLUE);
        startShell();
    }

    private Button key(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(14);
        b.setMinWidth(0);
        b.setMinimumWidth(Ui.dp(this, 44));
        b.setBackground(Ui.rounded(0xFF2E3A44, Ui.dp(this, 6)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 38));
        lp.rightMargin = Ui.dp(this, 4);
        b.setLayoutParams(lp);
        b.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        b.setOnClickListener(l);
        return b;
    }

    private void startShell() {
        shell = new ShellSession(this);
        try {
            shell.start(home);
        } catch (Exception e) {
            append("No se pudo arrancar la shell: " + e.getMessage() + "\n", RED);
        }
    }

    private void submit() {
        String cmd = input.getText().toString();
        input.setText("");
        append(promptText(), GREEN);
        append(cmd + "\n", 0xFFFFFFFF);
        if (cmd.trim().length() > 0 && (history.isEmpty() || !history.get(history.size() - 1).equals(cmd))) {
            history.add(cmd);
        }
        historyPos = history.size();
        if (cmd.trim().equals("clear")) {
            text.clear();
            out.setText(text);
        }
        shell.write(cmd + "\n");
    }

    private void historyMove(int d) {
        if (history.isEmpty()) return;
        historyPos = Math.max(0, Math.min(history.size(), historyPos + d));
        input.setText(historyPos < history.size() ? history.get(historyPos) : "");
        input.setSelection(input.getText().length());
    }

    private String promptText() {
        String c = cwd.startsWith(home.getAbsolutePath()) ? "~" + cwd.substring(home.getAbsolutePath().length()) : cwd;
        return c + " $ ";
    }

    private void append(CharSequence s, int color) {
        int start = text.length();
        text.append(s);
        text.setSpan(new ForegroundColorSpan(color), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (text.length() > MAX_CHARS) text.delete(0, text.length() - MAX_CHARS);
        out.setText(text);
        scroll.post(new Runnable() { @Override public void run() { scroll.fullScroll(View.FOCUS_DOWN); input.requestFocus(); } });
    }

    @Override public void onOutput(final String s) {
        main.post(new Runnable() { @Override public void run() { append(s, 0xFFE0E0E0); } });
    }

    @Override public void onPrompt(final int code, final String dir) {
        main.post(new Runnable() {
            @Override public void run() {
                cwd = dir;
                prompt.setText(promptText());
                prompt.setTextColor(code == 0 ? GREEN : RED);
            }
        });
    }

    @Override public void onExit(final int code) {
        main.post(new Runnable() {
            @Override public void run() {
                append("\n[La shell terminó (código " + code + "). Se abre una nueva.]\n\n", BLUE);
                startShell();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Copiar todo");
        menu.add(0, 2, 0, "Reiniciar shell");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("terminal", text.toString()));
            Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (item.getItemId() == 2) {
            shell.close();
            startShell();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shell != null) shell.close();
    }
}
