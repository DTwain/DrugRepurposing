package org.example.service.interfaces;

import org.example.domain.*;
import org.example.service.ServicesException;

import java.util.HashMap;
import java.util.List;

public interface IGeneDiseaseDrugCompoundService {
    // Existing methods
    public boolean existGene(String geneName) throws ServicesException;
    public boolean existDrug(String drugName) throws ServicesException;
    public boolean existDisease(String diseaseName) throws ServicesException;
    public boolean existCompound(String compoundName) throws ServicesException;

    public HashMap<String, String> retrieveInfoAboutGene(String geneName) throws ServicesException;
    public HashMap<String, String> retrieveInfoAboutDisease(String diseaseName) throws ServicesException;
    public HashMap<String, String> retrieveInfoAboutCompound(String compoundName) throws ServicesException;
    public HashMap<String, String> retrieveInfoAboutDrug(String drugName) throws ServicesException;

    public List<String> getGeneNameSuggestion(String genePrefix) throws ServicesException;
    public List<String> getCompoundNameSuggestion(String compoundPrefix) throws ServicesException;
    public List<String> getDrugNameSuggestion(String drugPrefix) throws ServicesException;

    // New methods for drug repurposing application
    /**
     * Get detailed information about a gene.
     *
     * @param geneName the gene name or symbol
     * @return Gene object with detailed information
     * @throws ServicesException if service operations fail
     */
    public Gene getGeneDetails(String geneName) throws ServicesException;

    /**
     * Get diseases associated with a gene.
     *
     * @param geneName the gene name or symbol
     * @return List of Disease objects associated with the gene
     * @throws ServicesException if service operations fail
     */
    public List<Disease> getAssociatedDiseases(String geneName) throws ServicesException;
}