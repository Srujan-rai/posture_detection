package com.example.body_posture_record_app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
    private WebView chartWebView;

    private long badPostureStartTime = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_posture_data);

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance();
        statusTextView = findViewById(R.id.statusTextView);
        logoutButton = findViewById(R.id.logoutButton);
        chartWebView = findViewById(R.id.webView);

        // Enable WebView settings
        chartWebView.getSettings().setJavaScriptEnabled(true);
        chartWebView.setWebViewClient(new WebViewClient());

        // Get current user's ID
        String currentUserId = firebaseAuth.getCurrentUser().getUid();

        // Firebase reference to "live" data
        DatabaseReference liveRef = FirebaseDatabase.getInstance("https://body-posture-record-app-73450-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("posture_logs")
                .child(currentUserId)
                .child("live");

        // Listen for real-time updates to posture data
        liveRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String status = snapshot.child("status").getValue(String.class);
                    String time = snapshot.child("time").getValue(String.class);

                    if (status != null && time != null) {
                        String displayText = "Your Current Body Posture: \n" + status + "\nTime: " + time;
                        statusTextView.setText(displayText);

                        if (status.equals("Bad")) {
                            if (badPostureStartTime == 0) {
                                badPostureStartTime = System.currentTimeMillis();
                            }
                            if ((System.currentTimeMillis() - badPostureStartTime) > 10000) {
                                showBadPosturePopup();
                            }
                        } else {
                            badPostureStartTime = 0;
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });

        // Firebase reference to "history" data
        DatabaseReference historyRef = FirebaseDatabase.getInstance("https://body-posture-record-app-73450-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("posture_logs")
                .child(currentUserId)
                .child("history");

        // Fetch history data to display in WebView
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
                // Handle error
            }
        });

        // Logout button functionality
        logoutButton.setOnClickListener(v -> {
            firebaseAuth.signOut();
            Intent intent = new Intent(PostureDataActivity.this, Login.class);
            startActivity(intent);
            finish();
        });
    }

    private void loadChartInWebView(List<String> times, List<String> statuses) {
        // Convert lists to JavaScript arrays
        String timeArray = new Gson().toJson(times);
        String statusArray = new Gson().toJson(statuses);

        // Load the HTML file from the assets folder
        chartWebView.loadUrl("file:///android_asset/barChart.html");

        // Inject the data into the WebView after the page has loaded
        chartWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Pass data arrays to the JavaScript function in the HTML file
                chartWebView.evaluateJavascript("updateChart(" + statusArray + ", " + timeArray + ");", null);
                Log.d("PostureChart", "Time Array: " + timeArray);
                Log.d("PostureChart", "Status Array: " + statusArray);

            }
        });
    }


    private void showBadPosturePopup() {
        new android.app.AlertDialog.Builder(PostureDataActivity.this)
                .setTitle("Bad Posture Alert")
                .setMessage("You have been in a bad posture for a long time.")
                .setPositiveButton("Okay", null)
                .show();
    }
}
