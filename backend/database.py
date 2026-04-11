from pymongo import MongoClient
from pymongo.errors import ConnectionFailure
import os
import json
import logging
from bson import ObjectId
import time

# ── PERSISTENT IN-MEMORY FALLBACK ────────────────────────────────────────────
# Data is saved to a local JSON file so it survives Flask restarts
FALLBACK_FILE = os.path.join(os.path.dirname(__file__), 'fallback_data.json')

def _load_store():
    """Load persisted data from JSON file, or return blank store."""
    if os.path.exists(FALLBACK_FILE):
        try:
            with open(FALLBACK_FILE, 'r') as f:
                data = json.load(f)
                # Ensure all keys exist
                data.setdefault('users', {})
                data.setdefault('incidents', [])
                data.setdefault('contacts', {})
                logging.info(f"Fallback store loaded: {len(data['users'])} users, {len(data['contacts'])} contact entries")
                return data
        except Exception as e:
            logging.warning(f"Could not load fallback store: {e}. Starting fresh.")
    return {'users': {}, 'incidents': [], 'contacts': {}}

def _save_store(store):
    """Persist store to JSON file."""
    try:
        with open(FALLBACK_FILE, 'w') as f:
            json.dump(store, f, indent=2, default=str)
    except Exception as e:
        logging.error(f"Could not save fallback store: {e}")

# Load on startup
MOCK_STORE = _load_store()

# ── DB Init ───────────────────────────────────────────────────────────────────
def init_db():
    mongo_uri = os.environ.get('MONGO_URI', 'mongodb://localhost:27017/')
    try:
        client = MongoClient(mongo_uri, serverSelectionTimeoutMS=2000)
        client.admin.command('ping')
        logging.info("MongoDB connected successfully.")
        return client['dualshield_db']
    except Exception as e:
        logging.warning(f"MongoDB connection failed: {e}. Switching to IN-MEMORY (persistent JSON) FALLBACK.")
        return None

# ── Users ─────────────────────────────────────────────────────────────────────
def register_user(db, user_data):
    phone = user_data.get("phone", "").strip()
    if not phone:
        raise ValueError("Phone number is required")
    if db is not None:
        collection = db['users']
        result = collection.update_one({"phone": phone}, {"$set": user_data}, upsert=True)
        return result.upserted_id or phone
    else:
        MOCK_STORE['users'][phone] = user_data
        _save_store(MOCK_STORE)
        logging.info(f"Fallback: Registered user {phone}")
        return phone

def login_user(db, phone):
    phone = phone.strip()
    if db is not None:
        return db['users'].find_one({"phone": phone})
    else:
        return MOCK_STORE['users'].get(phone)

# ── Incidents ─────────────────────────────────────────────────────────────────
def insert_incident(db, data):
    if db is not None:
        return db['incidents'].insert_one(data).inserted_id
    else:
        incident = data.copy()
        incident['_id'] = str(int(time.time() * 1000))
        MOCK_STORE['incidents'].append(incident)
        # Keep only last 100 incidents in fallback to avoid file bloat
        MOCK_STORE['incidents'] = MOCK_STORE['incidents'][-100:]
        _save_store(MOCK_STORE)
        return incident['_id']

def get_recent_incidents(db, limit=20):
    if db is not None:
        return list(db['incidents'].find().sort("timestamp", -1).limit(limit))
    else:
        return sorted(MOCK_STORE['incidents'], key=lambda x: x.get('timestamp', 0), reverse=True)[:limit]

# ── Geofences ─────────────────────────────────────────────────────────────────
def get_geofences(db):
    if db is not None:
        return list(db['geofences'].find())
    else:
        return [
            {"name": "NH-8 High Risk Curve", "lat": 28.4595, "lng": 77.0266, "radius": 500},
            {"name": "Accident Prone Intersection", "lat": 28.6139, "lng": 77.2090, "radius": 300},
            {"name": "Highway Blind Spot", "lat": 28.5355, "lng": 77.3910, "radius": 400}
        ]

# ── Emergency Contacts ────────────────────────────────────────────────────────
def get_contacts(db, phone):
    phone = phone.strip()
    if db is not None:
        user = db['users'].find_one({"phone": phone})
        return user.get('emergency_contacts', []) if user else []
    else:
        contacts = MOCK_STORE['contacts'].get(phone, [])
        logging.info(f"Fallback: get_contacts({phone}) -> {len(contacts)} contacts")
        return contacts

def add_contact(db, user_phone, contact_data):
    user_phone = user_phone.strip()
    contact_phone = contact_data.get('contact_phone', '').strip()
    if not contact_phone:
        raise ValueError("contact_phone is required")

    if db is not None:
        # Prevent duplicates in MongoDB
        db['users'].update_one(
            {"phone": user_phone},
            {"$pull": {"emergency_contacts": {"contact_phone": contact_phone}}}
        )
        db['users'].update_one(
            {"phone": user_phone},
            {"$push": {"emergency_contacts": {
                "contact_name": contact_data.get('contact_name', ''),
                "contact_phone": contact_phone,
                "relation": contact_data.get('relation', 'Family')
            }}},
            upsert=True
        )
    else:
        if user_phone not in MOCK_STORE['contacts']:
            MOCK_STORE['contacts'][user_phone] = []
        # Prevent duplicates in fallback
        MOCK_STORE['contacts'][user_phone] = [
            c for c in MOCK_STORE['contacts'][user_phone]
            if c.get('contact_phone') != contact_phone
        ]
        MOCK_STORE['contacts'][user_phone].append({
            "contact_name": contact_data.get('contact_name', ''),
            "contact_phone": contact_phone,
            "relation": contact_data.get('relation', 'Family')
        })
        _save_store(MOCK_STORE)
        logging.info(f"Fallback: Added contact {contact_phone} for {user_phone}")

def delete_contact(db, user_phone, contact_phone):
    user_phone = user_phone.strip()
    contact_phone = contact_phone.strip()
    if db is not None:
        db['users'].update_one(
            {"phone": user_phone},
            {"$pull": {"emergency_contacts": {"contact_phone": contact_phone}}}
        )
    else:
        if user_phone in MOCK_STORE['contacts']:
            MOCK_STORE['contacts'][user_phone] = [
                c for c in MOCK_STORE['contacts'][user_phone]
                if c.get('contact_phone') != contact_phone
            ]
            _save_store(MOCK_STORE)
            logging.info(f"Fallback: Deleted contact {contact_phone} for {user_phone}")
