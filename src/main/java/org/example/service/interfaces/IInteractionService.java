package org.example.service.interfaces;

import org.example.domain.PathwayInteraction;
import org.example.repository.entitiesRepository.TypesOfEntities;
import org.example.service.ServicesException;

import java.util.List;

public interface IInteractionService {
    public List<PathwayInteraction> findPathwayInteractionsBySource(String sourceName) throws ServicesException;
}
