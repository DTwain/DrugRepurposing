import os
import time
import logging
import argparse
import requests
import sqlite3
import random
import pubchempy as pcp
from rdkit import Chem
import pandas as pd
from concurrent.futures import ThreadPoolExecutor, as_completed

# ----------------------------
# Configuration & Logging
# ----------------------------
LOG_FILE = 'kegg_data_import.log'
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(LOG_FILE),
        logging.StreamHandler()  # Also print to console
    ]
)
logger = logging.getLogger(__name__)

os.makedirs('mol_files', exist_ok=True)
os.makedirs('data_files', exist_ok=True)

# ----------------------------
# Database Connection
# ----------------------------
def get_db_connection(db_path=r'D:\Facultate\polihack\GeneExplorer.db'):
    conn = sqlite3.connect(db_path, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    return conn

# ----------------------------
# Database Optimization
# ----------------------------
def optimize_database():
    """Apply SQLite optimizations for faster bulk inserts"""
    conn = get_db_connection()
    try:
        # Disable synchronous mode for faster writes
        conn.execute("PRAGMA synchronous = OFF")
        
        # Use memory for journal mode (faster but less safe)
        conn.execute("PRAGMA journal_mode = MEMORY")
        
        # Increase cache size (64MB)
        conn.execute("PRAGMA cache_size = -64000")
        
        # Disable auto vacuum
        conn.execute("PRAGMA auto_vacuum = 0")
        
        # Use WAL mode for better concurrency
        conn.execute("PRAGMA journal_mode = WAL")
        
        conn.commit()
        logger.info("Database optimizations applied")
    except Exception as e:
        logger.error(f"Failed to apply database optimizations: {e}")
    finally:
        conn.close()

# ----------------------------
# HTTP Helper with Retries
# ----------------------------
def request_with_retry(url, max_retries=20, pause=1):
    for attempt in range(1, max_retries + 1):
        try:
            resp = requests.get(url)
            if resp.status_code == 200 and resp.text.strip():
                logger.info(f"Success on attempt {attempt}: {url} returned {resp.status_code}")
                return resp
            logger.warning(f"Attempt {attempt}: {url} returned {resp.status_code}")
        except Exception as e:
            logger.warning(f"Attempt {attempt}: error fetching {url}: {e}")
        time.sleep(pause + random.uniform(0.1, 1.0))
    logger.error(f"Failed to fetch {url} after {max_retries} attempts")
    return None

def fetch_from_pubchem(cid):
    """Fetch Molfile from PubChem via PUG-REST."""
    url = f"https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/cid/{cid}/SDF"
    resp = request_with_retry(url)
    return resp.text if resp and resp.status_code == 200 else None

def extract_pubchem_id(entry_id):
    """Parse KEGG flatfile for a PubChem CID in DBLINKS."""
    resp = request_with_retry(f"https://rest.kegg.jp/get/{entry_id}")
    if not resp:
        return None
    for line in resp.text.splitlines():
        if "PubChem:" in line:
            parts = line.split("PubChem:")
            if len(parts) > 1:
                return parts[1].strip()
    return None

# ----------------------------
# KEGG Data Fetchers
# ----------------------------
def get_kegg_list(path):
    url = f"https://rest.kegg.jp/list/{path}"
    resp = request_with_retry(url)
    return [line.split('\t')[0] for line in resp.text.strip().splitlines()] if resp else []

def get_kegg_compounds(): return get_kegg_list("compound")
def get_kegg_drugs():     return get_kegg_list("drug")
def get_kegg_diseases():  return get_kegg_list("disease")

def get_mol_file(entry_id):
    """Download & cache MDL Molfile"""
    file_path = os.path.join("mol_files", f"{entry_id}.mol")
    if os.path.exists(file_path):
        return file_path

    # Try KEGG
    resp = request_with_retry(f"https://rest.kegg.jp/get/{entry_id}/mol")
    text = resp.text if resp and resp.status_code == 200 else None

    # Fallback to PubChem
    if not text:
        cid = extract_pubchem_id(entry_id)
        if cid:
            text = fetch_from_pubchem(cid)

    if not text:
        logger.warning(f"No Molfile available for {entry_id}")
        return None

    with open(file_path, "w") as f:
        f.write(text)
    return file_path

def mol_to_smiles(mol_file):
    try:
        m = Chem.MolFromMolFile(mol_file)
        return Chem.MolToSmiles(m) if m else None
    except Exception as e:
        logger.error(f"MOL→SMILES error {mol_file}: {e}")
        return None

def get_smiles_from_pubchem(kegg_id):
    resp = request_with_retry(f"https://rest.kegg.jp/get/{kegg_id}")
    if not resp:
        return None
    pubchem_id = None
    for ln in resp.text.splitlines():
        if "PubChem:" in ln:
            pubchem_id = ln.split("PubChem:")[1].strip()
            break
    if pubchem_id:
        try:
            cpds = pcp.get_compounds(pubchem_id, 'cid')
            return cpds[0].canonical_smiles if cpds else None
        except Exception as e:
            logger.error(f"PubChem lookup failed for {pubchem_id}: {e}")
    return None

# ----------------------------
# Disease-Compound Mapping (Optimized)
# ----------------------------
def save_chunk(chunk, chunk_num):
    """Save a chunk of associations to database"""
    conn = get_db_connection()
    try:
        conn.executemany(
            "INSERT OR IGNORE INTO DiseaseCompounds(disease_id,compound_id) VALUES(?,?)",
            chunk
        )
        conn.commit()
        logger.info(f"Saved chunk {chunk_num} with {len(chunk)} associations")
        return True
    except Exception as e:
        logger.error(f"Failed to save chunk {chunk_num}: {e}")
        conn.rollback()
        return False
    finally:
        conn.close()

def get_disease_compounds():
    """Get disease-compound associations (optimized for large datasets)"""
    conn = get_db_connection()
    try:
        # Get disease-pathway mappings from database
        logger.info("Getting disease-pathway mappings from database...")
        cursor = conn.execute("""
            SELECT disease_id, pathway_id 
            FROM DiseasePathways
        """)
        
        disease_pathways = {}
        for row in cursor.fetchall():
            disease_id, pathway_id = row[0], row[1]
            if disease_id not in disease_pathways:
                disease_pathways[disease_id] = set()
            disease_pathways[disease_id].add(pathway_id)
        
        logger.info(f"Found {len(disease_pathways)} diseases with pathway mappings")
        
        # Get compound-pathway associations from KEGG
        logger.info("Fetching compound-pathway links from KEGG...")
        cp = request_with_retry("https://rest.kegg.jp/link/compound/pathway")
        
        if not cp:
            logger.error("Failed to fetch compound-pathway data")
            return []
        
        # Process in chunks to manage memory
        chunk_size = 50000
        current_chunk = []
        chunk_num = 0
        total_processed = 0
        
        for ln in cp.text.splitlines():
            parts = ln.split('\t')
            if len(parts) == 2:
                pathway_id, compound_id = parts
                
                # Remove cpd: prefix from compound
                if compound_id.startswith('cpd:'):
                    compound_id = compound_id[4:]
                
                # Convert map to hsa pathway ID
                if pathway_id.startswith('path:map'):
                    map_num = pathway_id[8:]
                    hsa_pathway_id = f"path:hsa{map_num}"
                    
                    # Find diseases for this pathway
                    for disease_id, pathways in disease_pathways.items():
                        if hsa_pathway_id in pathways:
                            current_chunk.append((disease_id, compound_id))
                            
                            # Save chunk when it reaches chunk_size
                            if len(current_chunk) >= chunk_size:
                                chunk_num += 1
                                save_chunk(current_chunk, chunk_num)
                                total_processed += len(current_chunk)
                                current_chunk = []
                                
                                # Progress update
                                if chunk_num % 10 == 0:
                                    logger.info(f"Processed {total_processed} associations...")
        
        # Save any remaining associations
        if current_chunk:
            chunk_num += 1
            save_chunk(current_chunk, chunk_num)
            total_processed += len(current_chunk)
        
        logger.info(f"Total processed: {total_processed} disease-compound links")
        return []  # Return empty list since we're saving directly to DB
        
    except Exception as e:
        logger.error(f"Error getting disease-compounds: {e}")
        return []
    finally:
        conn.close()

# ----------------------------
# Insert Helpers
# ----------------------------
def insert_drug_structures(conn, drug_id, smiles):
    try:
        conn.execute(
            "INSERT OR REPLACE INTO DrugStructures(drug_id,smiles) VALUES(?,?)",
            (drug_id, smiles)
        )
        conn.commit()
        return True
    except Exception as e:
        logger.error(f"Insert drug {drug_id} failed: {e}")
        conn.rollback()
        return False

def insert_compound_structures(conn, comp_id, smiles):
    try:
        conn.execute(
            "INSERT OR REPLACE INTO CompoundStructures(compound_id,smiles) VALUES(?,?)",
            (comp_id, smiles)
        )
        conn.commit()
        return True
    except Exception as e:
        logger.error(f"Insert compound {comp_id} failed: {e}")
        conn.rollback()
        return False

# ----------------------------
# Per-item processors
# ----------------------------
def process_drug(drug_id):
    conn = get_db_connection()
    try:
        mf = get_mol_file(drug_id)
        sm = mol_to_smiles(mf) if mf else None
        if not sm:
            sm = get_smiles_from_pubchem(drug_id)
        if sm:
            insert_drug_structures(conn, drug_id, sm)
    finally:
        conn.close()

def process_compound(comp_id):
    conn = get_db_connection()
    try:
        mf = get_mol_file(comp_id)
        sm = mol_to_smiles(mf) if mf else None
        if not sm:
            sm = get_smiles_from_pubchem(comp_id)
        if sm:
            insert_compound_structures(conn, comp_id, sm)
    finally:
        conn.close()

# ----------------------------
# Table setup
# ----------------------------
def ensure_tables():
    conn = get_db_connection()
    try:
        conn.execute('''
            CREATE TABLE IF NOT EXISTS DrugStructures (
                drug_id TEXT PRIMARY KEY,
                smiles   TEXT NOT NULL
            )
        ''')
        conn.execute('''
            CREATE TABLE IF NOT EXISTS CompoundStructures (
                compound_id TEXT PRIMARY KEY,
                smiles      TEXT NOT NULL
            )
        ''')
        conn.execute('''
            CREATE TABLE IF NOT EXISTS DiseaseCompounds (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                disease_id  TEXT NOT NULL,
                compound_id TEXT NOT NULL,
                UNIQUE(disease_id, compound_id)
            )
        ''')
        
        # Create indexes for faster queries
        conn.execute('CREATE INDEX IF NOT EXISTS idx_disease_compound_disease ON DiseaseCompounds(disease_id)')
        conn.execute('CREATE INDEX IF NOT EXISTS idx_disease_compound_compound ON DiseaseCompounds(compound_id)')
        
        conn.commit()
        logger.info("Tables ensured with indexes")
    except Exception as e:
        logger.error(f"Error creating tables: {e}")
    finally:
        conn.close()

# ----------------------------
# Main Execution
# ----------------------------
if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--entity',
        choices=['drugs','compounds','disease'], required=True)
    parser.add_argument('--start',   type=int, default=0)
    parser.add_argument('--end',     type=int, default=100000)
    parser.add_argument('--workers', type=int, default=5)
    args = parser.parse_args()

    # Apply database optimizations first
    optimize_database()
    ensure_tables()

    if args.entity == 'drugs':
        items = get_kegg_drugs()[args.start:args.end]
        worker_fn = process_drug
        
        logger.info(f"Starting {args.entity} [{len(items)} items] with {args.workers} workers")
        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            futures = [pool.submit(worker_fn, it) for it in items]
            for _ in as_completed(futures):
                pass
                
    elif args.entity == 'compounds':
        items = get_kegg_compounds()[args.start:args.end]
        worker_fn = process_compound
        
        logger.info(f"Starting {args.entity} [{len(items)} items] with {args.workers} workers")
        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            futures = [pool.submit(worker_fn, it) for it in items]
            for _ in as_completed(futures):
                pass
                
    elif args.entity == 'disease':
        # For disease-compound, use the optimized version that saves directly to DB
        logger.info("Starting disease-compound mapping...")
        get_disease_compounds()

    # Export to CSV
    conn = get_db_connection()
    try:
        if args.entity == 'drugs':
            df = pd.read_sql_query("SELECT * FROM DrugStructures", conn)
            df.to_csv('data_files/drugs.csv', index=False)
        elif args.entity == 'compounds':
            df = pd.read_sql_query("SELECT * FROM CompoundStructures", conn)
            df.to_csv('data_files/compounds.csv', index=False)
        else:
            df = pd.read_sql_query("SELECT * FROM DiseaseCompounds", conn)
            df.to_csv('data_files/disease_compounds.csv', index=False)
        logger.info(f"Exported {args.entity} to CSV")
    finally:
        conn.close()

    logger.info("Instance completed.")