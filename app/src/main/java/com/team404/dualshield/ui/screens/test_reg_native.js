const http = require('http');

const data = JSON.stringify({
    name: "Test User Native",
    phone: "9999911111",
    emergency_name: "Native Guardian",
    emergency_phone: "0000011111"
});

const options = {
    hostname: 'localhost',
    port: 5000,
    path: '/api/users/register',
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'Content-Length': data.length
    }
};

const req = http.request(options, (res) => {
    console.log(`Status: ${res.statusCode}`);
    res.on('data', (d) => {
        process.stdout.write(d);
    });
});

req.on('error', (error) => {
    console.error('Error:', error.message);
    // Try port 5001 if 5000 fails
    if (options.port === 5000) {
        console.log("Trying port 5001...");
        options.port = 5001;
        const req2 = http.request(options, (res2) => {
            console.log(`Status: ${res2.statusCode}`);
            res2.on('data', (d) => {
                process.stdout.write(d);
            });
        });
        req2.on('error', (e) => console.error('Error on 5001:', e.message));
        req2.write(data);
        req2.end();
    }
});

req.write(data);
req.end();
