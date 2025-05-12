package org.example.domain;

/**
 * Represents a drug repurposing candidate result with success probability
 * and related information.
 */
public class DrugRepurposingResult {
    private String drugId;
    private String drugName;
    private double successRate;
    private String mechanismOfAction;
    private String currentIndication;
    private String evidenceLevel;
    private String evidenceDetails;
    private String adverseEffects;
    private String dosageInformation;

    public DrugRepurposingResult() {
    }

    public DrugRepurposingResult(String drugId, String drugName, double successRate) {
        this.drugId = drugId;
        this.drugName = drugName;
        this.successRate = successRate;
    }

    public DrugRepurposingResult(String drugId, String drugName, double successRate,
                                 String mechanismOfAction, String currentIndication,
                                 String evidenceLevel) {
        this.drugId = drugId;
        this.drugName = drugName;
        this.successRate = successRate;
        this.mechanismOfAction = mechanismOfAction;
        this.currentIndication = currentIndication;
        this.evidenceLevel = evidenceLevel;
    }

    // Full constructor
    public DrugRepurposingResult(String drugId, String drugName, double successRate,
                                 String mechanismOfAction, String currentIndication,
                                 String evidenceLevel, String evidenceDetails,
                                 String adverseEffects, String dosageInformation) {
        this.drugId = drugId;
        this.drugName = drugName;
        this.successRate = successRate;
        this.mechanismOfAction = mechanismOfAction;
        this.currentIndication = currentIndication;
        this.evidenceLevel = evidenceLevel;
        this.evidenceDetails = evidenceDetails;
        this.adverseEffects = adverseEffects;
        this.dosageInformation = dosageInformation;
    }

    // Getters and setters
    public String getDrugId() {
        return drugId;
    }

    public void setDrugId(String drugId) {
        this.drugId = drugId;
    }

    public String getDrugName() {
        return drugName;
    }

    public void setDrugName(String drugName) {
        this.drugName = drugName;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public String getMechanismOfAction() {
        return mechanismOfAction;
    }

    public void setMechanismOfAction(String mechanismOfAction) {
        this.mechanismOfAction = mechanismOfAction;
    }

    public String getCurrentIndication() {
        return currentIndication;
    }

    public void setCurrentIndication(String currentIndication) {
        this.currentIndication = currentIndication;
    }

    public String getEvidenceLevel() {
        return evidenceLevel;
    }

    public void setEvidenceLevel(String evidenceLevel) {
        this.evidenceLevel = evidenceLevel;
    }

    public String getEvidenceDetails() {
        return evidenceDetails;
    }

    public void setEvidenceDetails(String evidenceDetails) {
        this.evidenceDetails = evidenceDetails;
    }

    public String getAdverseEffects() {
        return adverseEffects;
    }

    public void setAdverseEffects(String adverseEffects) {
        this.adverseEffects = adverseEffects;
    }

    public String getDosageInformation() {
        return dosageInformation;
    }

    public void setDosageInformation(String dosageInformation) {
        this.dosageInformation = dosageInformation;
    }
}