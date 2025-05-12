import json
import requests
import xml.etree.ElementTree as ET
import argparse
import time

MAX_RETRIES = 20
RETRY_DELAY = 1  # seconds

def update_compound_ids(input_file, output_file):
    # Load the JSON data
    with open(input_file, 'r') as f:
        data = json.load(f)

    # Iterate over each pathway in the JSON
    for pathway_key, interactions in data.items():
        pathway_id = pathway_key.split(':', 1)[1]
        print(f"\nProcessing pathway {pathway_id} ({pathway_key})...")

        # Try up to MAX_RETRIES to fetch the KGML
        url = f'https://rest.kegg.jp/get/{pathway_id}/kgml'
        for attempt in range(1, MAX_RETRIES + 1):
            try:
                response = requests.get(url, timeout=10)
                response.raise_for_status()
                print(f"  ✓ Fetched KGML on attempt {attempt}")
                break
            except requests.RequestException as e:
                print(f"  ⚠ Attempt {attempt}/{MAX_RETRIES} failed: {e}")
                if attempt < MAX_RETRIES:
                    time.sleep(RETRY_DELAY)
        else:
            print(f"  ✗ Failed to fetch KGML for {pathway_id} after {MAX_RETRIES} attempts, skipping.")
            continue

        # Parse the returned KGML (XML)
        root = ET.fromstring(response.text)

        # Build a map: entry_id -> compound_name for compound entries
        entry_map = {
            entry.get('id'): entry.get('name')
            for entry in root.findall('entry')
            if entry.get('type') == 'compound'
        }
        print(f"  → Found {len(entry_map)} compound entries in KGML")

        # Update the JSON: replace subtype value when name="compound"
        replaced_count = 0
        for interaction in interactions:
            for subtype in interaction.get('subtypes', []):
                if subtype.get('name') == 'compound':
                    entry_id = subtype.get('value')
                    compound_name = entry_map.get(entry_id)
                    if compound_name:
                        subtype['value'] = compound_name
                        replaced_count += 1
                        print(f"    • Replaced entry_id {entry_id} → {compound_name}")

        print(f"  → Total replacements in this pathway: {replaced_count}")

    # Write the updated JSON back to a file
    with open(output_file, 'w') as f:
        json.dump(data, f, indent=2)
    print(f"\nDone! Updated JSON written to {output_file}")

def main():
    parser = argparse.ArgumentParser(
        description="Replace subtype 'compound' values with actual compound IDs from KEGG KGML, with retries and progress feedback."
    )
    parser.add_argument(
        "input_json", help="Path to the pathway_connections.json file"
    )
    parser.add_argument(
        "output_json", help="Path to write the updated JSON"
    )
    args = parser.parse_args()

    update_compound_ids(args.input_json, args.output_json)

if __name__ == "__main__":
    main()
