#!/usr/bin/env python3
"""
Parallel processing version of drug-pathway relationship finder
"""

import requests
import sqlite3
import time
import re
import json
from typing import List, Dict, Tuple, Set
from collections import defaultdict
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, as_completed
from multiprocessing import Pool, cpu_count
import queue
import threading
from functools import partial

# Configuration
DB_PATH = "D:\\Facultate\\polihack\\GeneExplorer.db"
KEGG_BASE_URL = "http://rest.kegg.jp"
MAX_WORKERS = min(16, cpu_count() * 2)  # Limit to 16 max concurrent connections
BATCH_SIZE = 100
API_TIMEOUT = 30

class RateLimiter:
    """Thread-safe rate limiter for API requests"""
    def __init__(self, max_calls: int, time_period: float):
        self.max_calls = max_calls
        self.time_period = time_period
        self.calls = queue.Queue()
        self.lock = threading.Lock()
    
    def acquire(self):
        with self.lock:
            current_time = time.time()
            
            # Remove old calls
            while not self.calls.empty():
                old_call = self.calls.queue[0]
                if current_time - old_call < self.time_period:
                    break
                self.calls.get()
            
            # Wait if we've hit the limit
            if self.calls.qsize() >= self.max_calls:
                sleep_time = self.calls.queue[0] + self.time_period - current_time
                if sleep_time > 0:
                    time.sleep(sleep_time)
            
            self.calls.put(current_time)

class ParallelDrugPathwayFinder:
    def __init__(self, db_path: str):
        self.db_path = db_path
        self.drug_ids = []
        self.pathway_ids = []
        self.drug_pathway_pairs = set()  # Use set for thread-safe operations
        self.rate_limiter = RateLimiter(max_calls=50, time_period=60)  # 50 calls per minute
        self.results_lock = threading.Lock()
        
    def connect_db(self):
        """Connect to the database"""
        return sqlite3.connect(self.db_path)
    
    def fetch_base_data(self):
        """Fetch drug and pathway IDs from database"""
        conn = self.connect_db()
        cursor = conn.cursor()
        
        cursor.execute("SELECT id FROM Drugs")
        self.drug_ids = [row[0] for row in cursor.fetchall()]
        
        cursor.execute("SELECT pathway_id FROM Pathways")
        self.pathway_ids = [row[0] for row in cursor.fetchall()]
        
        conn.close()
        print(f"Loaded {len(self.drug_ids)} drugs and {len(self.pathway_ids)} pathways")
    
    def kegg_api_request(self, endpoint: str) -> str:
        """Make rate-limited request to KEGG API"""
        self.rate_limiter.acquire()
        url = f"{KEGG_BASE_URL}/{endpoint}"
        
        try:
            response = requests.get(url, timeout=API_TIMEOUT)
            if response.status_code == 200:
                return response.text
            elif response.status_code == 404:
                return None
            else:
                print(f"Error {response.status_code} for {url}")
                return None
        except Exception as e:
            print(f"Exception for {url}: {e}")
            return None
    
    def process_drug_batch(self, drug_batch: List[str]) -> List[Tuple[str, str]]:
        """Process a batch of drugs for direct pathway information"""
        pairs = []
        for drug_id in drug_batch:
            drug_data = self.kegg_api_request(f"get/{drug_id}")
            if drug_data:
                pathway_ids = self._extract_pathways_from_entry(drug_data)
                for pathway_id in pathway_ids:
                    if pathway_id in self.pathway_ids:
                        pairs.append((drug_id, pathway_id))
        return pairs
    
    def process_pathway_batch(self, pathway_batch: List[str]) -> List[Tuple[str, str]]:
        """Process a batch of pathways for drug listings"""
        pairs = []
        for pathway_id in pathway_batch:
            pathway_data = self.kegg_api_request(f"get/{pathway_id}")
            if pathway_data:
                drug_ids = self._extract_drugs_from_pathway(pathway_data)
                for drug_id in drug_ids:
                    if drug_id in self.drug_ids:
                        pairs.append((drug_id, pathway_id))
        return pairs
    
    def process_drug_target_pair(self, drug_id: str) -> List[Tuple[str, str]]:
        """Process drug-target-pathway relationships"""
        pairs = []
        drug_data = self.kegg_api_request(f"get/{drug_id}")
        if not drug_data:
            return pairs
            
        target_genes = self._extract_drug_targets(drug_data)
        
        for gene_id in target_genes:
            gene_data = self.kegg_api_request(f"get/{gene_id}")
            if gene_data:
                pathway_ids = self._extract_pathways_from_entry(gene_data)
                for pathway_id in pathway_ids:
                    if pathway_id in self.pathway_ids:
                        pairs.append((drug_id, pathway_id))
        
        return pairs
    
    def method1_parallel_direct_drug_pathway(self):
        """Method 1: Parallel processing of drug entries"""
        print("\n=== Method 1: Parallel Direct Drug-Pathway Information ===")
        
        # Split drugs into batches
        drug_batches = [self.drug_ids[i:i + BATCH_SIZE] 
                       for i in range(0, len(self.drug_ids), BATCH_SIZE)]
        
        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
            futures = [executor.submit(self.process_drug_batch, batch) 
                      for batch in drug_batches]
            
            for i, future in enumerate(as_completed(futures)):
                pairs = future.result()
                with self.results_lock:
                    self.drug_pathway_pairs.update(pairs)
                print(f"Processed drug batch {i+1}/{len(drug_batches)}")
    
    def method2_parallel_drug_targets(self):
        """Method 2: Parallel processing of drug targets"""
        print("\n=== Method 2: Parallel Drug Targets and Pathways ===")
        
        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
            futures = {executor.submit(self.process_drug_target_pair, drug_id): drug_id 
                      for drug_id in self.drug_ids}
            
            completed = 0
            for future in as_completed(futures):
                pairs = future.result()
                with self.results_lock:
                    self.drug_pathway_pairs.update(pairs)
                completed += 1
                if completed % 100 == 0:
                    print(f"Processed {completed}/{len(self.drug_ids)} drugs")
    
    def method3_parallel_pathway_drugs(self):
        """Method 3: Parallel processing of pathway drug listings"""
        print("\n=== Method 3: Parallel Pathway Drug Listings ===")
        
        # Split pathways into batches
        pathway_batches = [self.pathway_ids[i:i + BATCH_SIZE] 
                          for i in range(0, len(self.pathway_ids), BATCH_SIZE)]
        
        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
            futures = [executor.submit(self.process_pathway_batch, batch) 
                      for batch in pathway_batches]
            
            for i, future in enumerate(as_completed(futures)):
                pairs = future.result()
                with self.results_lock:
                    self.drug_pathway_pairs.update(pairs)
                print(f"Processed pathway batch {i+1}/{len(pathway_batches)}")
    
    def method4_parallel_kgml_parsing(self):
        """Method 4: Parallel KGML file parsing"""
        print("\n=== Method 4: Parallel KGML Pathway Parsing ===")
        
        def process_kgml(pathway_id: str) -> List[Tuple[str, str]]:
            pairs = []
            pathway_code = pathway_id.replace('path:', '')
            kgml_data = self.kegg_api_request(f"get/{pathway_code}/kgml")
            
            if kgml_data:
                try:
                    root = ET.fromstring(kgml_data)
                    for entry in root.findall('.//entry'):
                        if entry.get('type') == 'drug':
                            drug_entries = entry.get('name', '').split()
                            for drug_entry in drug_entries:
                                if drug_entry in self.drug_ids:
                                    pairs.append((drug_entry, pathway_id))
                except ET.ParseError:
                    pass
            return pairs
        
        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
            futures = {executor.submit(process_kgml, pathway_id): pathway_id 
                      for pathway_id in self.pathway_ids}
            
            completed = 0
            for future in as_completed(futures):
                pairs = future.result()
                with self.results_lock:
                    self.drug_pathway_pairs.update(pairs)
                completed += 1
                if completed % 10 == 0:
                    print(f"Processed KGML for {completed}/{len(self.pathway_ids)} pathways")
    
    def method5_disease_connections(self):
        """Method 5: Disease-Drug-Pathway connections (database-based)"""
        print("\n=== Method 5: Disease-Drug-Pathway Connections ===")
        
        conn = self.connect_db()
        cursor = conn.cursor()
        
        # Get all disease-drug relationships
        cursor.execute("SELECT disease_id, drug_id FROM DiseaseDrugs")
        disease_drug_pairs = cursor.fetchall()
        
        # Get unique diseases
        diseases = list(set([pair[0] for pair in disease_drug_pairs]))
        
        def get_disease_pathways(disease_id: str) -> List[str]:
            disease_data = self.kegg_api_request(f"get/{disease_id}")
            if disease_data:
                return self._extract_pathways_from_entry(disease_data)
            return []
        
        # Parallel processing of disease pathways
        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
            future_to_disease = {executor.submit(get_disease_pathways, disease): disease 
                               for disease in diseases}
            
            disease_pathways = {}
            for future in as_completed(future_to_disease):
                disease = future_to_disease[future]
                disease_pathways[disease] = future.result()
        
        # Connect drugs to pathways via diseases
        pairs = []
        for disease_id, drug_id in disease_drug_pairs:
            if disease_id in disease_pathways:
                for pathway_id in disease_pathways[disease_id]:
                    if pathway_id in self.pathway_ids:
                        pairs.append((drug_id, pathway_id))
        
        with self.results_lock:
            self.drug_pathway_pairs.update(pairs)
        
        conn.close()
    
    def _extract_pathways_from_entry(self, entry_text: str) -> List[str]:
        """Extract pathway IDs from any KEGG entry"""
        pathways = []
        in_pathway_section = False
        
        for line in entry_text.split('\n'):
            if line.startswith('PATHWAY'):
                in_pathway_section = True
                matches = re.findall(r'hsa\d+', line)
                pathways.extend([f"path:{match}" for match in matches])
            elif in_pathway_section and line.startswith('  '):
                matches = re.findall(r'hsa\d+', line)
                pathways.extend([f"path:{match}" for match in matches])
            elif not line.startswith('  '):
                in_pathway_section = False
        
        return pathways
    
    def _extract_drug_targets(self, drug_entry: str) -> List[str]:
        """Extract target genes from drug entry"""
        targets = []
        in_target_section = False
        
        for line in drug_entry.split('\n'):
            if line.startswith('TARGET'):
                in_target_section = True
                matches = re.findall(r'hsa:\d+', line)
                targets.extend(matches)
            elif in_target_section and line.startswith('  '):
                matches = re.findall(r'hsa:\d+', line)
                targets.extend(matches)
            elif not line.startswith('  '):
                in_target_section = False
        
        return targets
    
    def _extract_drugs_from_pathway(self, pathway_entry: str) -> List[str]:
        """Extract drug IDs from pathway entry"""
        drugs = []
        in_drug_section = False
        
        for line in pathway_entry.split('\n'):
            if line.startswith('DRUG'):
                in_drug_section = True
                matches = re.findall(r'D\d+', line)
                drugs.extend(matches)
            elif in_drug_section and line.startswith('  '):
                matches = re.findall(r'D\d+', line)
                drugs.extend(matches)
            elif not line.startswith('  '):
                in_drug_section = False
        
        return drugs
    
    def save_results(self):
        """Save all drug-pathway relationships to database"""
        conn = self.connect_db()
        cursor = conn.cursor()
        
        # Convert set to list for database insertion
        unique_pairs = list(self.drug_pathway_pairs)
        
        print(f"\nSaving {len(unique_pairs)} unique drug-pathway relationships...")
        
        # Create table if not exists
        cursor.execute("""
        CREATE TABLE IF NOT EXISTS DrugPathways (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                drug_id     TEXT NOT NULL,
                pathway_id  TEXT NOT NULL,
                FOREIGN KEY (drug_id) REFERENCES Drugs(id)
                    ON DELETE CASCADE
                    ON UPDATE CASCADE,
                FOREIGN KEY (pathway_id) REFERENCES Pathways(pathway_id)
                    ON DELETE CASCADE
                    ON UPDATE CASCADE,
                UNIQUE(drug_id, pathway_id)
        )
        """)
        
        # Insert in batches for better performance
        batch_size = 1000
        for i in range(0, len(unique_pairs), batch_size):
            batch = unique_pairs[i:i + batch_size]
            cursor.executemany("""
                INSERT OR IGNORE INTO DrugPathways (drug_id, pathway_id) 
                VALUES (?, ?)
            """, batch)
            conn.commit()
            print(f"Inserted batch {i//batch_size + 1}")
        
        # Get count for verification
        cursor.execute("SELECT COUNT(*) FROM DrugPathways")
        total_count = cursor.fetchone()[0]
        print(f"Total drug-pathway relationships in database: {total_count}")
        
        conn.close()
    
    def run_parallel_methods(self):
        """Execute all parallel methods"""
        print("Starting parallel drug-pathway relationship search...")
        print(f"Using {MAX_WORKERS} worker threads")
        
        start_time = time.time()
        
        self.fetch_base_data()
        
        # Run methods in parallel where possible
        try:
            self.method1_parallel_direct_drug_pathway()
            self.method2_parallel_drug_targets()
            self.method3_parallel_pathway_drugs()
            self.method4_parallel_kgml_parsing()
            self.method5_disease_connections()
        except Exception as e:
            print(f"Error during execution: {e}")
        finally:
            # Save results even if there's an error
            self.save_results()
        
        end_time = time.time()
        print(f"\nTotal execution time: {(end_time - start_time) / 3600:.2f} hours")
        
        # Generate report
        self.generate_report()
    
    def generate_report(self):
        """Generate a summary report of the findings"""
        conn = self.connect_db()
        cursor = conn.cursor()
        
        # Get statistics
        cursor.execute("""
            SELECT drug_id, COUNT(*) as pathway_count 
            FROM DrugPathways 
            GROUP BY drug_id
        """)
        drug_counts = cursor.fetchall()
        
        cursor.execute("""
            SELECT pathway_id, COUNT(*) as drug_count 
            FROM DrugPathways 
            GROUP BY pathway_id
        """)
        pathway_counts = cursor.fetchall()
        
        # Generate report
        print("\n=== DRUG-PATHWAY RELATIONSHIP REPORT ===")
        print(f"Total drugs in database: {len(self.drug_ids)}")
        print(f"Total pathways in database: {len(self.pathway_ids)}")
        print(f"Total drug-pathway relationships found: {len(self.drug_pathway_pairs)}")
        
        # Drugs with pathways
        drugs_with_pathways = len(drug_counts)
        print(f"Drugs with at least one pathway: {drugs_with_pathways}")
        print(f"Drugs without pathways: {len(self.drug_ids) - drugs_with_pathways}")
        
        conn.close()

def main():
    finder = ParallelDrugPathwayFinder(DB_PATH)
    finder.run_parallel_methods()

if __name__ == "__main__":
    main()