#!/usr/bin/env python3
import requests
import json

def get_compounds(url):
    """
    Fetch the compound list from the given URL and parse each line into a dictionary.

    Args:
        url (str): The KEGG REST endpoint for compounds.

    Returns:
        list of dict: Each dict has keys 'id' and 'description'.
    """
    try:
        resp = requests.get(url)
        resp.raise_for_status()
    except requests.RequestException as e:
        print(f"Error fetching data from {url}: {e}")
        return []

    lines = resp.text.strip().splitlines()
    compounds = []
    for line in lines:
        parts = line.split("\t", 1)
        if len(parts) == 2:
            compounds.append({
                "id": parts[0],
                "description": parts[1].strip()
            })
    return compounds

def save_to_json(data, filename):
    """
    Save the provided data structure to a JSON file.

    Args:
        data: The Python object to serialize.
        filename (str): Path to the output JSON file.
    """
    try:
        with open(filename, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"Wrote {len(data)} records to {filename}")
    except IOError as e:
        print(f"Error writing to {filename}: {e}")

if __name__ == "__main__":
    KEGG_COMPOUND_URL = "https://rest.kegg.jp/list/compound"
    output_file = "../../../../../resources/data/compounds.json"

    compounds = get_compounds(KEGG_COMPOUND_URL)
    if compounds:
        save_to_json(compounds, output_file)
    else:
        print("No compounds were retrieved.")
