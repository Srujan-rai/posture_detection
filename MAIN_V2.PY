import cv2
import mediapipe as mp
import numpy as np
import pyrebase
from datetime import datetime
import tkinter as tk
from tkinter import ttk, messagebox
from PIL import Image, ImageTk
import threading
import queue

# Firebase configuration
firebase_config = {
    "apiKey": "AIzaSyBLqgsnn_53Kp6siy1lNPZgF43PhUvHS6w",
    "authDomain": "body-posture-record-app-73450.firebaseapp.com",
    "databaseURL": "https://body-posture-record-app-73450-default-rtdb.asia-southeast1.firebasedatabase.app",
    "storageBucket": "body-posture-record-app-73450.firebasestorage.app",
}

# Initialize Firebase
firebase = pyrebase.initialize_app(firebase_config)
auth = firebase.auth()
db = firebase.database()

mp_pose = mp.solutions.pose
pose = mp_pose.Pose()
mp_drawing = mp.solutions.drawing_utils


def calculate_angle(a, b, c):
    a = np.array(a)
    b = np.array(b)
    c = np.array(c)

    radians = np.arctan2(c[1] - b[1], c[0] - b[0]) - np.arctan2(a[1] - b[1], a[0] - b[0])
    angle = np.abs(radians * 180.0 / np.pi)

    if angle > 180.0:
        angle = 360 - angle

    return angle


class LoginSignupApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Login or Signup")
        self.root.geometry("450x450")
        self.root.configure(bg="#f4f4f9")

        self.email_var = tk.StringVar()
        self.password_var = tk.StringVar()

        tk.Label(self.root, text="Posture Detection Login", font=("Helvetica", 18, "bold"), bg="#f4f4f9").pack(pady=20)

        tk.Label(self.root, text="Email:", bg="#f4f4f9", font=("Helvetica", 12)).pack()
        tk.Entry(self.root, textvariable=self.email_var, font=("Helvetica", 12), width=30).pack(pady=5)

        tk.Label(self.root, text="Password:", bg="#f4f4f9", font=("Helvetica", 12)).pack()
        tk.Entry(self.root, textvariable=self.password_var, font=("Helvetica", 12), width=30, show="*").pack(pady=5)

        self.login_button = tk.Button(self.root, text="Login", command=self.login_user, bg="#4CAF50", fg="white",
                                      font=("Helvetica", 12), width=15)
        self.login_button.pack(pady=10)

        self.signup_button = tk.Button(self.root, text="Sign Up", command=self.signup_user, bg="#2196F3", fg="white",
                                       font=("Helvetica", 12), width=15)
        self.signup_button.pack()

    def login_user(self):
        email = self.email_var.get()
        password = self.password_var.get()

        try:
            user = auth.sign_in_with_email_and_password(email, password)
            messagebox.showinfo("Success", "Logged in successfully!")
            self.root.destroy()
            PostureApp(tk.Tk(), user["localId"])
        except:
            messagebox.showerror("Error", "Invalid email or password!")

    def signup_user(self):
        email = self.email_var.get()
        password = self.password_var.get()

        try:
            auth.create_user_with_email_and_password(email, password)
            messagebox.showinfo("Success", "Account created successfully! Please login.")
        except:
            messagebox.showerror("Error", "Could not create account. Try again!")


class PostureApp:
    def __init__(self, root, user_id):
        self.root = root
        self.user_id = user_id
        self.root.title("Posture Detection Application")
        self.root.geometry("900x900")
        self.root.configure(bg="#f4f4f9")

        self.camera_index = tk.IntVar(value=0)
        self.running = False
        self.cap = None
        self.frame_queue = queue.Queue(maxsize=1)  # Queue to store video frames
        self.video_thread = None

        self.bad_posture_start_time = None
        self.bad_posture_start_time_notification = None

        self.setup_ui()

    def setup_ui(self):
        header_frame = tk.Frame(self.root, bg="#283593", height=100)
        header_frame.pack(side=tk.TOP, fill=tk.X)

        header_label = tk.Label(
            header_frame,
            text="Posture Detection System",
            bg="#283593",
            fg="white",
            font=("Helvetica", 24, "bold"),
            pady=10,
        )
        header_label.pack()

        logout_button = tk.Button(
            header_frame,
            text="Logout",
            command=self.logout_user,
            bg="#F44336",
            fg="white",
            font=("Helvetica", 12, "bold"),
            activebackground="#e53935",
            width=10,
        )
        logout_button.pack(side=tk.RIGHT, padx=10, pady=10)

        control_frame = tk.Frame(self.root, bg="white", height=100, pady=10)
        control_frame.pack(side=tk.TOP, fill=tk.X)

        tk.Label(control_frame, text="Select Camera:", font=("Helvetica", 12), bg="white").pack(side=tk.LEFT, padx=10)
        self.camera_dropdown = ttk.Combobox(control_frame, textvariable=self.camera_index, values=[0, 1, 2], width=15)
        self.camera_dropdown.pack(side=tk.LEFT, padx=5)

        self.start_button = tk.Button(
            control_frame,
            text="Start",
            command=self.start_detection,
            bg="#4CAF50",
            fg="white",
            font=("Helvetica", 12, "bold"),
            activebackground="#45a049",
            width=10,
        )
        self.start_button.pack(side=tk.LEFT, padx=10)

        self.stop_button = tk.Button(
            control_frame,
            text="Stop",
            command=self.stop_detection,
            bg="#F44336",
            fg="white",
            font=("Helvetica", 12, "bold"),
            activebackground="#e53935",
            width=10,
        )
        self.stop_button.pack(side=tk.LEFT, padx=10)

        self.canvas = tk.Canvas(self.root, width=800, height=500, bg="#e0e0e0", highlightthickness=0)
        self.canvas.pack(pady=20)

        status_frame = tk.Frame(self.root, bg="#f4f4f9")
        status_frame.pack(pady=10)

        self.status_label = tk.Label(
            status_frame, text="Posture Status: Not Detected", font=("Helvetica", 16, "bold"), bg="#f4f4f9"
        )
        self.status_label.pack(side=tk.LEFT, padx=10)

        self.status_indicator = tk.Label(status_frame, width=2, height=1, bg="gray")
        self.status_indicator.pack(side=tk.LEFT)

        self.guidance_label = tk.Label(
            self.root, text="Guidance: ", font=("Helvetica", 14), bg="#f4f4f9", wraplength=800
        )
        self.guidance_label.pack(pady=10)

    def logout_user(self):
        self.stop_detection()
        self.root.destroy()
        root = tk.Tk()
        LoginSignupApp(root)
        root.mainloop()

    def start_detection(self):
        if self.running:
            messagebox.showinfo("Info", "Detection is already running.")
            return

        self.cap = cv2.VideoCapture(self.camera_index.get())
        if not self.cap.isOpened():
            messagebox.showerror("Error", "Failed to open camera.")
            return

        self.running = True

        # Start the video capture thread
        self.video_thread = threading.Thread(target=self.capture_frames)
        self.video_thread.daemon = True
        self.video_thread.start()

        self.process_frames()

    def stop_detection(self):
        self.running = False
        if self.cap:
            self.cap.release()
        self.canvas.delete("all")

    def capture_frames(self):
        while self.running:
            ret, frame = self.cap.read()
            if ret:
                resized_frame = cv2.resize(frame, (640, 360))  # Resize for efficiency
                if not self.frame_queue.full():
                    self.frame_queue.put(resized_frame)

    def process_frames(self):
        if not self.running:
            return

        if not self.frame_queue.empty():
            frame = self.frame_queue.get()
            image = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

            results = pose.process(image)
            posture_status = "Good"
            guidance_message = ""

            if results.pose_landmarks:
                mp_drawing.draw_landmarks(image, results.pose_landmarks, mp_pose.POSE_CONNECTIONS)

                try:
                    landmarks = results.pose_landmarks.landmark
                    nose = [landmarks[mp_pose.PoseLandmark.NOSE.value].x, landmarks[mp_pose.PoseLandmark.NOSE.value].y]
                    left_shoulder = [
                        landmarks[mp_pose.PoseLandmark.LEFT_SHOULDER.value].x,
                        landmarks[mp_pose.PoseLandmark.LEFT_SHOULDER.value].y,
                    ]
                    right_shoulder = [
                        landmarks[mp_pose.PoseLandmark.RIGHT_SHOULDER.value].x,
                        landmarks[mp_pose.PoseLandmark.RIGHT_SHOULDER.value].y,
                    ]
                    shoulder_angle = calculate_angle(left_shoulder, nose, right_shoulder)
                    left_hip = [
                        landmarks[mp_pose.PoseLandmark.LEFT_HIP.value].x,
                        landmarks[mp_pose.PoseLandmark.LEFT_HIP.value].y,
                    ]
                    right_hip = [
                        landmarks[mp_pose.PoseLandmark.RIGHT_HIP.value].x,
                        landmarks[mp_pose.PoseLandmark.RIGHT_HIP.value].y,
                    ]
                    back_angle = calculate_angle(left_shoulder, left_hip, right_shoulder)

                    if 72 <= shoulder_angle <= 88 and 25 <= back_angle <= 36:
                        posture_status = "Good"
                        guidance_message = "Keep it up! You're sitting well."
                    else:
                        posture_status = "Bad"
                        guidance_message = "Sit upright! Your posture needs adjustment."

                    self.update_status(posture_status)
                    self.update_guidance(guidance_message)
                except Exception as e:
                    print("Error processing landmarks:", e)
                    self.update_status("Not Detected")

            img = Image.fromarray(image)
            imgtk = ImageTk.PhotoImage(image=img)
            self.canvas.create_image(0, 0, anchor=tk.NW, image=imgtk)
            self.canvas.image = imgtk

        self.root.after(10, self.process_frames)

    def update_status(self, posture_status):
        self.status_label.config(text=f"Posture Status: {posture_status}")
        self.status_indicator.config(bg="green" if posture_status == "Good" else "red")

    def update_guidance(self, guidance_message):
        self.guidance_label.config(text=f"Guidance: {guidance_message}")


if __name__ == "__main__":
    root = tk.Tk()
    app = LoginSignupApp(root)
    root.mainloop()
