package com.example.body_posture_record_app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.body_posture_record_app.authentication.Login;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class PostureDataActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private TextView statusTextView;
    private Handler handler;
    private Runnable badPostureRunnable;
    private long badPostureStartTime = 0;

    private Button logoutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_posture_data);

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance();
        statusTextView = findViewById(R.id.statusTextView);
        logoutButton = findViewById(R.id.logoutButton);

        // Initialize handler for delaying the popup
        handler = new Handler();

        // Get current user's ID
        String currentUserId = firebaseAuth.getCurrentUser().getUid();

        // Firebase reference to posture_logs for the current user
        DatabaseReference databaseReference = FirebaseDatabase.getInstance("https://body-posture-record-app-73450-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("posture_logs").child(currentUserId);

        // Listen for real-time updates to posture data
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Check if there is data
                if (snapshot.exists()) {
                    // Get the status and time from Firebase
                    String status = snapshot.child("status").getValue(String.class);
                    String time = snapshot.child("time").getValue(String.class);

                    // Check if both status and time are not null
                    if (status != null && time != null) {
                        String displayText = "Your Current Body Posture: \n" + status + "\nTime: " + time;
                        statusTextView.setText(displayText);

                        // Check if the posture is "Bad"
                        if (status.equals("Bad")) {
                            if (badPostureStartTime == 0) {
                                // Start tracking time when "Bad" status is detected
                                badPostureStartTime = System.currentTimeMillis();
                            }

                            // Check if 10 seconds have passed since "Bad" status started
                            if ((System.currentTimeMillis() - badPostureStartTime) > 10000) {
                                // Show a popup after 10 seconds of bad posture
                                showBadPosturePopup();
                            }
                        } else {
                            // Reset the timer if the posture is "Good"
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

        logoutButton.setOnClickListener(v -> {
            // Log out the user from Firebase
            firebaseAuth.signOut();

            // Redirect to LoginActivity
            Intent intent = new Intent(PostureDataActivity.this, Login.class);
            startActivity(intent);

            // Finish this activity so the user can't go back to it after logout
            finish();
        });
    }

    private void showBadPosturePopup() {
        new android.app.AlertDialog.Builder(PostureDataActivity.this)
                .setTitle("Bad Posture Alert")
                .setMessage("You have been in a bad posture for long time")
                .setPositiveButton("Okay", null)
                .show();
    }

}
