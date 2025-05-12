package org.example.service;

import org.example.domain.*;
import org.example.repository.entitiesRepository.TypesOfEntities;
import org.example.repository.interfaces.IGeneDiseaseDrugCompoundRepository;
import org.example.repository.interfaces.IInteractionRepository;
import org.example.service.interfaces.IInteractionService;

import java.util.List;

public class InteractionService implements IInteractionService {
    private final IGeneDiseaseDrugCompoundRepository iGeneDiseaseDrugCompoundRepository;
    private final IInteractionRepository interactionRepository;

    public InteractionService(IGeneDiseaseDrugCompoundRepository iGeneDiseaseDrugCompoundRepository, IInteractionRepository interactionRepository) {
        this.iGeneDiseaseDrugCompoundRepository = iGeneDiseaseDrugCompoundRepository;
        this.interactionRepository = interactionRepository;
    }

    @Override
    public List<PathwayInteraction> findPathwayInteractionsBySource(String sourceName) throws ServicesException {
        if(iGeneDiseaseDrugCompoundRepository.existGene(new Gene(sourceName)))
            return interactionRepository.findPathwayInteractionsBySource(sourceName, TypesOfEntities.GENE);

        else if(iGeneDiseaseDrugCompoundRepository.existDrug(new Drug(sourceName)))
            return interactionRepository.findPathwayInteractionsBySource(sourceName, TypesOfEntities.DRUG);

        else if(iGeneDiseaseDrugCompoundRepository.existDisease(new Disease(sourceName)))
            return interactionRepository.findPathwayInteractionsBySource(sourceName, TypesOfEntities.DISEASE);

        else
            return interactionRepository.findPathwayInteractionsBySource(sourceName, TypesOfEntities.COMPOUND);
    }
}
