package com.example.body_posture_record_app;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.body_posture_record_app.authentication.Login;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class PostureDataActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private TextView statusTextView;
    private Button logoutButton;
    private Button viewHistoryButton;
    private WebView chartWebView;

    public static final String CHANNEL_ID = "PostureAlertChannel";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_posture_data);

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance();
        statusTextView = findViewById(R.id.statusTextView);
        logoutButton = findViewById(R.id.logoutButton);
        viewHistoryButton = findViewById(R.id.viewHistoryButton);
        chartWebView = findViewById(R.id.webView);

        // Enable WebView settings
        chartWebView.getSettings().setJavaScriptEnabled(true);
        chartWebView.setWebViewClient(new WebViewClient());

        // Get current user's ID
        String currentUserId = firebaseAuth.getCurrentUser().getUid();

        createNotificationChannel();

        // Monitor live posture data
        monitorLiveData(currentUserId);

        // Monitor notification tag for posture alerts
        monitorNotificationTag(currentUserId);

        // Load history data into the WebView
        loadHistoryData(currentUserId);

        // Handle view history button click
        viewHistoryButton.setOnClickListener(v -> {
            Intent intent = new Intent(PostureDataActivity.this, HistoryRecordsActivity.class);
            startActivity(intent);
        });

        // Handle logout button click
        logoutButton.setOnClickListener(v -> {
            firebaseAuth.signOut();
            Intent intent = new Intent(PostureDataActivity.this, Login.class);
            startActivity(intent);
            finish();
        });
    }

    private void monitorLiveData(String userId) {
        DatabaseReference liveRef = FirebaseDatabase.getInstance("https://body-posture-record-app-73450-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("posture_logs")
                .child(userId)
                .child("live");

        liveRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String status = snapshot.child("status").getValue(String.class);
                    String time = snapshot.child("time").getValue(String.class);

                    if (status != null && time != null) {
                        String displayText = "Your Current Body Posture: \n" + status + "\nTime: " + time;
                        statusTextView.setText(displayText);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("LiveDataError", "Error: " + error.getMessage());
            }
        });
    }

    private void monitorNotificationTag(String userId) {
        DatabaseReference notificationRef = FirebaseDatabase.getInstance("https://body-posture-record-app-73450-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("posture_logs")
                .child(userId)
                .child("notification");

        notificationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean notification = snapshot.getValue(Boolean.class);
                if (notification != null && notification) {
                    showBadPosturePopup();
                    showNotificationBar();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("NotificationError", "Error: " + error.getMessage());
            }
        });
    }

    private void loadHistoryData(String userId) {
        DatabaseReference historyRef = FirebaseDatabase.getInstance("https://body-posture-record-app-73450-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("posture_logs")
                .child(userId)
                .child("history");

        historyRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> times = new ArrayList<>();
                List<String> statuses = new ArrayList<>();

                for (DataSnapshot historySnapshot : snapshot.getChildren()) {
                    String status = historySnapshot.child("status").getValue(String.class);
                    String time = historySnapshot.child("time").getValue(String.class);

                    if (status != null && time != null) {
                        statuses.add(status);
                        times.add(time);
                    }
                }

                loadChartInWebView(times, statuses);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("HistoryDataError", "Error: " + error.getMessage());
            }
        });
    }

    private void loadChartInWebView(List<String> times, List<String> statuses) {
        String timeArray = new Gson().toJson(times);
        String statusArray = new Gson().toJson(statuses);

        chartWebView.loadUrl("file:///android_asset/barChart.html");

        chartWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                chartWebView.evaluateJavascript("updateChart(" + statusArray + ", " + timeArray + ");", null);
                Log.d("PostureChart", "Time Array: " + timeArray);
                Log.d("PostureChart", "Status Array: " + statusArray);
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Check for necessary permissions (though not required in most cases)
            try {
                CharSequence name = "Posture Alerts";
                String description = "Channel for bad posture notifications";
                int importance = NotificationManager.IMPORTANCE_HIGH;
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
                channel.setDescription(description);

                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                }
            } catch (SecurityException e) {
                Log.e("NotificationChannel", "Error creating notification channel: " + e.getMessage());
            }
        }
    }

    private void showNotificationBar() {
        // Check for notification permission on Android 13+ (API level 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Request permission if not granted
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
                return;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bad_posture) // Add your app's icon here
                .setContentTitle("Bad Posture Alert")
                .setContentText("You have been in a bad posture for too long.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true); // Dismiss the notification when clicked

        // Get an instance of NotificationManager
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // Show the notification
        notificationManager.notify(0, builder.build());
    }

    private void showBadPosturePopup() {
        new android.app.AlertDialog.Builder(PostureDataActivity.this)
                .setTitle("Bad Posture Alert")
                .setMessage("You have been in a bad posture for a long time.")
                .setPositiveButton("Okay", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, show the notification
                showNotificationBar();
            } else {
                // Permission denied, you may want to show a message to the user explaining why it's needed
                Log.e("NotificationPermission", "Permission denied for notifications.");
            }
        }
    }
}