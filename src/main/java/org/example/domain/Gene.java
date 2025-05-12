package org.example.domain;

import java.util.List;

public class Gene extends Entity<Long>{
    private String geneName;
    private String name;
    private String symbol;
    private String description;
    private String summary;
    private Integer Chromosome;
    private List<String> otherAliases;

    public Gene(String geneName) {
        this.geneName = geneName;
    }

    public Gene(Long id, String description, String summary) {
        setId(id);
        this.description = description;
        this.summary = summary;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getChromosome() {
        return Chromosome;
    }

    public void setChromosome(Integer chromosome) {
        Chromosome = chromosome;
    }

    public List<String> getOtherAliases() {
        return otherAliases;
    }

    public void setOtherAliases(List<String> otherAliases) {
        this.otherAliases = otherAliases;
    }

    public String getGeneName() {
        return geneName;
    }

    public void setGeneName(String geneName) {
        this.geneName = geneName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
}