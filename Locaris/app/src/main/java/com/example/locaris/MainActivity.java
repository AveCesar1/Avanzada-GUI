package com.example.locaris;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

// Google Location Services
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.gson.annotations.SerializedName;

// --- OSMDROID IMPORTS ---
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// --- RETROFIT IMPORTS CORRECTOS ---
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public class MainActivity extends AppCompatActivity {

    // ---------- CONFIG ----------
    // CAMBIA ESTO POR LA IP DE TU PC (ej. 192.168.1.x)
    // private static final String BASE_URL = "http://192.168.1.69:3000/";
    private static final String PREF_DEVICE_UUID = "pref_device_uuid";
    private static final String TAG = "LocarisApp";
    private static final long LOCATION_INTERVAL_MS = 5000;
    private static final long POLL_OTHER_MS = 5000;
    private static final double DISTANCE_THRESHOLD_METERS = 200.0;
    // ----------------------------

    private MapView mMap;
    private FusedLocationProviderClient fused;
    private LocationCallback locationCallback;
    private Marker myMarker, otherMarker;
    private TextView tvStatus;
    private Button btnMsg;
    private ApiService api;
    private String deviceUuid;
    private String otherDeviceUuid = "dev-B";
    private Handler handler = new Handler(Looper.getMainLooper());
    private Location myLastLocation = null;

    private final Runnable pollOtherRunnable = new Runnable() {
        @Override
        public void run() {
            pollOtherDevice();
            handler.postDelayed(this, POLL_OTHER_MS);
        }
    };

    private ActivityResultLauncher<String[]> locationPermissionRequest =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                Boolean coarse = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                if (fine || coarse) {
                    startLocationUpdates();
                } else {
                    Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        btnMsg = findViewById(R.id.btn_msg);
        btnMsg.setEnabled(false);

        mMap = findViewById(R.id.map);
        mMap.setMultiTouchControls(true);
        mMap.getController().setZoom(18.0);
        mMap.getController().setCenter(new GeoPoint(40.416775, -3.703790));

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();

        // Recuperar IP guardada
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String savedIp = prefs.getString("server_ip", "192.168.1.69"); // Default por si acaso
        String dynamicBaseUrl = "http://" + savedIp + ":3000/"; // Asumimos puerto 3000 siempre

        // Configurar Retrofit con la IP dinámica
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(dynamicBaseUrl) // <--- CAMBIO AQUÍ
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(ApiService.class);

        // después de api = retrofit.create(ApiService.class);
        api.listDevices().enqueue(new Callback<List<DeviceResp>>() {
            @Override
            public void onResponse(Call<List<DeviceResp>> call, Response<List<DeviceResp>> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                for (DeviceResp d : response.body()) {
                    if (d.device_uuid != null && !d.device_uuid.equals(deviceUuid)) {
                        otherDeviceUuid = d.device_uuid;
                        runOnUiThread(() -> tvStatus.setText("Device: " + deviceUuid + "  |  Otro: " + otherDeviceUuid));
                        Log.i(TAG, "Auto-selected otherDeviceUuid = " + otherDeviceUuid);
                        break;
                    }
                }
            }
            @Override
            public void onFailure(Call<List<DeviceResp>> call, Throwable t) { Log.w(TAG, "listDevices failed", t); }
        });

        deviceUuid = prefs.getString(PREF_DEVICE_UUID, null);
        if (deviceUuid == null) {
            deviceUuid = "dev-" + UUID.randomUUID().toString().substring(0,8);
            prefs.edit().putString(PREF_DEVICE_UUID, deviceUuid).apply();
        }
        tvStatus.setText("Device: " + deviceUuid);

        // REGISTRO (Corregido para usar retrofit2.Callback)
        api.register(new RegisterBody(deviceUuid, "Phone-" + android.os.Build.MODEL))
                .enqueue(new Callback<DeviceResp>() {
                    @Override
                    public void onResponse(@NonNull Call<DeviceResp> call, @NonNull Response<DeviceResp> response) {
                        Log.i(TAG, "Registered: " + deviceUuid);
                    }

                    @Override
                    public void onFailure(@NonNull Call<DeviceResp> call, @NonNull Throwable t) {
                        Log.w(TAG, "Register error", t);
                    }
                });

        fused = LocationServices.getFusedLocationProviderClient(this);
        // Reemplaza el listener viejo con este:
        btnMsg.setOnClickListener(v -> {
            // Abrir la pantalla de chat
            android.content.Intent intent = new android.content.Intent(MainActivity.this, ChatActivity.class);
            intent.putExtra("MY_UUID", deviceUuid);
            intent.putExtra("OTHER_UUID", otherDeviceUuid);
            startActivity(intent);
        });

        requestLocationPermissionIfNeeded();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMap.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMap.onPause();
    }

    private void requestLocationPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        } else {
            locationPermissionRequest.launch(new String[]{ Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION });
        }
    }

    private void startLocationUpdates() {
        LocationRequest request = LocationRequest.create();
        request.setInterval(LOCATION_INTERVAL_MS);
        request.setFastestInterval(2000);
        request.setPriority(Priority.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location loc = locationResult.getLastLocation();
                if (loc != null) {
                    myLastLocation = loc;
                    updateMyLocationOnMap(loc);
                    sendLocationToServer(loc);
                }
            }
        };

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        }
        handler.post(pollOtherRunnable);
    }

    private void updateMyLocationOnMap(Location loc) {
        GeoPoint p = new GeoPoint(loc.getLatitude(), loc.getLongitude());
        runOnUiThread(() -> {
            if (myMarker == null) {
                myMarker = new Marker(mMap);
                myMarker.setTitle("Yo");
                myMarker.setPosition(p);
                myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                mMap.getOverlays().add(myMarker);
                mMap.getController().animateTo(p);
            } else {
                myMarker.setPosition(p);
            }
            mMap.invalidate();
            tvStatus.setText("Mi ubicación: " + String.format("%.5f, %.5f", p.getLatitude(), p.getLongitude()));
        });
    }

    private void sendLocationToServer(Location loc) {
        api.updateLocation(deviceUuid, new UpdateLoc(loc.getLatitude(), loc.getLongitude()))
                .enqueue(new Callback<DeviceResp>() {
                    @Override public void onResponse(@NonNull Call<DeviceResp> call, @NonNull Response<DeviceResp> response) {}
                    @Override public void onFailure(@NonNull Call<DeviceResp> call, @NonNull Throwable t) {}
                });
    }

    private void pollOtherDevice() {
        api.getDevice(otherDeviceUuid).enqueue(new Callback<DeviceResp>() {
            @Override
            public void onResponse(@NonNull Call<DeviceResp> call, @NonNull Response<DeviceResp> response) {
                if (!response.isSuccessful() || response.body() == null) return;

                DeviceResp d = response.body();
                if (d.last_lat == null || d.last_lng == null) {
                    runOnUiThread(() -> {
                        btnMsg.setEnabled(false);
                        if (tvStatus.getText().toString().contains("Distancia")) {
                            tvStatus.setText("Esperando ubicación de " + otherDeviceUuid);
                        }
                    });
                    return;
                }

                GeoPoint p = new GeoPoint(d.last_lat, d.last_lng);
                runOnUiThread(() -> {
                    if (otherMarker == null) {
                        otherMarker = new Marker(mMap);
                        otherMarker.setTitle("Otro");
                        otherMarker.setPosition(p);
                        otherMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        mMap.getOverlays().add(otherMarker);
                    } else {
                        otherMarker.setPosition(p);
                    }
                    mMap.invalidate();

                    if (myLastLocation != null) {
                        float[] results = new float[1];
                        Location.distanceBetween(myLastLocation.getLatitude(), myLastLocation.getLongitude(),
                                p.getLatitude(), p.getLongitude(), results);
                        float dist = results[0];

                        tvStatus.setText(String.format("Distancia a %s: %.1f m", otherDeviceUuid, dist));
                        boolean canChat = dist <= DISTANCE_THRESHOLD_METERS;
                        btnMsg.setEnabled(canChat);
                        if(canChat) {
                            btnMsg.setText("CHATEAR (CERCA)");
                        } else {
                            btnMsg.setText("DEMASIADO LEJOS");
                        }
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<DeviceResp> call, @NonNull Throwable t) { }
        });
    }

    private void sendMessage(String text) {
        api.sendMessage(new MessageBody(deviceUuid, otherDeviceUuid, text)).enqueue(new Callback<MessageResp>() {
            @Override
            public void onResponse(@NonNull Call<MessageResp> call, @NonNull Response<MessageResp> response) {
                Toast.makeText(MainActivity.this, response.isSuccessful() ? "Mensaje enviado" : "Error envío", Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(@NonNull Call<MessageResp> call, @NonNull Throwable t) { }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationCallback != null) fused.removeLocationUpdates(locationCallback);
        handler.removeCallbacksAndMessages(null);
    }

    // ---------------- Retrofit interfaces & models ----------------
    interface ApiService {
        @POST("register") Call<DeviceResp> register(@Body RegisterBody body);
        @PUT("devices/{uuid}/location") Call<DeviceResp> updateLocation(@Path("uuid") String uuid, @Body UpdateLoc body);
        @GET("devices/{uuid}") Call<DeviceResp> getDevice(@Path("uuid") String uuid);
        @POST("messages") Call<MessageResp> sendMessage(@Body MessageBody body);
        @GET("messages/{u1}/{u2}")
        Call<List<ChatMessage>> getConversation(@Path("u1") String user1, @Path("u2") String user2);
        @GET("devices") Call<List<DeviceResp>> listDevices();
    }


    static class RegisterBody {
        @SerializedName("device_uuid") String device_uuid;
        @SerializedName("display_name") String display_name;
        RegisterBody(String u, String d) { device_uuid = u; display_name = d; }
    }
    static class UpdateLoc {
        @SerializedName("lat") Double lat;
        @SerializedName("lng") Double lng;
        UpdateLoc(Double lat, Double lng) { this.lat = lat; this.lng = lng; }
    }
    static class MessageBody {
        @SerializedName("from") String from;
        @SerializedName("to") String to;
        @SerializedName("text") String text;
        MessageBody(String f, String t, String txt) { from = f; to = t; text = txt; }
    }
    static class DeviceResp {
        @SerializedName("id") Integer id;
        @SerializedName("device_uuid") String device_uuid;
        @SerializedName("display_name") String display_name;
        @SerializedName("last_lat") Double last_lat;
        @SerializedName("last_lng") Double last_lng;
        @SerializedName("last_seen") String last_seen;
    }
    static class MessageResp {
        @SerializedName("ok") Boolean ok;
        @SerializedName("message") Object message;
    }

    // Clase para mapear lo que devuelve la Base de Datos
    static class ChatMessage {
        @SerializedName("from_device") String from_device;
        @SerializedName("to_device") String to_device;
        @SerializedName("text") String text;
        @SerializedName("created_at") String created_at;
    }
}
