package org.example.service;

import org.example.domain.*;
import org.example.domain.validation.PathwayComponentsValidator;
import org.example.domain.validation.ValidationException;
import org.example.repository.RepositoryException;
import org.example.repository.interfaces.IGeneDiseaseDrugCompoundRepository;
import org.example.repository.interfaces.IUserRepository;
import org.example.service.interfaces.IGeneDiseaseDrugCompoundService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service layer for gene, disease, drug, and compound operations.
 */
public class GeneDiseaseDrugCompoundService implements IGeneDiseaseDrugCompoundService {
    private final IGeneDiseaseDrugCompoundRepository GeneDiseaseDrugCompoundRepository;

    public GeneDiseaseDrugCompoundService(IGeneDiseaseDrugCompoundRepository repository, IUserRepository userRepository) {
        this.GeneDiseaseDrugCompoundRepository = repository;
    }

    @Override
    public boolean existGene(String geneName) throws ServicesException {
        Gene gene = new Gene(geneName);
        try {
            PathwayComponentsValidator.validateGene(gene);
            return GeneDiseaseDrugCompoundRepository.existGene(gene);
        } catch (ValidationException ve) {
            throw new ServicesException("Gene validation failed", ve);
        } catch (RepositoryException re) {
            throw new ServicesException("Error checking existence of gene: " + geneName, re);
        }
    }

    @Override
    public boolean existDrug(String drugName) throws ServicesException {
        Drug drug = new Drug(drugName);
        try {
            PathwayComponentsValidator.validateDrug(drug);
            return GeneDiseaseDrugCompoundRepository.existDrug(drug);
        } catch (ValidationException ve) {
            throw new ServicesException("Drug validation failed", ve);
        } catch (RepositoryException re) {
            throw new ServicesException("Error checking existence of drug: " + drugName, re);
        }
    }

    @Override
    public boolean existDisease(String diseaseName) throws ServicesException {
        Disease disease = new Disease(diseaseName);
        try {
            PathwayComponentsValidator.validateDisease(disease);
            return GeneDiseaseDrugCompoundRepository.existDisease(disease);
        } catch (ValidationException ve) {
            throw new ServicesException("Disease validation failed", ve);
        } catch (RepositoryException re) {
            throw new ServicesException("Error checking existence of disease: " + diseaseName, re);
        }
    }

    @Override
    public boolean existCompound(String compoundName) throws ServicesException {
        Compound compound = new Compound(compoundName);
        try {
            PathwayComponentsValidator.validateCompound(compound);
            return GeneDiseaseDrugCompoundRepository.existCompound(compound);
        } catch (ValidationException ve) {
            throw new ServicesException("Compound validation failed", ve);
        } catch (RepositoryException re) {
            throw new ServicesException("Error checking existence of compound: " + compoundName, re);
        }
    }

    @Override
    public HashMap<String, String> retrieveInfoAboutGene(String geneName) throws ServicesException {
        Gene gene = new Gene(geneName);
        try {
            PathwayComponentsValidator.validateGene(gene);
            return GeneDiseaseDrugCompoundRepository.retrieveInfoAboutGene(gene);
        } catch (ValidationException ve) {
            throw new ServicesException("Gene validation failed", ve);
        } catch (RepositoryException re) {
            throw new ServicesException("Error retrieving gene info for: " + geneName, re);
        }
    }

    @Override
    public HashMap<String, String> retrieveInfoAboutDisease(String diseaseName) throws ServicesException {
        Disease disease = new Disease(diseaseName);
        try {
            PathwayComponentsValidator.validateDisease(disease);
            return GeneDiseaseDrugCompoundRepository.retrieveInfoAboutDisease(disease);
        } catch (ValidationException ve) {
            throw new ServicesException("Disease validation failed", ve);
        } catch (RepositoryException re) {
            throw new ServicesException("Error retrieving disease info for: " + diseaseName, re);
        }
    }

    @Override
    public HashMap<String, String> retrieveInfoAboutCompound(String compoundName) throws ServicesException {
        Compound compound = new Compound(compoundName);
        try {
            PathwayComponentsValidator.validateCompound(compound);
            return GeneDiseaseDrugCompoundRepository.retrieveInfoAboutCompound(compound);
        } catch (ValidationException ve) {
            throw new ServicesException("Compound validation failed", ve);
        } catch (RepositoryException re) {
            throw new ServicesException("Error retrieving compound info for: " + compoundName, re);
        }
    }

    @Override
    public HashMap<String, String> retrieveInfoAboutDrug(String drugName) throws ServicesException {
        Drug drug = new Drug(drugName);
        try {
            PathwayComponentsValidator.validateDrug(drug);
            return GeneDiseaseDrugCompoundRepository.retrieveInfoAboutDrug(drug);
        } catch (ValidationException ve) {
            throw new ServicesException("Drug validation failed", ve);
        } catch (RepositoryException re) {
            throw new ServicesException("Error retrieving drug info for: " + drugName, re);
        }
    }

    @Override
    public List<String> getGeneNameSuggestion(String genePrefix) throws ServicesException {
        Gene gene = new Gene(genePrefix);
        try {
            return GeneDiseaseDrugCompoundRepository.getGeneNameSuggestion(gene);
        } catch (RepositoryException re) {
            throw new ServicesException("Error fetching gene suggestions for prefix: " + genePrefix, re);
        }
    }

    @Override
    public List<String> getCompoundNameSuggestion(String compoundPrefix) throws ServicesException {
        Compound compound = new Compound(compoundPrefix);
        try {
            return GeneDiseaseDrugCompoundRepository.getCompoundNameSuggestion(compound);
        } catch (RepositoryException re) {
            throw new ServicesException("Error fetching compound suggestions for prefix: " + compoundPrefix, re);
        }
    }

    @Override
    public List<String> getDrugNameSuggestion(String drugPrefix) throws ServicesException {
        Drug drug = new Drug(drugPrefix);
        try {
            return GeneDiseaseDrugCompoundRepository.getDrugNameSuggestion(drug);
        } catch (RepositoryException re) {
            throw new ServicesException("Error fetching drug suggestions for prefix: " + drugPrefix, re);
        }
    }

    /**
     * Get detailed information about a gene.
     *
     * @param geneName the gene name or symbol
     * @return Gene object with detailed information
     * @throws ServicesException if service operations fail
     */
    public Gene getGeneDetails(String geneName) throws ServicesException {
        try {
            // First, check if the gene exists
            if (!existGene(geneName)) {
                throw new ServicesException("Gene not found: " + geneName);
            }

            // Retrieve gene info from KEGG
            HashMap<String, String> geneInfo = retrieveInfoAboutGene(geneName);

            // Parse the KEGG entry to extract information
            Gene gene = new Gene(geneName);

            // Parse KEGG data to populate gene object
            if (geneInfo.containsKey("keggEntry")) {
                String keggData = geneInfo.get("keggEntry");
                gene = parseKeggGeneData(keggData, geneName);
            }

            return gene;
        } catch (ServicesException e) {
            throw e;
        } catch (Exception e) {
            throw new ServicesException("Error retrieving gene details: " + e.getMessage(), e);
        }
    }

    /**
     * Get diseases associated with a gene.
     *
     * @param geneName the gene name or symbol
     * @return List of Disease objects associated with the gene
     * @throws ServicesException if service operations fail
     */
    public List<Disease> getAssociatedDiseases(String geneName) throws ServicesException {
        try {
            // First, check if the gene exists
            if (!existGene(geneName)) {
                throw new ServicesException("Gene not found: " + geneName);
            }

            // Get the canonical gene ID
            String geneId = getGeneCanonicalId(geneName);

            // Query the GeneDiseases table for diseases associated with this gene
            List<String> diseaseIds = getDiseasesForGene(geneId);

            // Create Disease objects with details
            List<Disease> diseases = new ArrayList<>();
            for (String diseaseId : diseaseIds) {
                Disease disease = getDiseaseDetails(diseaseId);
                if (disease != null) {
                    diseases.add(disease);
                }
            }

            return diseases;
        } catch (ServicesException e) {
            throw e;
        } catch (Exception e) {
            throw new ServicesException("Error retrieving associated diseases: " + e.getMessage(), e);
        }
    }

    /**
     * Get the canonical ID for a gene from its name or symbol.
     *
     * @param geneName the gene name or symbol
     * @return the canonical gene ID
     * @throws ServicesException if service operations fail
     */
    private String getGeneCanonicalId(String geneName) throws ServicesException {
        try {
            Gene gene = new Gene(geneName);
            HashMap<String, String> geneInfo = retrieveInfoAboutGene(geneName);
            return geneInfo.getOrDefault("id", "");
        } catch (Exception e) {
            throw new ServicesException("Error retrieving gene ID: " + e.getMessage(), e);
        }
    }

    /**
     * Get diseases associated with a gene by ID.
     *
     * @param geneId the canonical gene ID
     * @return List of disease IDs
     * @throws ServicesException if service operations fail
     */
    private List<String> getDiseasesForGene(String geneId) throws ServicesException {
        try {
            // This would normally query the GeneDiseases table
            // For demo purposes, we'll use the repository directly
            return GeneDiseaseDrugCompoundRepository.getDiseasesForGene(geneId);
        } catch (RepositoryException e) {
            throw new ServicesException("Error retrieving diseases for gene: " + e.getMessage(), e);
        }
    }

    /**
     * Get detailed information about a disease.
     *
     * @param diseaseId the disease ID
     * @return Disease object with detailed information
     * @throws ServicesException if service operations fail
     */
    private Disease getDiseaseDetails(String diseaseId) throws ServicesException {
        try {
            // Create a placeholder disease name
            String diseaseName = getDiseaseNameFromId(diseaseId);
            Disease disease = new Disease(diseaseId, diseaseName);

            // Retrieve disease info from KEGG
            HashMap<String, String> diseaseInfo = retrieveInfoAboutDisease(diseaseName);

            // Parse KEGG data to populate disease object
            if (diseaseInfo.containsKey("keggEntry")) {
                String keggData = diseaseInfo.get("keggEntry");
                disease = parseKeggDiseaseData(keggData, diseaseId, diseaseName);
            } else {
                // Set some default description if we can't get KEGG data
                disease.setDescription("A disease associated with this gene. For more information, consult KEGG database.");
            }

            return disease;
        } catch (Exception e) {
            throw new ServicesException("Error retrieving disease details: " + e.getMessage(), e);
        }
    }

    /**
     * Get a disease name from its ID using the disease aliases table.
     *
     * @param diseaseId the disease ID
     * @return the disease name
     */
    private String getDiseaseNameFromId(String diseaseId) {
        try {
            // This would normally query the DiseaseAliases table
            // For demo purposes, we'll use direct access
            return GeneDiseaseDrugCompoundRepository.getDiseaseNameFromId(diseaseId);
        } catch (Exception e) {
            // If we can't find a name, use the ID as a fallback
            return diseaseId;
        }
    }

    /**
     * Parse KEGG gene data to extract detailed information.
     *
     * @param keggData the raw KEGG data
     * @param geneName the gene name/symbol
     * @return Gene object with populated fields
     */
    private Gene parseKeggGeneData(String keggData, String geneName) {
        Gene gene = new Gene(geneName);

        // Extract gene symbol
        Pattern symbolPattern = Pattern.compile("SYMBOL\\s+(\\S+)");
        Matcher symbolMatcher = symbolPattern.matcher(keggData);
        if (symbolMatcher.find()) {
            gene.setSymbol(symbolMatcher.group(1));
        } else {
            gene.setSymbol(geneName);
        }

        // Extract gene name/description
        Pattern namePattern = Pattern.compile("NAME\\s+(.+)\\n");
        Matcher nameMatcher = namePattern.matcher(keggData);
        if (nameMatcher.find()) {
            gene.setName(nameMatcher.group(1).trim());
        } else {
            gene.setName(geneName);
        }

        // Extract chromosome location
        Pattern chromPattern = Pattern.compile("CHROMOSOME\\s+(.+)\\n");
        Matcher chromMatcher = chromPattern.matcher(keggData);
        if (chromMatcher.find()) {
            try {
                gene.setChromosome(Integer.parseInt(chromMatcher.group(1).trim()));
            } catch (NumberFormatException e) {
                // If it's not a simple number, just store it as description data
                gene.setDescription("Chromosome: " + chromMatcher.group(1).trim());
            }
        }

        // Extract gene function/description
        Pattern descPattern = Pattern.compile("DESCRIPTION\\s+(.+?)\\n\\w+", Pattern.DOTALL);
        Matcher descMatcher = descPattern.matcher(keggData);
        if (descMatcher.find()) {
            gene.setDescription(descMatcher.group(1).trim());
        } else {
            gene.setDescription("A gene involved in biological pathways. For more information, consult KEGG database.");
        }

        return gene;
    }

    /**
     * Parse KEGG disease data to extract detailed information.
     *
     * @param keggData the raw KEGG data
     * @param diseaseId the disease ID
     * @param diseaseName the disease name
     * @return Disease object with populated fields
     */
    private Disease parseKeggDiseaseData(String keggData, String diseaseId, String diseaseName) {
        Disease disease = new Disease(diseaseId, diseaseName);

        // Set the name
        disease.setName(diseaseName);

        // Extract disease description
        Pattern descPattern = Pattern.compile("DESCRIPTION\\s+(.+?)\\n\\w+", Pattern.DOTALL);
        Matcher descMatcher = descPattern.matcher(keggData);
        if (descMatcher.find()) {
            disease.setDescription(descMatcher.group(1).trim());
        } else {
            // Try another pattern
            Pattern altPattern = Pattern.compile("DESCRIPTION\\s+(.+)", Pattern.DOTALL);
            Matcher altMatcher = altPattern.matcher(keggData);
            if (altMatcher.find()) {
                disease.setDescription(altMatcher.group(1).trim());
            } else {
                disease.setDescription("A disease associated with this gene. For more information, consult KEGG database.");
            }
        }

        // Extract KEGG ID
        disease.setKeggId(diseaseId);

        return disease;
    }

}
