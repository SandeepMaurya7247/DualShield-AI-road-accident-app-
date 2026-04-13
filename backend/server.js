require('dotenv').config();
const express = require('express');
const cors = require('cors');
const mongoose = require('mongoose');
const path = require('path');
const fs = require('fs');

const app = express();
app.use(cors());
app.use(express.json());

const PORT = 5000;
const mongoUri = process.env.MONGO_URI || 'mongodb://localhost:27017/dualshield_db';

// Fallback logic for in-memory JSON (Local only)
const FALLBACK_FILE = path.join(__dirname, 'fallback_data.json');
let fallbackStore = { users: {}, incidents: [], contacts: {} };
let isDbConnected = false;

function loadFallbackStore() {
    if (fs.existsSync(FALLBACK_FILE)) {
        try {
            fallbackStore = JSON.parse(fs.readFileSync(FALLBACK_FILE, 'utf8'));
            if (!fallbackStore.users) fallbackStore.users = {};
            if (!fallbackStore.incidents) fallbackStore.incidents = [];
            if (!fallbackStore.contacts) fallbackStore.contacts = {};
        } catch (err) {
            console.error("Could not load fallback store:", err);
        }
    }
}
function saveFallbackStore() {
    fs.writeFileSync(FALLBACK_FILE, JSON.stringify(fallbackStore, null, 2));
}

// Connect to MongoDB
mongoose.connect(mongoUri, { serverSelectionTimeoutMS: 2000 })
    .then(() => {
        isDbConnected = true;
        const dbName = mongoose.connection.name;
        console.log(`MongoDB connected successfully to database: "${dbName}"`);
    })
    .catch((err) => {
        isDbConnected = false;
        loadFallbackStore();
        console.log("MongoDB connection failed, using local IN-MEMORY fallback.", err.message);
    });

// Mongoose Models
const userSchema = new mongoose.Schema({
    phone: { type: String, unique: true },
    name: String,
    emergency_contacts: [
        { contact_name: String, contact_phone: String, relation: String }
    ]
});
const User = mongoose.model('User', userSchema);

const incidentSchema = new mongoose.Schema({
    userId: String,
    latitude: Number,
    longitude: Number,
    severityLevel: Number,
    timestamp: Number,
    received_at: String
});
const Incident = mongoose.model('Incident', incidentSchema);

const geofenceSchema = new mongoose.Schema({
    name: String, lat: Number, lng: Number, radius: Number
});
const Geofence = mongoose.model('Geofence', geofenceSchema);

// ── Mission Control Dashboard ──
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'templates', 'index.html'));
});

// ── Health ──
app.get('/health', (req, res) => {
    const mode = isDbConnected ? "Persistent (MongoDB)" : "Fast-Demo (In-Memory Fallback)";
    res.status(200).json({
        status: 'online',
        mode: mode,
        database: isDbConnected ? 'connected' : 'disconnected',
        version: '2.1.0'
    });
});

// ── Auth ──
app.post('/api/users/register', async (req, res) => {
    try {
        const { name, phone } = req.body;
        console.log(`[AUTH] Registration request received: ${name} (${phone})`);
        if (!phone) return res.status(400).json({ error: 'phone is required' });
        
        let uid = phone;
        if (isDbConnected) {
            const user = await User.findOneAndUpdate(
                { phone: phone },
                { $set: { phone, name } },
                { new: true, upsert: true }
            );
            uid = user._id;
        } else {
            fallbackStore.users[phone] = { phone, name };
            saveFallbackStore();
        }
        res.status(201).json({ status: 'success', user_id: uid, name, phone });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

app.post('/api/users/login', async (req, res) => {
    try {
        const phone = (req.body.phone || '').trim();
        console.log(`[AUTH] Login attempt for phone: ${phone}`);
        if (!phone) return res.status(400).json({ error: 'phone is required' });

        if (isDbConnected) {
            const user = await User.findOne({ phone });
            if (user) {
                return res.status(200).json({ status: 'success', user_id: user._id, name: user.name, phone });
            }
        } else {
            const user = fallbackStore.users[phone];
            if (user) {
                return res.status(200).json({ status: 'success', user_id: phone, name: user.name, phone });
            }
        }
        res.status(404).json({ status: 'not_found', message: 'Phone not registered' });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// ── Incidents ──
app.post('/api/incidents', async (req, res) => {
    try {
        const data = req.body;
        if (!data) return res.status(400).json({ error: 'No data provided' });
        data.received_at = new Date().toISOString();
        
        console.log(`CRASH: User ${data.userId} at (${data.latitude}, ${data.longitude})`);
        
        let iid = Date.now().toString();
        if (isDbConnected) {
            const incident = new Incident(data);
            await incident.save();
            iid = incident._id;
        } else {
            const incident = { ...data, _id: iid };
            fallbackStore.incidents.push(incident);
            if (fallbackStore.incidents.length > 100) fallbackStore.incidents.shift();
            saveFallbackStore();
        }
        res.status(201).json({ status: 'success', incident_id: iid });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

app.get('/api/incidents', async (req, res) => {
    try {
        let result = [];
        if (isDbConnected) {
            const docs = await Incident.find().sort({ timestamp: -1 }).limit(20);
            result = docs.map(doc => ({
                id: doc._id,
                userId: doc.userId || 'unknown',
                latitude: doc.latitude || 0,
                longitude: doc.longitude || 0,
                severityLevel: doc.severityLevel || 1,
                timestamp: doc.timestamp || 0,
                received_at: doc.received_at || ''
            }));
        } else {
            const list = [...fallbackStore.incidents].sort((a,b) => (b.timestamp||0) - (a.timestamp||0));
            result = list.slice(0, 20).map(doc => ({
                id: doc._id,
                userId: doc.userId || 'unknown',
                latitude: doc.latitude || 0,
                longitude: doc.longitude || 0,
                severityLevel: doc.severityLevel || 1,
                timestamp: doc.timestamp || 0,
                received_at: doc.received_at || ''
            }));
        }
        res.status(200).json(result);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// ── Geofences ──
app.get('/api/geofences/accident-zones', async (req, res) => {
    try {
        let zones = [];
        if (isDbConnected) {
            zones = await Geofence.find();
        }
        
        if (zones.length === 0) {
            zones = [
                {"name": "NH-8 High Risk Curve", "lat": 28.4595, "lng": 77.0266, "radius": 500, "risk": "Very High"},
                {"name": "Accident Prone Intersection", "lat": 28.6139, "lng": 77.2090, "radius": 300, "risk": "High"},
                {"name": "Highway Blind Spot", "lat": 28.5355, "lng": 77.3910, "radius": 400, "risk": "Moderate"}
            ];
        }
        res.status(200).json(zones);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// ── Emergency Contacts ──
app.get('/api/users/:phone/contacts', async (req, res) => {
    try {
        const phone = req.params.phone.trim();
        let contacts = [];
        if (isDbConnected) {
            const user = await User.findOne({ phone });
            contacts = user ? user.emergency_contacts || [] : [];
        } else {
            contacts = fallbackStore.contacts[phone] || [];
        }
        res.status(200).json(contacts);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

app.post('/api/users/:phone/contacts', async (req, res) => {
    try {
        const phone = req.params.phone.trim();
        const data = req.body;
        console.log(`[CONTACTS] Saving contact for ${phone}: ${data.contact_name} (${data.contact_phone})`);        const contact_phone = (data.contact_phone || '').trim();
        
        if (!data.contact_name || !contact_phone) {
            return res.status(400).json({ error: 'contact_name and contact_phone required' });
        }
        
        const newContact = {
            contact_name: data.contact_name,
            contact_phone: contact_phone,
            relation: data.relation || 'Family'
        };

        if (isDbConnected) {
            await User.findOneAndUpdate(
                { phone },
                { $pull: { emergency_contacts: { contact_phone } } }
            );
            await User.findOneAndUpdate(
                { phone },
                { $push: { emergency_contacts: newContact } },
                { upsert: true }
            );
        } else {
            if (!fallbackStore.contacts[phone]) fallbackStore.contacts[phone] = [];
            fallbackStore.contacts[phone] = fallbackStore.contacts[phone].filter(c => c.contact_phone !== contact_phone);
            fallbackStore.contacts[phone].push(newContact);
            saveFallbackStore();
        }
        res.status(201).json({ status: 'success', message: 'Contact saved' });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

app.delete('/api/users/:phone/contacts/:contactPhone', async (req, res) => {
    try {
        const phone = req.params.phone.trim();
        const contactPhone = req.params.contactPhone.trim();
        
        if (isDbConnected) {
            await User.findOneAndUpdate(
                { phone },
                { $pull: { emergency_contacts: { contact_phone: contactPhone } } }
            );
        } else {
            if (fallbackStore.contacts[phone]) {
                fallbackStore.contacts[phone] = fallbackStore.contacts[phone].filter(c => c.contact_phone !== contactPhone);
                saveFallbackStore();
            }
        }
        res.status(200).json({ status: 'success' });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Node.js Backend server running on http://0.0.0.0:${PORT}`);
});
