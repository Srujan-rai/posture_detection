package com.example.body_posture_record_app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.body_posture_record_app.authentication.Login;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.Map;

public class HistoryRecordsActivity extends AppCompatActivity {

    private TableLayout historyTable;

    private FirebaseAuth firebaseAuth;

    private Button backButton;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_records);

        historyTable = findViewById(R.id.historyTable);

        backButton = findViewById(R.id.backButton);

        // Fetch history records from Firebase
        fetchHistoryRecords();

        backButton.setOnClickListener(v -> {
            // Navigate back to PostureDataActivity
            Intent intent = new Intent(HistoryRecordsActivity.this, PostureDataActivity.class);
            startActivity(intent);
            finish();  // Finish current activity to remove it from the stack
        });
    }

    private void fetchHistoryRecords() {
        firebaseAuth = FirebaseAuth.getInstance();
        if (firebaseAuth.getCurrentUser() == null) {
            // Redirect to Login if user is not authenticated
            startActivity(new Intent(HistoryRecordsActivity.this, Login.class));
            finish();
            return;
        }

        String currentUserId = firebaseAuth.getCurrentUser().getUid();

        DatabaseReference databaseReference = FirebaseDatabase.getInstance("https://body-posture-record-app-73450-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("posture_logs")
                .child(currentUserId)
                .child("history");

        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot record : snapshot.getChildren()) {
                    Map<String, String> data = (Map<String, String>) record.getValue();

                    if (data != null) {
                        String time = data.get("time");
                        String status = data.get("status");
                        addRowToTable(time, status);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle database errors
            }
        });
    }


    private void addRowToTable(String time, String status) {
        TableRow row = new TableRow(this);

        TextView timeView = new TextView(this);
        timeView.setText(time);
        timeView.setPadding(8, 8, 8, 8);
        timeView.setBackground(createCellBorder()); // Add border
        timeView.setTextColor(Color.BLACK);
        timeView.setGravity(Gravity.CENTER); // Center alignment


        TextView statusView = new TextView(this);
        statusView.setText(status);
        statusView.setPadding(8, 8, 8, 8);
        statusView.setTextColor(Color.BLACK);
        statusView.setBackground(createCellBorder()); // Add border
        statusView.setGravity(Gravity.CENTER); // Center alignment

        row.addView(timeView);
        row.addView(statusView);

        historyTable.addView(row);
    }

    private Drawable createCellBorder() {
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.parseColor("#eaeaea")); // Background color of the cell
        border.setStroke(2, Color.BLACK); // Border width and color
        return border;
    }
}
