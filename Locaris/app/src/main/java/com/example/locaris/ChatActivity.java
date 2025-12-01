package com.example.locaris;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ChatActivity extends AppCompatActivity {

    // ¡¡¡ IMPORTANTE: PON LA MISMA IP QUE EN MAINACTIVITY !!!
    // private static final String BASE_URL = "http://192.168.1.69:3000/";

    private EditText etMessage;
    private Button btnSend;
    private RecyclerView recycler;
    private ChatAdapter adapter;
    private MainActivity.ApiService api;

    private String myUuid;
    private String otherUuid;
    private Handler handler = new Handler(Looper.getMainLooper());

    // Polling: Busca mensajes nuevos cada 2 segundos
    private Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            fetchMessages();
            handler.postDelayed(this, 2000);
        }
    };

    // BORRA ESTA LINEA SI EXISTE:
    // private static final String BASE_URL = "http://192.168.1.69:3000/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // ... (código de recibir intents) ...

        // --- INICIO CORRECCIÓN IP DINÁMICA ---

        // 1. Recuperar la IP que escribiste en la pantalla de inicio
        android.content.SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        String savedIp = prefs.getString("server_ip", "192.168.1.69"); // Valor por defecto

        // 2. Construir la URL
        String dynamicBaseUrl = "http://" + savedIp + ":3000/";


        // Recibir IDs del MainActivity
        myUuid = getIntent().getStringExtra("MY_UUID");
        otherUuid = getIntent().getStringExtra("OTHER_UUID");

        setTitle("Chat con " + otherUuid);

        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send_chat);
        recycler = findViewById(R.id.recycler_chat);

        // Configurar Retrofit (Igual que en MainActivity)
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(dynamicBaseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(MainActivity.ApiService.class);

        // Configurar lista
        adapter = new ChatAdapter(myUuid);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());

        // Iniciar bucle de lectura
        handler.post(pollRunnable);
    }

    private void fetchMessages() {
        api.getConversation(myUuid, otherUuid).enqueue(new Callback<List<MainActivity.ChatMessage>>() {
            @Override
            public void onResponse(Call<List<MainActivity.ChatMessage>> call, Response<List<MainActivity.ChatMessage>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    adapter.setMessages(response.body());
                    recycler.scrollToPosition(adapter.getItemCount() - 1); // Bajar al último
                }
            }
            @Override public void onFailure(Call<List<MainActivity.ChatMessage>> call, Throwable t) {}
        });
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        etMessage.setText(""); // Limpiar campo

        api.sendMessage(new MainActivity.MessageBody(myUuid, otherUuid, text)).enqueue(new Callback<MainActivity.MessageResp>() {
            @Override
            public void onResponse(Call<MainActivity.MessageResp> call, Response<MainActivity.MessageResp> response) {
                if(response.isSuccessful()) fetchMessages(); // Actualizar inmediato
                else Toast.makeText(ChatActivity.this, "Error envío", Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(Call<MainActivity.MessageResp> call, Throwable t) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(pollRunnable); // Parar el bucle al salir
    }
}
