import json
import requests
import argparse
import time

def get_pathways_for_gene(gene_id):
    """Retrieve KEGG pathway IDs for a given gene ID, retrying up to 10 times upon errors."""
    url = f"https://rest.kegg.jp/link/pathway/{gene_id}"
    max_attempts = 200
    attempt = 0
    while attempt < max_attempts:
        try:
            # Optionally, set a timeout to avoid hanging indefinitely
            response = requests.get(url, timeout=10)
            response.raise_for_status()  # Raises HTTPError for bad responses (4xx or 5xx)
            
            pathways = []
            # Each line is tab-separated: gene_id <tab> pathway:XXXXX
            for line in response.text.strip().splitlines():
                parts = line.split("\t")
                if len(parts) == 2:
                    pathways.append(parts[1])
            return pathways
        
        except requests.exceptions.RequestException as e:
            attempt += 1
            print(f"Attempt {attempt}/{max_attempts} failed for gene {gene_id}. Error: {e}")
            # Wait a second before retrying (you can adjust this delay if needed)
            time.sleep(2)
    
    # After all attempts have failed, log and return an empty list
    print(f"All {max_attempts} attempts failed for gene {gene_id}. Skipping.")
    return []

def main():
    parser = argparse.ArgumentParser(description="Process a batch of genes from gene_map.json.")
    parser.add_argument(
        "--batch-index",
        type=int,
        required=True,
        help=("Index of the batch to process (0-indexed). For example, 0 for the first 2000 genes, "
              "1 for the next 2000, etc.")
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=2000,
        help="Number of genes to process in one batch (default: 2000)."
    )
    args = parser.parse_args()

    # Load the JSON file containing the gene map
    with open('../../../../../resources/data/gene_map.json', 'r') as file:
        gene_data = json.load(file)

    gene_ids = list(gene_data.keys())
    start_index = args.batch_index * args.batch_size
    end_index = start_index + args.batch_size
    end_index = min(end_index, len(gene_ids))
    batch_gene_ids = gene_ids[start_index:end_index]

    print(f"Processing genes from index {start_index} to {end_index}...")

    gene_to_pathways = {}
    all_pathways = set()

    # Process only the genes in the current batch
    for gene_id in batch_gene_ids:
        pathways = get_pathways_for_gene(gene_id)
        print(f"{gene_id}: {pathways}")
        gene_to_pathways[gene_id] = pathways
        all_pathways.update(pathways)

    unique_pathways = list(all_pathways)

    # Display results in the console
    print("\nPathways per gene:")
    for gene, pathways in gene_to_pathways.items():
        print(f"{gene}: {pathways}")

    print("\nUnique pathways found in this batch:")
    print(unique_pathways)

    # Save results to a JSON file named according to the batch index
    output_data = {
        "gene_to_pathways": gene_to_pathways,
        "unique_pathways": unique_pathways
    }
    output_filename = f"pathways_output_batch_{args.batch_index}.json"
    with open(output_filename, "w") as outfile:
        json.dump(output_data, outfile, indent=4)
    
    print(f"\nOutput saved to: {output_filename}")

if __name__ == "__main__":
    main()
