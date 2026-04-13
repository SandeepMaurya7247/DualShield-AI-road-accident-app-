const mongoose = require('mongoose');

const sourceUri = 'mongodb://localhost:27017/dualshield_db';
const targetUri = 'mongodb://localhost:27017/backend';

const userSchema = new mongoose.Schema({
    phone: { type: String, unique: true },
    name: String,
    emergency_contacts: [
        { contact_name: String, contact_phone: String, relation: String }
    ]
});
const incidentSchema = new mongoose.Schema({
    userId: String, latitude: Number, longitude: Number, severityLevel: Number, timestamp: Number, received_at: String
});

async function migrate() {
    try {
        console.log("--- STARTING MIGRATION ---");
        
        // Connect to Source
        const sourceConn = await mongoose.createConnection(sourceUri).asPromise();
        const SourceUser = sourceConn.model('User', userSchema);
        const SourceIncident = sourceConn.model('Incident', incidentSchema);

        console.log("Connected to SOURCE: dualshield_db");
        const users = await SourceUser.find({});
        const incidents = await SourceIncident.find({});
        console.log(`Found ${users.length} users and ${incidents.length} incidents.`);

        // Connect to Target
        const targetConn = await mongoose.createConnection(targetUri).asPromise();
        const TargetUser = targetConn.model('User', userSchema);
        const TargetIncident = targetConn.model('Incident', incidentSchema);
        console.log("Connected to TARGET: backend");

        // Migrate Users
        for (const user of users) {
             console.log(`Migrating User: ${user.name} (${user.phone})`);
             await TargetUser.findOneAndUpdate(
                 { phone: user.phone },
                 { $set: { name: user.name, emergency_contacts: user.emergency_contacts } },
                 { upsert: true }
             );
        }

        // Migrate Incidents (Only if they don't exist by timestamp/userId)
        for (const inc of incidents) {
            const exists = await TargetIncident.findOne({ userId: inc.userId, timestamp: inc.timestamp });
            if (!exists) {
                console.log(`Migrating Incident for ${inc.userId}...`);
                await new TargetIncident(inc.toObject()).save();
            }
        }

        console.log("--- MIGRATION COMPLETED SUCCESSFULLY ---");
        await sourceConn.close();
        await targetConn.close();
        process.exit(0);

    } catch (err) {
        console.error("MIGRATION FAILED:", err);
        process.exit(1);
    }
}

migrate();
