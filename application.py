import cv2
import mediapipe as mp
import numpy as np
import pyrebase
from datetime import datetime, timedelta
import tkinter as tk
from tkinter import ttk
from tkinter import messagebox
from PIL import Image, ImageTk

# Firebase configuration
firebase_config = {
    "apiKey": "AIzaSyAYTScDSbNOgDfmFoT_Yw2kJYRcBHVlcZc",
    "authDomain": "posture-detction.firebaseapp.com",
    "databaseURL": "https://posture-detction-default-rtdb.asia-southeast1.firebasedatabase.app",
    "storageBucket": "posture-detction.firebasestorage.app"
}

# Initialize Firebase
firebase = pyrebase.initialize_app(firebase_config)
db = firebase.database()

# Initialize MediaPipe Pose
mp_pose = mp.solutions.pose
pose = mp_pose.Pose()
mp_drawing = mp.solutions.drawing_utils

# Helper function to calculate angle between three points
def calculate_angle(a, b, c):
    a = np.array(a)
    b = np.array(b)
    c = np.array(c)

    radians = np.arctan2(c[1] - b[1], c[0] - b[0]) - np.arctan2(a[1] - b[1], a[0] - b[0])
    angle = np.abs(radians * 180.0 / np.pi)

    if angle > 180.0:
        angle = 360 - angle

    return angle

# Tkinter Application
class PostureApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Posture Detection Application")
        self.root.geometry("800x600")

        # Variables
        self.camera_index = tk.IntVar(value=0)
        self.running = False
        self.cap = None
        self.bad_posture_start_time = None
        self.last_update_time = datetime.now()

        # UI Setup
        self.setup_ui()

    def setup_ui(self):
        # Frame for controls
        control_frame = tk.Frame(self.root, bg="lightgray", height=100)
        control_frame.pack(side=tk.TOP, fill=tk.X)

        # Camera selection
        tk.Label(control_frame, text="Select Camera:", bg="lightgray").pack(side=tk.LEFT, padx=10)
        self.camera_dropdown = ttk.Combobox(control_frame, textvariable=self.camera_index, values=[0, 1, 2])
        self.camera_dropdown.pack(side=tk.LEFT, padx=5)

        # Start and Stop buttons
        self.start_button = tk.Button(control_frame, text="Start", command=self.start_detection, bg="green", fg="white")
        self.start_button.pack(side=tk.LEFT, padx=10)

        self.stop_button = tk.Button(control_frame, text="Stop", command=self.stop_detection, bg="red", fg="white")
        self.stop_button.pack(side=tk.LEFT, padx=10)

        # Canvas for video feed
        self.canvas = tk.Canvas(self.root, width=800, height=500)
        self.canvas.pack()

        # Label for displaying live posture status
        self.status_label = tk.Label(self.root, text="Posture Status: Not Detected", font=("Helvetica", 16))
        self.status_label.pack(pady=10)

    def start_detection(self):
        if self.running:
            messagebox.showinfo("Info", "Detection is already running.")
            return

        self.cap = cv2.VideoCapture(self.camera_index.get())
        if not self.cap.isOpened():
            messagebox.showerror("Error", "Failed to open camera.")
            return

        self.running = True
        self.process_frame()

    def stop_detection(self):
        self.running = False
        if self.cap:
            self.cap.release()
        self.canvas.delete("all")
        self.status_label.config(text="Posture Status: Not Detected")

    def process_frame(self):
        if not self.running:
            return

        ret, frame = self.cap.read()
        if not ret:
            messagebox.showerror("Error", "Failed to read frame from camera.")
            self.stop_detection()
            return

        # Process the frame
        image = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = pose.process(image)

        # Extract pose landmarks and draw on frame
        posture_status = "Good"
        try:
            landmarks = results.pose_landmarks.landmark

            # Get coordinates of key points
            nose = [landmarks[mp_pose.PoseLandmark.NOSE.value].x, landmarks[mp_pose.PoseLandmark.NOSE.value].y]
            left_shoulder = [landmarks[mp_pose.PoseLandmark.LEFT_SHOULDER.value].x,
                             landmarks[mp_pose.PoseLandmark.LEFT_SHOULDER.value].y]
            right_shoulder = [landmarks[mp_pose.PoseLandmark.RIGHT_SHOULDER.value].x,
                              landmarks[mp_pose.PoseLandmark.RIGHT_SHOULDER.value].y]
            left_hip = [landmarks[mp_pose.PoseLandmark.LEFT_HIP.value].x, landmarks[mp_pose.PoseLandmark.LEFT_HIP.value].y]
            right_hip = [landmarks[mp_pose.PoseLandmark.RIGHT_HIP.value].x, landmarks[mp_pose.PoseLandmark.RIGHT_HIP.value].y]

            # Calculate angles
            shoulder_angle = calculate_angle(left_shoulder, nose, right_shoulder)
            back_angle = calculate_angle(left_shoulder, left_hip, right_shoulder)

            # Determine live posture
            if 75 <= shoulder_angle <= 88 and 28 <= back_angle <= 34:
                posture_status = "Good"
                self.bad_posture_start_time = None
            else:
                posture_status = "Bad"
                if self.bad_posture_start_time is None:
                    self.bad_posture_start_time = datetime.now()

            # Update Firebase every 5 seconds
            if self.bad_posture_start_time:
                elapsed_time = (datetime.now() - self.bad_posture_start_time).total_seconds()
                if elapsed_time > 2:
                    posture_status = "Bad"
            if (datetime.now() - self.last_update_time).total_seconds() > 5:
                current_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                data = {"posture": posture_status, "time": current_time}
                db.child("PostureData").set(data)
                self.last_update_time = datetime.now()

        except:
            posture_status = "Not Detected"

        # Update posture status in UI
        self.status_label.config(text=f"Posture Status: {posture_status}")

        # Draw pose landmarks and posture status
        image.flags.writeable = True
        if results.pose_landmarks:
            mp_drawing.draw_landmarks(image, results.pose_landmarks, mp_pose.POSE_CONNECTIONS)

        # Convert to Tkinter Image
        img = Image.fromarray(image)
        imgtk = ImageTk.PhotoImage(image=img)

        self.canvas.create_image(0, 0, anchor=tk.NW, image=imgtk)
        self.canvas.image = imgtk

        # Schedule the next frame
        self.root.after(10, self.process_frame)

# Run the application
if __name__ == "__main__":
    root = tk.Tk()
    app = PostureApp(root)
    root.mainloop()
