# Real-Time Posture Detection System

This project is a real-time posture detection system that utilizes **Mediapipe**, **OpenCV**, and **Firebase** to monitor and analyze human posture. It calculates joint angles using key points on the body and provides feedback on whether the user's posture is "Good" or "Bad". The system includes a graphical user interface (GUI) for live video feed display and posture evaluation.

---

## Features

- **Real-time Posture Detection**:
  - Detects body landmarks using Mediapipe's BlazePose.
  - Calculates joint angles to determine posture.
- **Angle-Based Evaluation**:
  - Uses geometric calculations to classify posture as "Good" or "Bad".
- **GUI Interface**:
  - Displays video feed with posture landmarks and real-time feedback.
  - Shows visual indicators for posture status (green = "Good", red = "Bad").
- **Data Logging**:
  - Logs posture status into Firebase for historical tracking.

---

## How It Works

1. Captures live video feed using OpenCV.
2. Processes frames with Mediapipe to extract body landmarks.
3. Calculates angles between key points on the body (e.g., shoulders, back).
4. Compares calculated angles with pre-defined thresholds to classify posture.
5. Displays feedback and logs data to Firebase for further analysis.

---

## Prerequisites

Before you begin, ensure you have met the following requirements:

- Python 3.10 or higher
- Required Python libraries:
  - `mediapipe`
  - `opencv-python`
  - `firebase-admin`
  - `tkinter`
- Firebase account and project setup for real-time database usage.

---

## Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/posture-detection-system.git


2. Navigate to the Project Repository:
```bash
    cd posture-detection-system

3. Install Dependencies:
```bash
    pip install -r requirements.txt

4. Run the Posture Detection System:
```bash
    python3 main.py



