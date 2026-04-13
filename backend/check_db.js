const mongoose = require('mongoose');
require('dotenv').config();

const mongoUri = process.env.MONGO_URI || 'mongodb://localhost:27017/dualshield_db';
console.log("Connecting to:", mongoUri);

mongoose.connect(mongoUri)
    .then(async () => {
        console.log("Connected.");
        const collections = await mongoose.connection.db.listCollections().toArray();
        console.log("Collections:", collections.map(c => c.name));
        
        const User = mongoose.model('User', new mongoose.Schema({ phone: String, name: String }));
        const users = await User.find({});
        console.log("Found Users:", users.length);
        users.forEach(u => console.log(`- ${u.name} (${u.phone})`));

        process.exit(0);
    })
    .catch(err => {
        console.error("Error:", err);
        process.exit(1);
    });
