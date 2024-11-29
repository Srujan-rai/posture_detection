import cv2
import mediapipe as mp
import numpy as np
import pyrebase
from datetime import datetime

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
    a = np.array(a)  # Point A
    b = np.array(b)  # Point B (Angle vertex)
    c = np.array(c)  # Point C

    radians = np.arctan2(c[1] - b[1], c[0] - b[0]) - np.arctan2(a[1] - b[1], a[0] - b[0])
    angle = np.abs(radians * 180.0 / np.pi)

    if angle > 180.0:
        angle = 360 - angle

    return angle

# Start webcam feed
cap = cv2.VideoCapture(1)

while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        break

    # Convert the image to RGB
    image = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    image.flags.writeable = False

    # Perform pose detection
    results = pose.process(image)

    # Convert the image back to BGR for rendering
    image.flags.writeable = True
    image = cv2.cvtColor(image, cv2.COLOR_RGB2BGR)

    # Extract pose landmarks
    try:
        landmarks = results.pose_landmarks.landmark

        # Get coordinates of key points
        nose = [landmarks[mp_pose.PoseLandmark.NOSE.value].x,
                landmarks[mp_pose.PoseLandmark.NOSE.value].y]
        left_shoulder = [landmarks[mp_pose.PoseLandmark.LEFT_SHOULDER.value].x,
                         landmarks[mp_pose.PoseLandmark.LEFT_SHOULDER.value].y]
        right_shoulder = [landmarks[mp_pose.PoseLandmark.RIGHT_SHOULDER.value].x,
                          landmarks[mp_pose.PoseLandmark.RIGHT_SHOULDER.value].y]
        left_hip = [landmarks[mp_pose.PoseLandmark.LEFT_HIP.value].x,
                    landmarks[mp_pose.PoseLandmark.LEFT_HIP.value].y]
        right_hip = [landmarks[mp_pose.PoseLandmark.RIGHT_HIP.value].x,
                     landmarks[mp_pose.PoseLandmark.RIGHT_HIP.value].y]

        # Calculate key angles
        shoulder_angle = calculate_angle(left_shoulder, nose, right_shoulder)  # Neck alignment
        back_angle = calculate_angle(left_shoulder, left_hip, right_shoulder)  # Upper back alignment
        print(f"shoulder angle {shoulder_angle}")
        print(f"back angle {back_angle}")
        # Thresholds for good posture
        if 75 <= shoulder_angle <= 88 and 28 <= back_angle <= 34:
            posture = "Good"
            color = (0, 255, 0)  # Green
        else:
            posture = "Bad"
            color = (0, 0, 255)  # Red

        # Push data to Firebase
        current_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        data = {
        "posture": posture,
        "time": current_time
    }
        db.child("PostureData").set(data)

        # Display posture status
        cv2.putText(image, f"Posture: {posture}", (10, 50), cv2.FONT_HERSHEY_SIMPLEX, 1, color, 2)

        # Draw landmarks
        mp_drawing.draw_landmarks(image, results.pose_landmarks, mp_pose.POSE_CONNECTIONS)

    except Exception as e:
        # If pose not detected
        cv2.putText(image, "Posture: Not Detected", (10, 50), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 255), 2)

    # Show output
    cv2.imshow("Laptop Posture Detection", image)

    # Break on pressing 'q'
    if cv2.waitKey(10) & 0xFF == ord('q'):
        break

# Release resources
cap.release()
cv2.destroyAllWindows()
