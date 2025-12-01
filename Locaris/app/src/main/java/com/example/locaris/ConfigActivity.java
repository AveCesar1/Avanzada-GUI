package com.example.locaris;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class ConfigActivity extends AppCompatActivity {

    private EditText etIp;
    private Button btnConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        etIp = findViewById(R.id.et_ip);
        btnConnect = findViewById(R.id.btn_connect);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Cargar última IP usada o una por defecto
        String lastIp = prefs.getString("server_ip", "192.168.1.69");
        etIp.setText(lastIp);

        btnConnect.setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            if (ip.isEmpty()) return;

            // Guardar IP nueva
            prefs.edit().putString("server_ip", ip).apply();

            // Iniciar la app principal
            Intent intent = new Intent(ConfigActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // Cerrar esta pantalla para que no se pueda volver atrás
        });
    }
}
