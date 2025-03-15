import json

# Generate the list of entries
entries = [{
        "autoReconnect": True,
        "backpressure": 10,
        "cleanSession": True,
        "tenant_id": f"{i}",
        "device_id": f"{i}",
        "location": "Room 15",
        "name": "Shelly HT Plus",
        "provision_service": "elit ipsum ea sint",
        "server": "tcp://37.27.243.155",
        "topics": [
            "shellyplusht-e86beae8d784/status/temperature:0"
        ]
    } for i in range(0, 401)]

# Save the entries to a JSON file
file_path = './provisioning.json'
with open(file_path, 'w') as f:
    json.dump(entries, f, indent=4)

file_path