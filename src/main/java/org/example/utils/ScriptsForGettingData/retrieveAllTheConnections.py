import json
import requests
import xml.etree.ElementTree as ET
import time

def fetch_kgml(pathway_id, max_attempts=200):
    """
    Given a pathway ID (e.g., "path:hsa05110"), attempt to fetch
    its KGML (XML) representation from the KEGG REST API.
    """
    url = f"https://rest.kegg.jp/get/{pathway_id}/kgml"
    attempt = 0
    while attempt < max_attempts:
        try:
            response = requests.get(url, timeout=10)
            response.raise_for_status()  # raises an exception for bad responses
            return response.text
        except requests.exceptions.RequestException as e:
            attempt += 1
            print(f"Attempt {attempt}/{max_attempts} for {pathway_id} failed: {e}")
            time.sleep(1)  # pause before retrying
    print(f"All attempts failed for {pathway_id}. Skipping this pathway.")
    return None

def parse_kgml(kgml_content):
    """
    Parses the provided KGML content (as a string) and extracts
    connection (relation) information.
    
    Returns a list of connections where each connection is a dict:
      - source: { "name": <entry1 name>, "type": <entry1 type> }
      - relation_type: value from the relation attribute "type"
      - subtypes: list of dictionaries with each subtype's name and value
      - target: { "name": <entry2 name>, "type": <entry2 type> }
    """
    # Parse the XML content
    root = ET.fromstring(kgml_content)
    
    # Build a dictionary mapping each entry id to its details (name & type)
    entries = {}
    for entry in root.findall("entry"):
        entry_id = entry.get("id")
        entry_name = entry.get("name")  # may contain more than one name
        entry_type = entry.get("type")
        entries[entry_id] = {"name": entry_name, "type": entry_type}
    
    # Process the relations between the entries
    connections = []
    for relation in root.findall("relation"):
        entry1_id = relation.get("entry1")
        entry2_id = relation.get("entry2")
        relation_type = relation.get("type")
        subtypes = []
        for subtype in relation.findall("subtype"):
            subtype_name = subtype.get("name")
            subtype_value = subtype.get("value")
            subtypes.append({"name": subtype_name, "value": subtype_value})
        
        # Look up the corresponding entry details; if not found, set as None
        source_info = entries.get(entry1_id, {"name": None, "type": None})
        target_info = entries.get(entry2_id, {"name": None, "type": None})
        
        connection = {
            "source": source_info,
            "relation_type": relation_type,
            "subtypes": subtypes,
            "target": target_info
        }
        connections.append(connection)
    
    return connections

def main():
    # Load unique pathways from pathways_output.json
    with open("data/pathways_output.json", "r") as infile:
        data = json.load(infile)
    
    unique_pathways = data.get("unique_pathways", [])
    print(f"Found {len(unique_pathways)} unique pathways to process.")
    
    # Dictionary to hold all pathways' connections
    pathways_connections = {}
    
    # Iterate over each unique pathway
    for pathway_id in unique_pathways:
        print(f"\nProcessing pathway: {pathway_id}")
        kgml_content = fetch_kgml(pathway_id)
        if kgml_content is None:
            continue  # skip if unable to fetch KGML
        connections = parse_kgml(kgml_content)
        pathways_connections[pathway_id] = connections
        print(f"Found {len(connections)} relation(s) in {pathway_id}.")
    
    # Save the merged connections into a JSON file
    output_filename = "../../../../../resources/data/pathway_connections.json"
    with open(output_filename, "w") as outfile:
        json.dump(pathways_connections, outfile, indent=4)
    
    print(f"\nAll connections have been saved to {output_filename}")

if __name__ == "__main__":
    main()
