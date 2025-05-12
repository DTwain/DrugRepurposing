package org.example.domain;

import java.util.List;

public class PathwayInteraction extends Entity<Integer> {
    private String sourceName;
    private String targetName;
    private final String relationType;
    private final String sourceType;
    private final String targetType;

    private final List<SubInteraction> subInteractionList;

    public PathwayInteraction(String sourceName, String targetName, String relationType, String sourceType, String targetType, List<SubInteraction> subInteractionList) {
        this.sourceName = sourceName;
        this.targetName = targetName;
        this.relationType = relationType;
        this.sourceType = sourceType;
        this.targetType = targetType;
        this.subInteractionList = subInteractionList;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getRelationType() {
        return relationType;
    }

    public List<SubInteraction> getSubInteractionList() {
        return subInteractionList;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setSourceName(String name) {
        this.sourceName = name;
    }

    public void setTargetName(String name) {
        this.targetName = name;
    }
}
