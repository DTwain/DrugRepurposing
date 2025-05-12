package org.example.repository.interfaces;

import org.example.domain.*;
import org.example.repository.RepositoryException;
import org.example.repository.entitiesRepository.TypesOfEntities;

import java.util.List;

public interface IInteractionRepository {
    public List<PathwayInteraction> findPathwayInteractionsBySource(String sourceName, TypesOfEntities typesOfEntities) throws RepositoryException;

    public List<Gene> findGenesById(String geneId) throws RepositoryException;
    public List<Drug> findDrugsById(String drugId) throws RepositoryException;
    public List<Disease> findDiseasesById(String diseaseId) throws RepositoryException;
    public List<Compound> findCompoundsById(String compoundId) throws RepositoryException;
}
