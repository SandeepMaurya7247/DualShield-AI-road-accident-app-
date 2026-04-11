from pymongo import MongoClient
from pymongo.errors import ConnectionFailure
import os
import logging
from bson import ObjectId
import time

# ── IN-MEMORY FALLBACK (For environments without MongoDB) ────────────────────
MOCK_STORE = {
    'users': {},      # phone -> user_doc
    'incidents': [],   # list of incident_docs
    'contacts': {}     # phone -> list of contact_docs
}

def init_db():
    mongo_uri = os.environ.get('MONGO_URI', 'mongodb://localhost:27017/')
    try:
        client = MongoClient(mongo_uri, serverSelectionTimeoutMS=2000)
        client.admin.command('ping')
        logging.info("MongoDB connected successfully.")
        return client['dualshield_db']
    except Exception as e:
        logging.warning(f"MongoDB connection failed: {e}. Switching to IN-MEMORY FALLBACK.")
        return None

def register_user(db, user_data):
    phone = user_data.get("phone")
    if db is not None:
        collection = db['users']
        result = collection.update_one({"phone": phone}, {"$set": user_data}, upsert=True)
        return result.upserted_id or phone
    else:
        # Fallback
        MOCK_STORE['users'][phone] = user_data
        logging.info(f"Fallback: Registered user {phone} in memory")
        return phone

def login_user(db, phone):
    if db is not None:
        return db['users'].find_one({"phone": phone})
    else:
        # Fallback
        return MOCK_STORE['users'].get(phone)

def insert_incident(db, data):
    if db is not None:
        return db['incidents'].insert_one(data).inserted_id
    else:
        # Fallback
        incident = data.copy()
        incident['_id'] = str(int(time.time() * 1000))
        MOCK_STORE['incidents'].append(incident)
        return incident['_id']

def get_recent_incidents(db, limit=20):
    if db is not None:
        return list(db['incidents'].find().sort("timestamp", -1).limit(limit))
    else:
        # Fallback
        return sorted(MOCK_STORE['incidents'], key=lambda x: x.get('timestamp', 0), reverse=True)[:limit]

def get_geofences(db):
    if db is not None:
        return list(db['geofences'].find())
    else:
        # Static defaults for demo
        return [
            {"name": "NH-8 High Risk Curve", "lat": 28.4595, "lng": 77.0266, "radius": 500},
            {"name": "Accident Prone Intersection", "lat": 28.6139, "lng": 77.2090, "radius": 300}
        ]

# ── Emergency Contacts ───────────────────────────────────────────────────────

def get_contacts(db, phone):
    if db is not None:
        user = db['users'].find_one({"phone": phone})
        return user.get('emergency_contacts', []) if user else []
    else:
        # Fallback
        return MOCK_STORE['contacts'].get(phone, [])

def add_contact(db, user_phone, contact_data):
    if db is not None:
        db['users'].update_one(
            {"phone": user_phone},
            {"$push": {"emergency_contacts": {
                "contact_name": contact_data.get('contact_name'),
                "contact_phone": contact_data.get('contact_phone'),
                "relation": contact_data.get('relation', 'Family')
            }}},
            upsert=True
        )
    else:
        # Fallback
        if user_phone not in MOCK_STORE['contacts']:
            MOCK_STORE['contacts'][user_phone] = []
        MOCK_STORE['contacts'][user_phone].append({
            "contact_name": contact_data.get('contact_name'),
            "contact_phone": contact_data.get('contact_phone'),
            "relation": contact_data.get('relation', 'Family')
        })
        logging.info(f"Fallback: Added contact for {user_phone} in memory")

def delete_contact(db, user_phone, contact_phone):
    if db is not None:
        db['users'].update_one(
            {"phone": user_phone},
            {"$pull": {"emergency_contacts": {"contact_phone": contact_phone}}}
        )
    else:
        # Fallback
        if user_phone in MOCK_STORE['contacts']:
            MOCK_STORE['contacts'][user_phone] = [
                c for c in MOCK_STORE['contacts'][user_phone] 
                if c.get('contact_phone') != contact_phone
            ]
