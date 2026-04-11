from flask import Flask, request, jsonify, render_template
from flask_cors import CORS
from database import (init_db, insert_incident, register_user, login_user,
                      get_recent_incidents, get_geofences,
                      add_contact, get_contacts, delete_contact)
import logging
from datetime import datetime

app = Flask(__name__)
CORS(app)
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
db = init_db()

# ── Mission Control Dashboard ────────────────────────────────────────────────
@app.route('/', methods=['GET'])
def index():
    return render_template('index.html')

# ── Health ──────────────────────────────────────────────────────────────────
@app.route('/health', methods=['GET'])
def health_check():
    mode = "Persistent (MongoDB)" if db is not None else "Fast-Demo (In-Memory Fallback)"
    return jsonify({
        'status': 'online', 
        'mode': mode,
        'database': 'connected' if db is not None else 'disconnected', 
        'version': '2.1.0'
    }), 200

# ── Auth ─────────────────────────────────────────────────────────────────────
@app.route('/api/users/register', methods=['POST'])
def register():
    try:
        data = request.json
        if not data or not data.get('phone'):
            return jsonify({'error': 'phone is required'}), 400
        logging.info(f"Register: {data.get('name')} - {data.get('phone')}")
        uid = register_user(db, data)
        return jsonify({'status': 'success', 'user_id': str(uid), 'name': data.get('name', ''), 'phone': data.get('phone')}), 201
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/users/login', methods=['POST'])
def login():
    try:
        data = request.json
        phone = data.get('phone', '').strip()
        if not phone:
            return jsonify({'error': 'phone is required'}), 400
        logging.info(f"Login: {phone}")
        user = login_user(db, phone)
        if user:
            uid = user.get('_id', user.get('phone', 'uid'))
            return jsonify({'status': 'success', 'user_id': str(uid), 'name': user.get('name', ''), 'phone': phone}), 200
        return jsonify({'status': 'not_found', 'message': 'Phone not registered'}), 404
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ── Incidents ────────────────────────────────────────────────────────────────
@app.route('/api/incidents', methods=['POST'])
def report_incident():
    try:
        data = request.json
        if not data:
            return jsonify({'error': 'No data provided'}), 400
        data['received_at'] = datetime.utcnow().isoformat()
        logging.info(f"CRASH: User {data.get('userId')} at ({data.get('latitude')}, {data.get('longitude')})")
        iid = insert_incident(db, data)
        return jsonify({'status': 'success', 'incident_id': str(iid)}), 201
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/incidents', methods=['GET'])
def fetch_incidents():
    try:
        raw = get_recent_incidents(db)
        result = []
        for doc in raw:
            result.append({
                'id': str(doc.get('_id', '')),
                'userId': doc.get('userId', 'unknown'),
                'latitude': doc.get('latitude', 0),
                'longitude': doc.get('longitude', 0),
                'severityLevel': doc.get('severityLevel', 1),
                'timestamp': doc.get('timestamp', 0),
                'received_at': doc.get('received_at', '')
            })
        return jsonify(result), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ── Geofences ────────────────────────────────────────────────────────────────
@app.route('/api/geofences/accident-zones', methods=['GET'])
def get_zones():
    try:
        zones = get_geofences(db)
        if not zones:
            zones = [
                {"name": "NH-8 High Risk Curve", "lat": 28.4595, "lng": 77.0266, "radius": 500},
                {"name": "Accident Prone Intersection", "lat": 28.6139, "lng": 77.2090, "radius": 300},
                {"name": "Highway Blind Spot", "lat": 28.5355, "lng": 77.3910, "radius": 400}
            ]
        else:
            for z in zones:
                z['_id'] = str(z.get('_id', ''))
        return jsonify(zones), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ── Emergency Contacts ───────────────────────────────────────────────────────
@app.route('/api/users/<phone>/contacts', methods=['GET'])
def get_user_contacts(phone):
    try:
        contacts = get_contacts(db, phone)
        for c in contacts:
            if '_id' in c: c['_id'] = str(c['_id'])
        return jsonify(contacts), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/users/<phone>/contacts', methods=['POST'])
def add_user_contact(phone):
    try:
        data = request.json
        if not data or not data.get('contact_name') or not data.get('contact_phone'):
            return jsonify({'error': 'contact_name and contact_phone required'}), 400
        add_contact(db, phone, data)
        return jsonify({'status': 'success', 'message': 'Contact saved'}), 201
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/users/<phone>/contacts/<contact_phone>', methods=['DELETE'])
def remove_contact(phone, contact_phone):
    try:
        delete_contact(db, phone, contact_phone)
        return jsonify({'status': 'success'}), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
