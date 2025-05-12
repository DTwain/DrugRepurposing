import requests
import os
from time import sleep
import random
import re
from multiprocessing import Pool, Manager, current_process
import multiprocessing as mp
from tqdm import tqdm
import argparse

def make_request_with_retry(url, max_retries=20, initial_delay=1):
    """Make a request to KEGG API with retries"""
    for attempt in range(max_retries):
        try:
            response = requests.get(url, timeout=30)
            if response.status_code == 200:
                return response
            
            print(f"Request failed with status code {response.status_code}, attempt {attempt+1}/{max_retries}")
        except Exception as e:
            print(f"Request failed with error: {e}, attempt {attempt+1}/{max_retries}")
        
        # Calculate exponential backoff with jitter
        delay = initial_delay + random.uniform(0, 1)
        # Cap at 60 seconds to avoid very long waits
        delay = min(delay, 60)
        sleep(delay)
    
    print(f"Failed after {max_retries} attempts")
    return None

def get_kegg_diseases():
    """Get a list of all human disease IDs from KEGG"""
    response = make_request_with_retry("https://rest.kegg.jp/list/disease")
    if not response:
        print("Could not retrieve disease list")
        return []
    
    diseases = []
    for line in response.text.strip().split('\n'):
        parts = line.split('\t')
        if len(parts) == 2:
            disease_id, description = parts
            if disease_id.startswith('H'):  # Human diseases start with H
                diseases.append(disease_id)  # Only store the ID
    
    return diseases

def get_disease_pathways(disease_id):
    """Get all pathways associated with a disease from multiple sources"""
    pathways = set()  # Use set to avoid duplicates
    
    # Method 1: Get pathways linked to disease directly
    response = make_request_with_retry(f"https://rest.kegg.jp/link/pathway/{disease_id}")
    if response and response.text.strip():
        for line in response.text.strip().split('\n'):
            parts = line.split('\t')
            if len(parts) == 2:
                disease, pathway = parts
                # Extract just the pathway identifier for KEGG format
                pathway_id = pathway.replace("path:", "")
                pathways.add(pathway_id)
    
    # Method 2: Get pathways from disease entry itself
    response = make_request_with_retry(f"https://rest.kegg.jp/get/{disease_id}")
    if response and response.text.strip():
        lines = response.text.strip().split('\n')
        in_pathway_section = False
        for line in lines:
            if line.startswith("PATHWAY"):
                in_pathway_section = True
                # Extract pathway from PATHWAY line
                pathway_part = line.replace("PATHWAY", "").strip()
                # Extract only the pathway ID (everything before first space or parenthesis)
                if pathway_part:
                    # Use regex to extract just "hsaXXXXX"
                    match = re.search(r'hsa\d{5}', pathway_part)
                    if match:
                        pathways.add(match.group(0))
            elif line.startswith("  ") and in_pathway_section:
                # Additional pathways in subsequent lines
                pathway_part = line.strip()
                # Extract only the pathway ID (everything before first space or parenthesis)
                if pathway_part:
                    # Use regex to extract just "hsaXXXXX"
                    match = re.search(r'hsa\d{5}', pathway_part)
                    if match:
                        pathways.add(match.group(0))
            elif not line.startswith("  ") and in_pathway_section:
                # End of pathway section
                in_pathway_section = False
    
    response = make_request_with_retry(f"https://rest.kegg.jp/link/hsa/{disease_id}")
    if response and response.text.strip():
        gene_count = 0
        for line in response.text.strip().split('\n'):
            parts = line.split('\t')
            if len(parts) == 2:
                disease, gene = parts
                gene_count += 1
                # Sleep after every few genes to avoid overloading the API
                if gene_count % 5 == 0:
                    sleep(1)
                
                # Get pathways for each gene
                gene_response = make_request_with_retry(f"https://rest.kegg.jp/link/pathway/{gene}")
                if gene_response and gene_response.text.strip():
                    for gene_line in gene_response.text.strip().split('\n'):
                        gene_parts = gene_line.split('\t')
                        if len(gene_parts) == 2:
                            gene_id, pathway = gene_parts
                            pathway_id = pathway.replace("path:", "")
                            pathways.add(pathway_id)
    
    return list(pathways), disease_id

def process_diseases_chunk(disease_ids, chunk_id):
    """Process a chunk of disease IDs and return results"""
    results = []
    for i, disease_id in enumerate(disease_ids):
        try:
            pathways, _ = get_disease_pathways(disease_id)
            results.append((disease_id, pathways))
            
            # Rate limiting - be nice to the API
            sleep_time = 0.5 + random.uniform(0, 0.5)
            sleep(sleep_time)
            
        except Exception as e:
            print(f"Process {chunk_id}: Error processing {disease_id}: {e}")
            results.append((disease_id, []))
    
    return results

def write_sql_file(results, output_file):
    """Write results to SQL file"""
    insert_count = 0
    diseases_with_pathways = 0
    diseases_without_pathways = 0
    
    with open(output_file, 'w', encoding='utf-8') as sql_file:
        # Write transaction begin
        sql_file.write("BEGIN TRANSACTION;\n\n")
        
        # Clear existing data
        sql_file.write("DELETE FROM DiseasePathways;\n")
        sql_file.write("-- Inserting disease-pathway relationships\n\n")
        
        # Write INSERT statements
        for disease_id, pathways in results:
            if pathways:
                diseases_with_pathways += 1
                for pathway in pathways:
                    # Add path: prefix for database storage
                    pathway_with_prefix = f"path:{pathway}"
                    insert_sql = f"INSERT INTO DiseasePathways (disease_id, pathway_id) VALUES ('{disease_id}', '{pathway_with_prefix}');\n"
                    sql_file.write(insert_sql)
                    insert_count += 1
            else:
                diseases_without_pathways += 1
        
        # Write transaction commit
        sql_file.write("\nCOMMIT;\n")
    
    return insert_count, diseases_with_pathways, diseases_without_pathways

def main():
    parser = argparse.ArgumentParser(description='Extract KEGG disease-pathway relationships using multiple processes')
    parser.add_argument('--n-workers', type=int, default=4, help='Number of worker processes')
    parser.add_argument('--output-dir', type=str, default='kegg_data', help='Output directory')
    args = parser.parse_args()
    
    # Create output directory if it doesn't exist
    os.makedirs(args.output_dir, exist_ok=True)
    
    # Get all human diseases
    print("Fetching human diseases from KEGG...")
    diseases = get_kegg_diseases()
    if not diseases:
        print("Failed to fetch diseases, exiting.")
        return
    
    print(f"Found {len(diseases)} diseases")
    print(f"Using {args.n_workers} worker processes")
    
    # Split diseases into chunks for parallel processing
    chunk_size = max(1, len(diseases) // args.n_workers)
    disease_chunks = [diseases[i:i + chunk_size] for i in range(0, len(diseases), chunk_size)]
    
    # Process diseases in parallel
    all_results = []
    with Pool(processes=args.n_workers) as pool:
        # Create list of arguments for each process
        process_args = [(chunk, idx) for idx, chunk in enumerate(disease_chunks)]
        
        # Use pool.starmap to process chunks in parallel
        chunk_results = pool.starmap(process_diseases_chunk, process_args)
        
        # Flatten results
        for chunk in chunk_results:
            all_results.extend(chunk)
    
    print("\nProcessing complete. Writing SQL file...")
    
    # Write results to SQL file
    output_file = os.path.join(args.output_dir, 'insert_disease_pathways.sql')
    insert_count, diseases_with_pathways, diseases_without_pathways = write_sql_file(all_results, output_file)
    
    print(f"\nSummary:")
    print(f"Generated {insert_count} INSERT statements for the DiseasePathways table")
    print(f"Diseases with pathways: {diseases_with_pathways}")
    print(f"Diseases without pathways: {diseases_without_pathways}")
    
    print(f"\nDone! SQL file created at: {output_file}")
    print("\nYou can now run this SQL file against your database to populate the DiseasePathways table.")

if __name__ == "__main__":
    # Set start method to 'spawn' for better compatibility with different platforms
    mp.set_start_method('spawn', force=True)
    main()