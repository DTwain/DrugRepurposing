import requests, json

URL = "https://rest.kegg.jp/list/disease"
resp = requests.get(URL)
resp.raise_for_status()

records = []
for line in resp.text.strip().splitlines():
    eid, desc = line.split("\t", 1)
    records.append({"id": eid.replace("disease:", ""), "description": desc})

with open("../../../../../resources/data/diseases.json", "w", encoding="utf-8") as f:
    json.dump(records, f, indent=2, ensure_ascii=False)

print(f"Saved {len(records)} diseases to diseases.json")
