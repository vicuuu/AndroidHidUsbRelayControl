package gigacycle.usbrelaycontrol;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS = "relay_prefs";
    private static final String K1 = "relay_name_1";
    private static final String K2 = "relay_name_2";
    private static final String K3 = "relay_name_3";
    private static final String K4 = "relay_name_4";
    private static final String KLOG = "app_log";

    private EditText etLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        EditText e1 = findViewById(R.id.etName1);
        EditText e2 = findViewById(R.id.etName2);
        EditText e3 = findViewById(R.id.etName3);
        EditText e4 = findViewById(R.id.etName4);

        Button btnSave = findViewById(R.id.btnSave);
        Button btnDefaults = findViewById(R.id.btnDefaults);

        etLog = findViewById(R.id.etLogSettings);
        Button btnClearLog = findViewById(R.id.btnClearLog);

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);

        e1.setText(p.getString(K1, "Przekaźnik 1"));
        e2.setText(p.getString(K2, "Przekaźnik 2"));
        e3.setText(p.getString(K3, "Przekaźnik 3"));
        e4.setText(p.getString(K4, "Przekaźnik 4"));

        btnSave.setOnClickListener(v -> {
            p.edit()
                    .putString(K1, safe(e1.getText().toString(), "Przekaźnik 1"))
                    .putString(K2, safe(e2.getText().toString(), "Przekaźnik 2"))
                    .putString(K3, safe(e3.getText().toString(), "Przekaźnik 3"))
                    .putString(K4, safe(e4.getText().toString(), "Przekaźnik 4"))
                    .apply();
            finish();
        });

        btnDefaults.setOnClickListener(v -> {
            e1.setText("Przekaźnik 1");
            e2.setText("Przekaźnik 2");
            e3.setText("Przekaźnik 3");
            e4.setText("Przekaźnik 4");
        });

        btnClearLog.setOnClickListener(v -> {
            p.edit().putString(KLOG, "").apply();
            loadLog();
        });

        loadLog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLog();
    }

    private void loadLog() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String log = p.getString(KLOG, "");
        etLog.setText(log == null ? "" : log);
        etLog.post(() -> {
            int len = etLog.getText().length();
            if (len > 0) etLog.setSelection(len);
        });
    }

    private String safe(String s, String def) {
        if (s == null) return def;
        s = s.trim();
        return s.isEmpty() ? def : s;
    }
}
