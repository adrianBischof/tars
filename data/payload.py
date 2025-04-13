import json

# Generate the list of entries
entries = [{
    "tenant_id": "1",
    "device_id": "1"
} for i in range(0, 401)]

# Save the entries to a JSON file
file_path = './persistence_query.json'
with open(file_path, 'w') as f:
    json.dump(entries, f, indent=4)

file_path