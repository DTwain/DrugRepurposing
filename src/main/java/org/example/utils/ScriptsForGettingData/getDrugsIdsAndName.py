#!/usr/bin/env python3
import requests
import json

def get_drugs(url):
    """
    Fetch the drug list from the given URL and parse each line into a dictionary.
    
    Args:
        url (str): The URL to fetch the drug data from.
    
    Returns:
        list: A list of dictionaries, each containing the drug id and its description.
    """
    try:
        response = requests.get(url)
        response.raise_for_status()  # Raise an exception if the HTTP request returned an unsuccessful status
    except Exception as e:
        print(f"Error fetching data from {url}: {e}")
        return []

    # Each line is expected to contain the drug id and its description separated by a tab.
    lines = response.text.strip().split("\n")
    drugs = []
    
    for line in lines:
        parts = line.split("\t")
        if len(parts) >= 2:
            drug_entry = {
                "id": parts[0],
                "description": parts[1].strip()  # may contain multiple names or synonyms separated by semicolons
            }
            drugs.append(drug_entry)
    
    return drugs

def save_to_json(data, filename):
    """
    Save the provided data to a JSON file.
    
    Args:
        data (Any): The data to be saved.
        filename (str): The filename of the JSON file.
    """
    try:
        with open(filename, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=4)
        print(f"Data successfully saved to {filename}")
    except Exception as e:
        print(f"Error saving data to {filename}: {e}")

if __name__ == "__main__":
    url = "https://rest.kegg.jp/list/drug"
    drugs = get_drugs(url)
    
    if drugs:
        save_to_json(drugs, "../../../../../resources/data/drugs.json")
        print(f"Saved {len(drugs)} drugs to 'drugs.json'")
    else:
        print("No drug data was retrieved.")
