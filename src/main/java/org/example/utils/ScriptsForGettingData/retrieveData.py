import requests
import json

def get_gene_details(gene):
    BASE_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/"

    esearch_url = f"{BASE_URL}esearch.fcgi?db=gene&term={gene}[gene]+AND+Homo+sapiens[orgn]&retmode=json"

    response = requests.get(esearch_url).json()

    gene_id = response["esearchresult"]["idlist"][0]

    esummary_url = f"{BASE_URL}esummary.fcgi?db=gene&id={gene_id}&retmode=json"

    summary_response = requests.get(esummary_url).json()

    # Save summary_response to a JSON file``
    with open(f"gene_summary{gene}.json", "w") as json_file:
        json.dump(summary_response, json_file, indent=4)
    print(f"Summary response saved to gene_summary{gene}.json")

if __name__ == "__main__":
    get_gene_details("BSC")



