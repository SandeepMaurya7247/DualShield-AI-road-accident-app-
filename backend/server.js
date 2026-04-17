require('dotenv').config();
const express = require('express');
const cors = require('cors');
const mongoose = require('mongoose');
const path = require('path');
const fs = require('fs');
const localtunnel = require('localtunnel');

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 5000;
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
mongoose.connect(mongoUri, { 
    serverSelectionTimeoutMS: 5000,
    connectTimeoutMS: 10000 
})
    .then(() => {
        isDbConnected = true;
        const host = mongoose.connection.host;
        console.log(`[DATABASE] Success! Connected to MongoDB Hosted at: ${host}`);
        console.log(`[DATABASE] Active Database: "${mongoose.connection.name}"`);
    })
    .catch((err) => {
        isDbConnected = false;
        loadFallbackStore();
        console.log(`\n[DATABASE] ❌ WARNING: CONNECTION TO ATLAS FAILED!`);
        console.log(`[DATABASE] URI: ${mongoUri.split('@')[1] ? '***@' + mongoUri.split('@')[1] : mongoUri}`);
        console.log(`[DATABASE] ERROR: ${err.message}`);
        console.log(`[DATABASE] ACTION: Ensure your current IP is whitelisted in Atlas -> Network Access.`);
        console.log(`[DATABASE] FALLBACK: Using local file [${FALLBACK_FILE}]\n`);
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
    phone: String,
    latitude: Number,
    longitude: Number,
    severityLevel: Number,
    accelX: Number,
    accelY: Number,
    accelZ: Number,
    gyroX: Number,
    gyroY: Number,
    gyroZ: Number,
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

// ── Health Check ──
app.get('/health', (req, res) => {
    console.log(`[HEALTH] Heartbeat received at: ${new Date().toISOString()} - Keeping system active`);
    const mode = isDbConnected ? "Persistent (MongoDB)" : "Fast-Demo (In-Memory Fallback)";
    res.status(200).json({
        status: 'online',
        mode: mode,
        database: isDbConnected ? 'connected' : 'disconnected',
        db_details: isDbConnected ? {
            name: mongoose.connection.name,
            host: mongoose.connection.host
        } : null,
        ip_whitelist_hint: "Check Atlas Network Access if database is disconnected",
        version: '2.2.0'
    });
});

// ── Auth ──
app.post('/api/users/register', async (req, res) => {
    try {
        const { name, phone, emergency_name, emergency_phone } = req.body;
        console.log(`[AUTH] Registration request: ${name} (${phone})`);
        if (!phone) return res.status(400).json({ error: 'phone is required' });
        
        let uid = phone;
        if (isDbConnected) {
            const upData = { 
                phone, 
                name,
                emergency_contacts: [] 
            };
            if (emergency_name || emergency_phone) {
                upData.emergency_contacts = [{
                    contact_name: emergency_name || "Emergency",
                    contact_phone: emergency_phone || "",
                    relation: "Guardian"
                }];
            }

            const user = await User.findOneAndUpdate(
                { phone: phone },
                { $set: upData },
                { new: true, upsert: true }
            );
            uid = user._id;
        } else {
            fallbackStore.users[phone] = { 
                phone, 
                name,
                emergency_name: emergency_name || "",
                emergency_phone: emergency_phone || ""
            };
            saveFallbackStore();
        }
        res.status(201).json({ status: 'success', user_id: uid, name, phone });
    } catch (e) {
        console.error("[AUTH] Registration error:", e.message);
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

app.post('/api/users/sync', async (req, res) => {
    try {
        const { phone, name, contacts } = req.body;
        console.log(`[SYNC] Request for ${phone} - Name: ${name}, Contacts: ${contacts?.length || 0}`);
        
        if (!phone) return res.status(400).json({ error: 'phone is required' });

        if (isDbConnected) {
            const updateData = { phone };
            if (name) updateData.name = name;
            if (contacts && Array.isArray(contacts) && contacts.length > 0) {
                updateData.emergency_contacts = contacts.map(c => ({
                    contact_name: c.contact_name,
                    contact_phone: c.contact_phone,
                    relation: c.relation || 'Family'
                }));
            }

            const user = await User.findOneAndUpdate(
                { phone },
                { $set: updateData },
                { new: true, upsert: true }
            );
            res.status(200).json({ status: 'success', user_id: user._id, name: user.name, phone: user.phone });
        } else {
            fallbackStore.users[phone] = { 
                phone, 
                name: name || fallbackStore.users[phone]?.name || "User",
            };
            if (contacts) fallbackStore.contacts[phone] = contacts;
            saveFallbackStore();
            res.status(200).json({ status: 'success', user_id: phone, name, phone });
        }
    } catch (e) {
        console.error("[SYNC] Error:", e.message);
        res.status(500).json({ error: e.message });
    }
});

// ── Incidents ──
app.post('/api/incidents', async (req, res) => {
    try {
        const data = req.body;
        if (!data) return res.status(400).json({ error: 'No data provided' });
        data.received_at = new Date().toISOString();
        
        const reportUser = data.phone || data.userId || 'unknown';
        console.log(`🚨 CRASH REPORT: User ${reportUser} at (${data.latitude}, ${data.longitude})`);
        console.log(`📊 TELEMETRY: Accel(${data.accelX}, ${data.accelY}, ${data.accelZ}) Gyro(${data.gyroX}, ${data.gyroY}, ${data.gyroZ})`);
        
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
        const { userId, phone } = req.query;
        let filter = {};
        if (phone) filter.phone = phone;
        else if (userId) filter.userId = userId;

        if (isDbConnected) {
            const docs = await Incident.find(filter).sort({ timestamp: -1 }).limit(20);
            result = docs.map(doc => ({
                id: doc._id,
                userId: doc.userId || 'unknown',
                phone: doc.phone || '',
                latitude: doc.latitude || 0,
                longitude: doc.longitude || 0,
                severityLevel: doc.severityLevel || 1,
                timestamp: doc.timestamp || 0,
                received_at: doc.received_at || ''
            }));
        } else {
            let list = [...fallbackStore.incidents];
            if (phone) {
                list = list.filter(inc => inc.phone === phone);
            } else if (userId) {
                list = list.filter(inc => inc.userId === userId);
            }
            list.sort((a,b) => (b.timestamp||0) - (a.timestamp||0));
            result = list.slice(0, 20).map(doc => ({
                id: doc._id,
                userId: doc.userId || 'unknown',
                phone: doc.phone || '',
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
  {"name":"MP Nagar","lat":23.2330,"lng":77.4320,"radius":200,"risk":"High"},
  {"name":"New Market TT Nagar","lat":23.2280,"lng":77.4080,"radius":200,"risk":"Very High"},
  {"name":"Board Office Square","lat":23.2305,"lng":77.4317,"radius":200,"risk":"High"},
  {"name":"Rani Kamlapati Station","lat":23.2333,"lng":77.4370,"radius":200,"risk":"Very High"},
  {"name":"Habibganj Naka","lat":23.2290,"lng":77.4400,"radius":200,"risk":"High"},
  {"name":"Kolar Road","lat":23.2150,"lng":77.4500,"radius":200,"risk":"Very High"},
  {"name":"Kolar Dam","lat":23.1500,"lng":77.3800,"radius":200,"risk":"Moderate"},
  {"name":"Sarvdharm Colony","lat":23.2200,"lng":77.4550,"radius":200,"risk":"High"},
  {"name":"Bima Kunj","lat":23.2250,"lng":77.4600,"radius":200,"risk":"Moderate"},
  {"name":"Danish Nagar","lat":23.2100,"lng":77.4600,"radius":200,"risk":"High"},
  {"name":"Neelbad","lat":23.193409,"lng":77.343359,"radius":200,"risk":"High"},
  {"name":"Ratibad","lat":23.169909,"lng":77.321277,"radius":200,"risk":"High"},
  {"name":"Kerwa Dam Road","lat":23.2800,"lng":77.2600,"radius":200,"risk":"Moderate"},
  {"name":"Kaliasot Dam","lat":23.2700,"lng":77.3000,"radius":200,"risk":"Moderate"},
  {"name":"Bhadbhada Dam","lat":23.2069,"lng":77.2298,"radius":200,"risk":"High"},
  {"name":"Lalghati Square","lat":23.2660,"lng":77.3800,"radius":200,"risk":"Moderate"},
  {"name":"VIP Road","lat":23.2500,"lng":77.3600,"radius":200,"risk":"High"},
  {"name":"Airport Road","lat":23.2900,"lng":77.3500,"radius":200,"risk":"High"},
  {"name":"Gandhi Nagar","lat":23.3000,"lng":77.3600,"radius":200,"risk":"Moderate"},
  {"name":"Bairagarh","lat":23.2800,"lng":77.3300,"radius":200,"risk":"High"},
  {"name":"Prabhat Square","lat":23.2521,"lng":77.4308,"radius":200,"risk":"Very High"},
  {"name":"Govindpura","lat":23.2500,"lng":77.4400,"radius":200,"risk":"Very High"},
  {"name":"Piplani","lat":23.2600,"lng":77.4500,"radius":200,"risk":"High"},
  {"name":"Indrapuri","lat":23.2800,"lng":77.4600,"radius":200,"risk":"High"},
  {"name":"Ayodhya Nagar","lat":23.2700,"lng":77.4700,"radius":200,"risk":"High"},
  {"name":"Karond","lat":23.3000,"lng":77.4200,"radius":200,"risk":"Extremely High"},
  {"name":"Bhanpur","lat":23.3100,"lng":77.4300,"radius":200,"risk":"High"},
  {"name":"Narela Shankari","lat":23.3200,"lng":77.4400,"radius":200,"risk":"High"},
  {"name":"Ayodhya Bypass","lat":23.2900,"lng":77.4500,"radius":200,"risk":"Extremely High"},
  {"name":"Raisen Road","lat":23.2600,"lng":77.4700,"radius":200,"risk":"Extremely High"},
  {"name":"MISROD","lat":23.2000,"lng":77.5000,"radius":200,"risk":"Very High"},
  {"name":"Mandideep Highway","lat":23.1500,"lng":77.5200,"radius":200,"risk":"High"},
  {"name":"Hoshangabad Road","lat":23.2200,"lng":77.4800,"radius":200,"risk":"Extremely High"},
  {"name":"AIIMS Bhopal","lat":23.2100,"lng":77.4800,"radius":200,"risk":"High"},
  {"name":"Bagsewania","lat":23.2300,"lng":77.4900,"radius":200,"risk":"High"},
  {"name":"Chowk Bazaar","lat":23.2600,"lng":77.4000,"radius":200,"risk":"Very High"},
  {"name":"Royal Market","lat":23.2550,"lng":77.4100,"radius":200,"risk":"High"},
  {"name":"Peer Gate","lat":23.2500,"lng":77.4050,"radius":200,"risk":"Very High"},
  {"name":"Itwara","lat":23.2700,"lng":77.4100,"radius":200,"risk":"High"},
  {"name":"Budhwara","lat":23.2650,"lng":77.4150,"radius":200,"risk":"High"},
  {"name":"Ratibadh point1","lat":23.16362446010636,"lng":77.31342717434326,"radius":100,"risk":"High"},
  {"name":"Ratibadh point2","lat":23.155614706253573,"lng": 77.29947968751591,"radius":100,"risk":"High"}
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

app.listen(PORT, '0.0.0.0', async () => {
    console.log(`🚀 Sentinel Backend active on http://0.0.0.0:${PORT}`);
    
    // Log Public IP for Whitelisting
    try {
        const response = await fetch('https://api.ipify.org?format=json');
        const data = await response.json();
        console.log(`🌍 Server Public IP (for Atlas Whitelist): ${data.ip}`);
    } catch (e) {
        console.log('🌍 Could not fetch Public IP (check internet connection)');
    }
    
    // ── AUTOMATED TUNNEL INITIALIZATION (Development Only) ──
    if (process.env.NODE_ENV !== 'production') {
        try {
            const tunnel = await localtunnel({ 
                port: PORT, 
                subdomain: 'dualshield-live-v3' 
            });
            
            console.log(`📡 Sentinel Tunnel Active: ${tunnel.url}`);
            
            tunnel.on('close', () => {
                console.log('⚠️ Sentinel Tunnel Closed');
            });
        } catch (err) {
            console.log('❌ Sentinel Tunnel Failed:', err.message);
        }
    } else {
        console.log('🌐 Running in PRODUCTION mode - LocalTunnel disabled.');
    }
});
