import cv2
import mediapipe as mp
import face_recognition
import numpy as np
import os
import datetime
import base64
from flask import Flask, request, jsonify
from flask_cors import CORS
import json
import os
from werkzeug.utils import secure_filename

app = Flask(__name__)
CORS(app)


ORG_CONFIG_FILE = "organizations.json"
UPLOAD_FOLDER = "uploads"
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

def get_organizations():
    if not os.path.exists(ORG_CONFIG_FILE):
        with open(ORG_CONFIG_FILE, "w") as f:
            json.dump({}, f)
        return {}
    with open(ORG_CONFIG_FILE, "r") as f:
        return json.load(f)
    
def save_organizations(orgs):
    with open(ORG_CONFIG_FILE, "w") as f:
        json.dump(orgs, f)

def get_org_face_directory(org_id):
    dir_path = f"Registered_faces/{org_id}"
    os.makedirs(dir_path, exist_ok=True)
    return dir_path

def get_org_log_file(org_id):
    log_dir = "logs"
    os.makedirs(log_dir, exist_ok=True)
    return f"{log_dir}/{org_id}_log.txt"


mp_face_detection = mp.solutions.face_detection
face_detection = mp_face_detection.FaceDetection(model_selection=1, min_detection_confidence=0.5)


def load_known_faces(org_id):
    known_face_encodings = []
    known_face_names = []
    face_dir = get_org_face_directory(org_id)
    
    for filename in os.listdir(face_dir):
        if filename.lower().endswith(('.jpg', '.jpeg', '.png')):
            image_path = os.path.join(face_dir, filename)
            image = face_recognition.load_image_file(image_path)
            encodings = face_recognition.face_encodings(image)
            if encodings:
                known_face_encodings.append(encodings[0])
                known_face_names.append(os.path.splitext(filename)[0])
    
    return known_face_encodings, known_face_names


@app.route('/api/login', methods=['POST'])
def login():
    data = request.get_json()
    org_id = data.get('orgId')
    password = data.get('password')
    
    organizations = get_organizations()
    if org_id in organizations and organizations[org_id]['password'] == password:
        return jsonify({"success": True, "message": "Login successful"})
    
    return jsonify({"success": False, "message": "Invalid credentials"}), 401


@app.route('/api/register-organization', methods=['POST'])
def register_organization():
    data = request.get_json()
    org_id = data.get('orgId')
    password = data.get('password')
    org_name = data.get('orgName')
    
    if not org_id or not password or not org_name:
        return jsonify({"success": False, "message": "Missing required fields"}), 400
    
    organizations = get_organizations()
    if org_id in organizations:
        return jsonify({"success": False, "message": "Organization ID already exists"}), 409
    
    organizations[org_id] = {
        "name": org_name,
        "password": password,
        "created_at": datetime.datetime.now().isoformat()
    }
    
    save_organizations(organizations)
    get_org_face_directory(org_id)  
    
    return jsonify({"success": True, "message": "Organization registered successfully"})


@app.route('/api/register-face', methods=['POST'])
def register_face():
    org_id = request.form.get('orgId')
    password = request.form.get('password')
    person_name = request.form.get('name')
    
    
    organizations = get_organizations()
    if not org_id in organizations or organizations[org_id]['password'] != password:
        return jsonify({"success": False, "message": "Invalid organization credentials"}), 401
    
    
    if 'image' not in request.files:
        return jsonify({"success": False, "message": "No image provided"}), 400
    
    file = request.files['image']
    if file.filename == '':
        return jsonify({"success": False, "message": "No image selected"}), 400
    
    
    filename = secure_filename(f"{person_name}.jpg")
    face_dir = get_org_face_directory(org_id)
    filepath = os.path.join(face_dir, filename)
    
    
    if os.path.exists(filepath):
        return jsonify({"success": False, "message": "Person already registered"}), 409
    
    
    file.save(filepath)
    
    
    image = face_recognition.load_image_file(filepath)
    face_locations = face_recognition.face_locations(image)
    if not face_locations:
        os.remove(filepath)  
        return jsonify({"success": False, "message": "No face detected in the image"}), 400
    
    
    face_encodings = face_recognition.face_encodings(image, face_locations)
    if not face_encodings:
        os.remove(filepath)
        return jsonify({"success": False, "message": "Failed to encode face"}), 400
    
    return jsonify({"success": True, "message": "Face registered successfully"})


@app.route('/api/recognize-face', methods=['POST'])
def recognize_face():
    org_id = request.form.get('orgId')
    
    
    organizations = get_organizations()
    if not org_id in organizations:
        return jsonify({"success": False, "message": "Invalid organization ID"}), 401
    
    
    if 'image' not in request.files:
        return jsonify({"success": False, "message": "No image provided"}), 400
    
    file = request.files['image']
    if file.filename == '':
        return jsonify({"success": False, "message": "No image selected"}), 400
    
    
    temp_path = os.path.join(UPLOAD_FOLDER, "temp_" + secure_filename(file.filename))
    file.save(temp_path)
    
    
    frame = cv2.imread(temp_path)
    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    
    
    results = face_detection.process(rgb_frame)
    if not results.detections:
        os.remove(temp_path)
        return jsonify({"success": False, "message": "No face detected"}), 400
    
    
    detection = results.detections[0]
    bbox = detection.location_data.relative_bounding_box
    h, w, _ = frame.shape
    x = int(bbox.xmin * w)
    y = int(bbox.ymin * h)
    w_box = int(bbox.width * w)
    h_box = int(bbox.height * h)
    
    face_location = (y, x + w_box, y + h_box, x)  
    
    
    face_encodings = face_recognition.face_encodings(rgb_frame, [face_location])
    if not face_encodings:
        os.remove(temp_path)
        return jsonify({"success": False, "message": "Failed to encode face"}), 400
    
    encoding = face_encodings[0]
    
    
    known_face_encodings, known_face_names = load_known_faces(org_id)
    
    if not known_face_encodings:
        os.remove(temp_path)
        return jsonify({"success": False, "message": "No registered faces for this organization"}), 400
    
    
    matches = face_recognition.compare_faces(known_face_encodings, encoding, tolerance=0.5)
    
    os.remove(temp_path)  
    
    if True in matches:
        match_index = matches.index(True)
        name = known_face_names[match_index]
        now = datetime.datetime.now().strftime("%A, %Y-%m-%d %H:%M:%S")
        
        
        with open(get_org_log_file(org_id), "a") as log:
            log.write(f"ALLOWED: {name} | {now}\n")
        
        return jsonify({
            "success": True, 
            "recognized": True, 
            "name": name,
            "timestamp": now
        })
    else:
        return jsonify({
            "success": True,
            "recognized": False,
            "message": "Face not recognized"
        })


@app.route('/api/attendance-logs', methods=['GET'])
def get_attendance_logs():
    org_id = request.args.get('orgId')
    password = request.args.get('password')
    date_filter = request.args.get('date')
    
    
    organizations = get_organizations()
    if not org_id in organizations or organizations[org_id]['password'] != password:
        return jsonify({"success": False, "message": "Invalid organization credentials"}), 401
    
    log_file = get_org_log_file(org_id)
    if not os.path.exists(log_file):
        return jsonify({"success": True, "logs": []})
    

    logs = []
    with open(log_file, "r") as file:
        for line in file:
            parts = line.strip().split(" | ")
            if len(parts) == 2:
                status_name = parts[0]
                timestamp = parts[1]
                
                
                if date_filter and date_filter not in timestamp:
                    continue
                
                status = "ALLOWED"
                name = status_name.replace("ALLOWED: ", "")
                logs.append({
                    "name": name,
                    "timestamp": timestamp,
                    "status": status
                })
    
    return jsonify({"success": True, "logs": logs})

if __name__ == "__main__":
    app.run(host='0.0.0.0', port=5000, debug=True)