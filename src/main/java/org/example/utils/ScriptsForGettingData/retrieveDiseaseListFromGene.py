#!/usr/bin/env python3
import json
import time
import requests
import argparse
from requests.exceptions import RequestException

# Path to your gene_map.json
GENE_MAP_FILE   = r"D:\\Facultate\\polihack\\src\\main\\resources\\data\\gene_map.json"
KEGG_URL        = "https://rest.kegg.jp/link/disease/{}"

# Retry settings
MAX_ATTEMPTS    = 200
RETRY_DELAY_SEC = 2

def get_diseases_for_gene(gene_id):
    """
    Retrieve KEGG disease IDs for a given gene_id, retrying up to MAX_ATTEMPTS.
    Returns a list of strings like "disease:H00001", etc.
    """
    url = KEGG_URL.format(gene_id)
    attempt = 0

    while attempt < MAX_ATTEMPTS:
        try:
            resp = requests.get(url, timeout=10)
            resp.raise_for_status()
            diseases = []
            for line in resp.text.strip().splitlines():
                parts = line.split("\t")
                if len(parts) == 2:
                    diseases.append(parts[1])
            return diseases

        except RequestException as e:
            attempt += 1
            print(f"Attempt {attempt}/{MAX_ATTEMPTS} failed for {gene_id}: {e}")
            time.sleep(RETRY_DELAY_SEC)

    print(f"All {MAX_ATTEMPTS} attempts failed for {gene_id}. Skipping.")
    return []

def main():
    parser = argparse.ArgumentParser(
        description="Fetch batch of gene→disease mappings from KEGG"
    )
    parser.add_argument(
        "--batch-index", type=int, required=True,
        help="Zero‑based batch index (0 = first 2000 genes, 1 = next 2000, …)"
    )
    parser.add_argument(
        "--batch-size", type=int, default=2000,
        help="Number of genes per batch (default: 2000)"
    )
    args = parser.parse_args()

    # load gene_map.json (assumes it's a dict of gene_id → ...)
    with open(GENE_MAP_FILE, 'r', encoding='utf-8') as f:
        gene_data = json.load(f)

    gene_ids = list(gene_data.keys())
    total = len(gene_ids)

    start = args.batch_index * args.batch_size
    end   = min(start + args.batch_size, total)
    batch = gene_ids[start:end]

    print(f"Processing genes {start}–{end-1} (of {total})")

    gene_to_diseases = {}
    all_diseases = set()

    for i, gid in enumerate(batch, start=1):
        diseases = get_diseases_for_gene(gid)
        gene_to_diseases[gid] = diseases
        all_diseases.update(diseases)
        print(f"[{i}/{len(batch)}] {gid} → {len(diseases)} diseases")

    output = {
        "gene_to_diseases": gene_to_diseases,
        "unique_diseases": sorted(all_diseases)
    }

    out_fname = f"diseases_output_batch_{args.batch_index}.json"
    with open(out_fname, 'w', encoding='utf-8') as outf:
        json.dump(output, outf, indent=4, ensure_ascii=False)

    print(f"\nWrote {len(gene_to_diseases)} genes and "
          f"{len(all_diseases)} unique diseases to {out_fname}")

if __name__ == "__main__":
    main()
