package org.example.service;

import org.example.service.interfaces.IDrugRepurposingService;
import org.example.service.interfaces.IGeneDiseaseDrugCompoundService;
import org.example.service.interfaces.IInteractionService;
import org.example.service.interfaces.IUserService;

public class AllServices {
    private final IUserService userService;
    private final IGeneDiseaseDrugCompoundService geneDiseaseDrugCompoundService;
    private final IInteractionService interactionService;
    private final IDrugRepurposingService drugRepurposingService;

    public AllServices(IUserService userService,
                       IGeneDiseaseDrugCompoundService geneDiseaseDrugCompoundService,
                       IInteractionService interactionService,
                       IDrugRepurposingService drugRepurposingService) {
        this.userService = userService;
        this.geneDiseaseDrugCompoundService = geneDiseaseDrugCompoundService;
        this.interactionService = interactionService;
        this.drugRepurposingService = drugRepurposingService;
    }

    public IUserService getUserService() {
        return userService;
    }

    public IGeneDiseaseDrugCompoundService getGeneDiseaseDrugCompoundService() {
        return geneDiseaseDrugCompoundService;
    }

    public IInteractionService getInteractionService() {
        return interactionService;
    }

    public IDrugRepurposingService getDrugRepurposingService() {
        return drugRepurposingService;
    }
}