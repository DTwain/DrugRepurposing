package org.example.controller;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.domain.Disease;
import org.example.domain.DrugRepurposingResult;
import org.example.domain.Gene;
import org.example.domain.entitiesAssociatedWithUser.User;
import org.example.service.AllServices;
import org.example.service.ServicesException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller for the main full-screen view. Shows dashboard by default,
 * and displays gene information, associated diseases, and drug repurposing options upon search.
 */
public class FullScreenController {
    private AllServices services;
    private Optional<User> currentUser = Optional.empty();

    // Current selected data
    private Gene currentGene;
    private Disease selectedDisease;

    // FXML-injected UI elements
    @FXML private BorderPane rootPane;
    @FXML private HBox searchContainer;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private ImageView userIcon;

    @FXML private ScrollPane dashboardPane;
    @FXML private ScrollPane geneInfoPane;
    @FXML private AnchorPane geneDetailsPane;
    @FXML private VBox diseaseListContainer;
    @FXML private VBox drugRepurposingContainer;
    @FXML private Label geneNameLabel;
    @FXML private Label geneDescriptionLabel;
    @FXML private Label diseaseHeaderLabel;

    @FXML private AnchorPane userMenuPopup;
    @FXML private VBox userMenuContent;
    @FXML private Button myAccountButton;
    @FXML private Button geneSearchesButton;
    @FXML private Button settingsButton;
    @FXML private Button loginButton;
    @FXML private Button signUpButton;
    @FXML private Button logOutButton;

    // Suggestion dropdown for gene search
    private final ObservableList<String> suggestions = javafx.collections.FXCollections.observableArrayList();
    private final ContextMenu suggestionsMenu = new ContextMenu();

    // Status tracking
    private AtomicBoolean isDataLoading = new AtomicBoolean(false);
    private Map<String, List<DrugRepurposingResult>> drugResultsCache = new HashMap<>();

    /**
     * Set shared services (called by application startup).
     */
    public void setServices(AllServices services) {
        this.services = services;
    }

    /**
     * Set the current user and update menu visibility.
     */
    public void setCurrentUser(Optional<User> user) {
        this.currentUser = user;
        updateUserMenuVisibility();
    }

    private void updateUserMenuVisibility() {
        boolean isLoggedIn = currentUser.isPresent();
        myAccountButton.setVisible(isLoggedIn);
        myAccountButton.setManaged(isLoggedIn);
        geneSearchesButton.setVisible(isLoggedIn);
        geneSearchesButton.setManaged(isLoggedIn);
        settingsButton.setVisible(isLoggedIn);
        settingsButton.setManaged(isLoggedIn);
        logOutButton.setVisible(isLoggedIn);
        logOutButton.setManaged(isLoggedIn);

        loginButton.setVisible(!isLoggedIn);
        loginButton.setManaged(!isLoggedIn);
        signUpButton.setVisible(!isLoggedIn);
        signUpButton.setManaged(!isLoggedIn);
    }

    @FXML
    public void initialize() {
        if (searchField == null) {
            throw new IllegalStateException("searchField was not injected—check your FXML fx:id!");
        }

        updateUserMenuVisibility();

        // Dismiss user menu when clicking outside
        rootPane.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (!userMenuPopup.isVisible()) return;
            Node target = (Node) event.getTarget();
            boolean inPopup = false, onIcon = false;
            for (Node n = target; n != null; n = n.getParent()) {
                if (n == userMenuPopup) inPopup = true;
                if (n == userIcon) onIcon = true;
            }
            if (!inPopup && !onIcon) {
                userMenuPopup.setVisible(false);
                userMenuPopup.setManaged(false);
            }
        });

        // Suggestion menu width
        suggestionsMenu.prefWidthProperty().bind(searchContainer.widthProperty());

        // Autocomplete listener
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.isEmpty()) {
                Platform.runLater(() -> {
                    List<String> raw = fetchGeneSuggestions(newV);
                    List<String> matches = raw.stream()
                            .filter(s -> s.toLowerCase().startsWith(newV.toLowerCase()))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList();
                    if (!matches.isEmpty()) {
                        populateSuggestions(matches);
                        if (!suggestionsMenu.isShowing()) {
                            suggestionsMenu.show(searchContainer, Side.BOTTOM, 0, 0);
                        }
                    } else {
                        suggestionsMenu.hide();
                    }
                });
            } else {
                suggestionsMenu.hide();
            }
        });
        searchField.focusedProperty().addListener((obs, wasF, isF) -> {
            if (!isF) suggestionsMenu.hide();
        });

        // Setup event handlers
        setupEventHandlers();

        // Initialize UI elements if they weren't defined in FXML
        initializeUIComponents();
    }

    /**
     * Initialize UI components that might not be in FXML
     */
    private void initializeUIComponents() {
        // Create gene info pane if not available
        if (geneInfoPane == null) {
            geneInfoPane = new ScrollPane();
            geneInfoPane.setFitToWidth(true);
            geneInfoPane.setVisible(false);
            geneInfoPane.setManaged(false);
            rootPane.setCenter(geneInfoPane);
        }

        // Create gene details pane if not available
        if (geneDetailsPane == null) {
            geneDetailsPane = new AnchorPane();
            geneInfoPane.setContent(geneDetailsPane);

            VBox contentBox = new VBox(20);
            contentBox.setPrefWidth(800);
            contentBox.setMaxWidth(1200);
            contentBox.setPrefHeight(600);
            contentBox.setStyle("-fx-padding: 20;");
            AnchorPane.setTopAnchor(contentBox, 0.0);
            AnchorPane.setLeftAnchor(contentBox, 0.0);
            AnchorPane.setRightAnchor(contentBox, 0.0);
            AnchorPane.setBottomAnchor(contentBox, 0.0);

            geneDetailsPane.getChildren().add(contentBox);

            // Gene info section
            VBox geneInfoBox = new VBox(10);
            geneInfoBox.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-background-radius: 5;");

            geneNameLabel = new Label();
            geneNameLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

            geneDescriptionLabel = new Label();
            geneDescriptionLabel.setWrapText(true);
            geneDescriptionLabel.setStyle("-fx-font-size: 14px;");

            geneInfoBox.getChildren().addAll(geneNameLabel, geneDescriptionLabel);

            // Disease section
            VBox diseasesBox = new VBox(10);
            diseasesBox.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-background-radius: 5;");

            diseaseHeaderLabel = new Label("Associated Diseases");
            diseaseHeaderLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

            diseaseListContainer = new VBox(10);
            diseasesBox.getChildren().addAll(diseaseHeaderLabel, diseaseListContainer);

            // Drug repurposing section
            VBox drugBox = new VBox(10);
            drugBox.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 15; -fx-background-radius: 5;");

            Label drugHeaderLabel = new Label("Drug Repurposing Options");
            drugHeaderLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

            drugRepurposingContainer = new VBox(10);
            drugBox.getChildren().addAll(drugHeaderLabel, drugRepurposingContainer);

            contentBox.getChildren().addAll(geneInfoBox, diseasesBox, drugBox);
        }
    }

    /**
     * Method to initialize the controller with event handlers
     */
    private void setupEventHandlers() {
        // Set up search button handler
        searchButton.setOnAction(this::handleSearchButtonClick);

        // Make pressing Enter in search field trigger search
        searchField.setOnAction(event -> {
            String query = searchField.getText().trim();
            searchGene(query);
        });
    }

    /**
     * Handle search button click
     */
    @FXML
    public void handleSearchButtonClick(ActionEvent event) {
        String query = searchField.getText().trim();
        searchGene(query);
    }

    /**
     * Handle clicks on gene buttons in the dashboard.
     */
    @FXML
    public void handleGeneButtonClick(ActionEvent event) {
        // Get the button that was clicked
        Button clickedButton = (Button) event.getSource();

        // Get the gene name from the button text
        String geneName = clickedButton.getText();

        // Set the search field to the gene name
        searchField.setText(geneName);

        // Search for the gene
        searchGene(geneName);
    }

    private void populateSuggestions(List<String> list) {
        suggestionsMenu.getItems().clear();
        for (String gene : list) {
            Label lbl = new Label(gene);
            CustomMenuItem item = new CustomMenuItem(lbl, true);
            item.getStyleClass().add("suggestion-item");
            item.setOnAction(evt -> {
                searchField.setText(gene);
                searchGene(gene);
                suggestionsMenu.hide();
            });
            suggestionsMenu.getItems().add(item);
        }
    }

    private List<String> fetchGeneSuggestions(String prefix) {
        if (services == null) {
            var mock = new ArrayList<String>();
            if (prefix.toLowerCase().startsWith("b")) {
                mock.add("BRCA1"); mock.add("BRCA2"); mock.add("BRAF");
            } else if (prefix.toLowerCase().startsWith("t")) {
                mock.add("TP53"); mock.add("TNF"); mock.add("TERT");
            } else if (prefix.toLowerCase().startsWith("p")) {
                mock.add("PRKACA"); mock.add("PIK3CA"); mock.add("PTEN");
            } else {
                mock.add(prefix + "_GENE1"); mock.add(prefix + "_GENE2");
            }
            return mock;
        }
        try {
            return services.getGeneDiseaseDrugCompoundService().getGeneNameSuggestion(prefix);
        } catch (ServicesException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     * Search for gene information and associated diseases
     */
    private void searchGene(String geneName) {
        if (geneName == null || geneName.isBlank()) {
            showDashboard();
            return;
        }

        try {
            isDataLoading.set(true);

            // Reset current selections
            currentGene = null;
            selectedDisease = null;

            // Fetch gene info from KEGG database
            Gene gene = services.getGeneDiseaseDrugCompoundService().getGeneDetails(geneName);
            currentGene = gene;

            // Update UI with gene info
            updateGeneInfo(gene);

            // Fetch associated diseases
            List<Disease> diseases = services.getGeneDiseaseDrugCompoundService().getAssociatedDiseases(geneName);

            // Update disease list
            updateDiseaseList(diseases);

            // Clear drug repurposing container since no disease is selected yet
            drugRepurposingContainer.getChildren().clear();

            // Show gene info pane
            showGeneInfoPane();

            // Save search history if user is logged in
            if (currentUser.isPresent()) {
                services.getUserService().saveGeneSearch(currentUser.get().getId(), geneName);
            }

        } catch (ServicesException ex) {
            ex.printStackTrace();

            // Show error dialog
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Search Error");
            alert.setHeaderText("Error searching for " + geneName);
            alert.setContentText("An error occurred: " + ex.getMessage());
            alert.showAndWait();

            showDashboard();
        } finally {
            isDataLoading.set(false);
        }
    }

    /**
     * Update the UI with gene information
     */
    private void updateGeneInfo(Gene gene) {
        geneNameLabel.setText(gene.getName() + " (" + gene.getSymbol() + ")");
        geneDescriptionLabel.setText(gene.getDescription());
    }

    /**
     * Update the list of diseases associated with the gene
     */
    private void updateDiseaseList(List<Disease> diseases) {
        diseaseListContainer.getChildren().clear();

        if (diseases.isEmpty()) {
            Label noDiseasesLabel = new Label("No associated diseases found for this gene.");
            noDiseasesLabel.setStyle("-fx-font-style: italic;");
            diseaseListContainer.getChildren().add(noDiseasesLabel);
            return;
        }

        for (Disease disease : diseases) {
            // Create a disease card
            VBox diseaseCard = createDiseaseCard(disease);
            diseaseListContainer.getChildren().add(diseaseCard);
        }
    }

    /**
     * Create a UI card for a disease
     */
    private VBox createDiseaseCard(Disease disease) {
        VBox card = new VBox(5);
        card.setStyle("-fx-background-color: white; -fx-padding: 10; -fx-border-color: #dddddd; " +
                "-fx-border-width: 1; -fx-border-radius: 5; -fx-cursor: hand;");

        // Disease name
        Label nameLabel = new Label(disease.getName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        // Disease ID (KEGG ID)
        Label idLabel = new Label("KEGG ID: " + disease.getKeggId());
        idLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666;");

        // Brief description (first 100 chars)
        String briefDesc = disease.getDescription();
        if (briefDesc != null && briefDesc.length() > 100) {
            briefDesc = briefDesc.substring(0, 97) + "...";
        } else if (briefDesc == null) {
            briefDesc = "No description available";
        }
        Label descLabel = new Label(briefDesc);
        descLabel.setWrapText(true);

        card.getChildren().addAll(nameLabel, idLabel, descLabel);

        // Add click handler
        card.setOnMouseClicked(event -> onDiseaseSelected(disease));

        return card;
    }

    /**
     * Handle disease selection with improved performance
     */
    private void onDiseaseSelected(Disease disease) {
        // Set selected disease early to prevent duplicate processing
        if (selectedDisease != null && selectedDisease.getKeggId().equals(disease.getKeggId())) {
            // Same disease already selected, just scroll to drug section
            scrollToDrugSection();
            return;
        }

        selectedDisease = disease;

        // Show loading indicator first to provide immediate feedback
        drugRepurposingContainer.getChildren().clear();
        ProgressIndicator loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        HBox loadingBox = new HBox(loadingIndicator, new Label(" Loading drug data..."));
        loadingBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        loadingBox.setPadding(new javafx.geometry.Insets(10));
        drugRepurposingContainer.getChildren().add(loadingBox);

        // Immediately display disease info while drug data loads
        VBox diseaseInfoBox = createDiseaseInfoBox(disease);
        drugRepurposingContainer.getChildren().add(0, diseaseInfoBox);

        // Add separator
        Separator separator = new Separator();
        separator.setStyle("-fx-padding: 10 0;");
        drugRepurposingContainer.getChildren().add(1, separator);

        // Start scrolling to drug section early
        scrollToDrugSection();

        // Load drug repurposing data in background thread
        javafx.concurrent.Task<List<DrugRepurposingResult>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<DrugRepurposingResult> call() throws Exception {
                try {
                    // Use page size to limit initial results for faster loading
                    return services.getDrugRepurposingService().getDrugRepurposingForDiseasePaginated(
                            disease.getKeggId(), 0, 5);
                } catch (ServicesException ex) {
                    // Propagate exception to be handled in onFailed
                    throw ex;
                }
            }
        };

        // Handle success - update UI with results
        task.setOnSucceeded(event -> {
            List<DrugRepurposingResult> drugResults = task.getValue();

            // Remove loading indicator (but keep disease info)
            drugRepurposingContainer.getChildren().removeIf(node -> node == loadingBox);

            if (drugResults.isEmpty()) {
                Label noResultsLabel = new Label("No drug repurposing options found for this disease.");
                noResultsLabel.setStyle("-fx-font-style: italic;");
                drugRepurposingContainer.getChildren().add(noResultsLabel);
            } else {
                // Sort drugs by success rate (highest first)
                drugResults.sort(Comparator.comparing(DrugRepurposingResult::getSuccessRate).reversed());

                // Add each drug with lazy loading for details
                for (DrugRepurposingResult drug : drugResults) {
                    VBox drugCard = createDrugCard(drug);
                    drugRepurposingContainer.getChildren().add(drugCard);
                }

                // Add "Load More" button if applicable
                if (drugResults.size() == 5) {
                    Button loadMoreButton = new Button("Load More Results");
                    loadMoreButton.setStyle("-fx-background-color: #5a67d8; -fx-text-fill: white;");
                    loadMoreButton.setOnAction(e -> loadMoreDrugs(disease.getKeggId(), 5));
                    drugRepurposingContainer.getChildren().add(loadMoreButton);
                }
            }

            // Cache the results for future use
            drugResultsCache.put(disease.getKeggId(), drugResults);
        });

        // Handle failure - show error message
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            ex.printStackTrace();

            // Remove loading indicator but keep disease info
            drugRepurposingContainer.getChildren().removeIf(node -> node == loadingBox);

            // Add error message
            Label errorLabel = new Label("Error loading drug repurposing data: " +
                    (ex.getMessage() != null ? ex.getMessage() : "Unknown error"));
            errorLabel.setStyle("-fx-text-fill: red;");
            drugRepurposingContainer.getChildren().add(errorLabel);

            // Add retry button
            Button retryButton = new Button("Retry");
            retryButton.setStyle("-fx-background-color: #5a67d8; -fx-text-fill: white;");
            retryButton.setOnAction(e -> onDiseaseSelected(disease));
            drugRepurposingContainer.getChildren().add(retryButton);
        });

        // Start background task
        new Thread(task).start();
    }

    /**
     * Helper method to create disease info box
     */
    private VBox createDiseaseInfoBox(Disease disease) {
        VBox diseaseInfoBox = new VBox(5);
        diseaseInfoBox.setStyle("-fx-background-color: #e6f2ff; -fx-padding: 10; -fx-border-radius: 5;");

        Label diseaseHeaderLabel = new Label("Selected Disease: " + disease.getName());
        diseaseHeaderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        Label diseaseDescLabel = new Label(disease.getDescription() != null ?
                disease.getDescription() : "No description available");
        diseaseDescLabel.setWrapText(true);

        diseaseInfoBox.getChildren().addAll(diseaseHeaderLabel, diseaseDescLabel);
        return diseaseInfoBox;
    }

    /**
     * Helper method to scroll to drug section
     */
    private void scrollToDrugSection() {
        Platform.runLater(() -> {
            if (geneInfoPane != null) {
                // Smoother scrolling with animation
                javafx.animation.AnimationTimer timer = new javafx.animation.AnimationTimer() {
                    private long startTime = 0;
                    private double startValue = 0;
                    private double targetValue = 0.7; // Approximation to show drug section

                    @Override
                    public void start() {
                        startTime = System.nanoTime();
                        startValue = geneInfoPane.getVvalue();
                        super.start();
                    }

                    @Override
                    public void handle(long now) {
                        double elapsed = (now - startTime) / 1_000_000_000.0;
                        if (elapsed > 0.5) { // 500ms animation
                            geneInfoPane.setVvalue(targetValue);
                            stop();
                        } else {
                            double fraction = elapsed / 0.5;
                            double newValue = startValue + (targetValue - startValue) * fraction;
                            geneInfoPane.setVvalue(newValue);
                        }
                    }
                };
                timer.start();
            }
        });
    }

    /**
     * Load more drugs when "Load More" button is clicked
     */
    private void loadMoreDrugs(String diseaseId, int offset) {
        // Remove the "Load More" button if it exists
        drugRepurposingContainer.getChildren().removeIf(node ->
                node instanceof Button && ((Button) node).getText().equals("Load More Results"));

        // Add loading indicator
        ProgressIndicator loadingMore = new ProgressIndicator();
        loadingMore.setMaxSize(24, 24);
        HBox loadingBox = new HBox(loadingMore, new Label(" Loading more..."));
        loadingBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        loadingBox.setPadding(new javafx.geometry.Insets(5));
        drugRepurposingContainer.getChildren().add(loadingBox);

        // Load additional results in background
        javafx.concurrent.Task<List<DrugRepurposingResult>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<DrugRepurposingResult> call() throws Exception {
                return services.getDrugRepurposingService().getDrugRepurposingForDiseasePaginated(
                        diseaseId, offset, 5);
            }
        };

        task.setOnSucceeded(event -> {
            List<DrugRepurposingResult> moreResults = task.getValue();

            // Remove loading indicator
            drugRepurposingContainer.getChildren().remove(loadingBox);

            // Sort by success rate
            moreResults.sort(Comparator.comparing(DrugRepurposingResult::getSuccessRate).reversed());

            // Add each additional drug
            for (DrugRepurposingResult drug : moreResults) {
                VBox drugCard = createDrugCard(drug);
                drugRepurposingContainer.getChildren().add(drugCard);
            }

            // Add "Load More" button if there are more results
            if (moreResults.size() == 5) {
                Button loadMoreButton = new Button("Load More Results");
                loadMoreButton.setStyle("-fx-background-color: #5a67d8; -fx-text-fill: white;");
                loadMoreButton.setOnAction(e -> loadMoreDrugs(diseaseId, offset + 5));
                drugRepurposingContainer.getChildren().add(loadMoreButton);
            }

            // Update cache with new results
            List<DrugRepurposingResult> cachedResults = drugResultsCache.getOrDefault(diseaseId, new ArrayList<>());
            cachedResults.addAll(moreResults);
            drugResultsCache.put(diseaseId, cachedResults);
        });

        task.setOnFailed(event -> {
            // Remove loading indicator
            drugRepurposingContainer.getChildren().remove(loadingBox);

            // Show error
            Label errorLabel = new Label("Error loading additional results");
            errorLabel.setStyle("-fx-text-fill: red;");
            drugRepurposingContainer.getChildren().add(errorLabel);

            // Add "Try Again" button
            Button tryAgainButton = new Button("Try Again");
            tryAgainButton.setStyle("-fx-background-color: #5a67d8; -fx-text-fill: white;");
            tryAgainButton.setOnAction(e -> loadMoreDrugs(diseaseId, offset));
            drugRepurposingContainer.getChildren().add(tryAgainButton);
        });

        new Thread(task).start();
    }


    /**
     * Update the list of drug repurposing options
     */
    private void updateDrugRepurposingList(List<DrugRepurposingResult> drugs) {
        drugRepurposingContainer.getChildren().clear();

        if (drugs.isEmpty()) {
            Label noResultsLabel = new Label("No drug repurposing options found for this disease.");
            noResultsLabel.setStyle("-fx-font-style: italic;");
            drugRepurposingContainer.getChildren().add(noResultsLabel);
            return;
        }

        // Add disease info at the top of drug repurposing section
        VBox diseaseInfoBox = new VBox(5);
        diseaseInfoBox.setStyle("-fx-background-color: #e6f2ff; -fx-padding: 10; -fx-border-radius: 5;");

        Label diseaseHeaderLabel = new Label("Selected Disease: " + selectedDisease.getName());
        diseaseHeaderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        Label diseaseDescLabel = new Label(selectedDisease.getDescription());
        diseaseDescLabel.setWrapText(true);

        diseaseInfoBox.getChildren().addAll(diseaseHeaderLabel, diseaseDescLabel);
        drugRepurposingContainer.getChildren().add(diseaseInfoBox);

        // Add separator
        Separator separator = new Separator();
        separator.setStyle("-fx-padding: 10 0;");
        drugRepurposingContainer.getChildren().add(separator);

        // Sort drugs by success rate (highest first)
        drugs.sort(Comparator.comparing(DrugRepurposingResult::getSuccessRate).reversed());

        // Add each drug
        for (DrugRepurposingResult drug : drugs) {
            VBox drugCard = createDrugCard(drug);
            drugRepurposingContainer.getChildren().add(drugCard);
        }
    }

    /**
     * Create a UI card for a drug repurposing result
     */
    private VBox createDrugCard(DrugRepurposingResult drug) {
        VBox card = new VBox(5);

        // Color based on success rate
        String backgroundColor;
        if (drug.getSuccessRate() >= 0.7) {
            backgroundColor = "#e6ffe6"; // Light green for high success
        } else if (drug.getSuccessRate() >= 0.4) {
            backgroundColor = "#fff2e6"; // Light orange for medium success
        } else {
            backgroundColor = "#ffe6e6"; // Light red for low success
        }

        card.setStyle("-fx-background-color: " + backgroundColor + "; -fx-padding: 10; -fx-border-color: #dddddd; " +
                "-fx-border-width: 1; -fx-border-radius: 5;");

        // Drug name
        Label nameLabel = new Label(drug.getDrugName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        // Success rate as percentage with progress bar
        double percentage = drug.getSuccessRate() * 100;
        Label successLabel = new Label(String.format("Success Rate: %.1f%%", percentage));

        ProgressBar progressBar = new ProgressBar(drug.getSuccessRate());
        progressBar.setPrefWidth(200);

        // Drug info
        Label mechanismLabel = new Label("Mechanism: " + drug.getMechanismOfAction());
        mechanismLabel.setWrapText(true);

        Label currentUseLabel = new Label("Current Use: " + drug.getCurrentIndication());
        currentUseLabel.setWrapText(true);

        Label evidenceLabel = new Label("Evidence: " + drug.getEvidenceLevel());
        evidenceLabel.setStyle("-fx-font-style: italic;");

        card.getChildren().addAll(nameLabel, successLabel, progressBar,
                mechanismLabel, currentUseLabel, evidenceLabel);

        // Add details button
        Button detailsButton = new Button("View Details");
        detailsButton.setOnAction(e -> showDrugDetails(drug));
        detailsButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");

        HBox buttonBox = new HBox();
        buttonBox.setStyle("-fx-padding: 10 0 0 0;");
        buttonBox.getChildren().add(detailsButton);

        card.getChildren().add(buttonBox);

        return card;
    }

    /**
     * Show detailed information about a drug
     */
    private void showDrugDetails(DrugRepurposingResult drug) {
        try {
            // Load additional drug information if needed
            DrugRepurposingResult detailedDrug = services.getDrugRepurposingService()
                    .getDrugDetailedInformation(drug.getDrugId());

            // Create dialog
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Drug Details: " + drug.getDrugName());
            dialog.setHeaderText("Detailed Information");

            // Create content
            VBox content = new VBox(10);
            content.setStyle("-fx-padding: 20;");
            content.setPrefWidth(500);

            // Drug basic info
            Label nameLabel = new Label(detailedDrug.getDrugName());
            nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

            Label idLabel = new Label("Drug ID: " + detailedDrug.getDrugId());

            // Create sections for different information
            TitledPane mechanismPane = createInfoSection("Mechanism of Action",
                    detailedDrug.getMechanismOfAction());

            TitledPane indicationPane = createInfoSection("Current Indication",
                    detailedDrug.getCurrentIndication());

            TitledPane evidencePane = createInfoSection("Evidence for Repurposing",
                    detailedDrug.getEvidenceDetails());

            TitledPane adversePane = createInfoSection("Potential Adverse Effects",
                    detailedDrug.getAdverseEffects());

            TitledPane dosagePane = createInfoSection("Recommended Dosage",
                    detailedDrug.getDosageInformation());

            content.getChildren().addAll(nameLabel, idLabel, mechanismPane, indicationPane,
                    evidencePane, adversePane, dosagePane);

            // Create scrollpane to handle overflow
            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setFitToWidth(true);
            scrollPane.setPrefHeight(500);

            dialog.getDialogPane().setContent(scrollPane);

            // Add close button
            ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().add(closeBtn);

            dialog.showAndWait();

        } catch (ServicesException ex) {
            ex.printStackTrace();

            // Show error dialog
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Unable to load drug details");
            alert.setContentText("An error occurred: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Create an expandable section for drug information
     */
    private TitledPane createInfoSection(String title, String content) {
        TitledPane pane = new TitledPane();
        pane.setText(title);

        TextArea textArea = new TextArea(content);
        textArea.setWrapText(true);
        textArea.setEditable(false);
        textArea.setPrefRowCount(4);

        pane.setContent(textArea);
        pane.setExpanded(false);

        return pane;
    }

    /**
     * Show the dashboard
     */
    @FXML
    public void showDashboard() {
        dashboardPane.setVisible(true);
        dashboardPane.setManaged(true);

        if (geneInfoPane != null) {
            geneInfoPane.setVisible(false);
            geneInfoPane.setManaged(false);
        }
    }

    /**
     * Show the gene info pane
     */
    private void showGeneInfoPane() {
        dashboardPane.setVisible(false);
        dashboardPane.setManaged(false);

        if (geneInfoPane != null) {
            geneInfoPane.setVisible(true);
            geneInfoPane.setManaged(true);
        }
    }

    @FXML public void showUserMenu(MouseEvent event) {
        boolean now = !userMenuPopup.isVisible();
        userMenuPopup.setVisible(now);
        userMenuPopup.setManaged(now);
        if (now) {
            Bounds b = userIcon.localToScene(userIcon.getBoundsInLocal());
            double x = b.getMinX() + userIcon.getFitWidth()
                    - userMenuPopup.getPrefWidth() - 170;
            userMenuPopup.setLayoutX(x);
            userMenuPopup.setLayoutY(b.getMaxY() - 5);
            userMenuPopup.toFront();
        }
        event.consume();
    }

    @FXML
    public void handleLogin(ActionEvent e) {
        System.out.println("Login clicked");
        userMenuPopup.setVisible(false);
        userMenuPopup.setManaged(false);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui_thing/views/LoginDialog.fxml"));
            Parent root = loader.load();

            LoginDialogController controller = loader.getController();
            controller.setServices(services);
            controller.setOnLoginSuccess(this::setCurrentUser);

            Stage dialogStage = new Stage();
            dialogStage.initStyle(StageStyle.UNDECORATED);
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Login");

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/ui_thing/views/style.css").toExternalForm());
            dialogStage.setScene(scene);

            // Center the dialog on the main window
            Stage mainStage = (Stage) rootPane.getScene().getWindow();
            dialogStage.setX(mainStage.getX() + (mainStage.getWidth() - 400) / 2);
            dialogStage.setY(mainStage.getY() + (mainStage.getHeight() - 300) / 2);

            dialogStage.showAndWait();
        } catch (IOException ex) {
            System.err.println("Error loading login dialog: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @FXML
    public void handleSignUp(ActionEvent e) {
        System.out.println("Sign Up clicked");
        userMenuPopup.setVisible(false);
        userMenuPopup.setManaged(false);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui_thing/views/SignUpDialog.fxml"));
            Parent root = loader.load();

            SignUpDialogController controller = loader.getController();
            controller.setServices(services);
            controller.setOnSignUpSuccess(this::setCurrentUser);

            Stage dialogStage = new Stage();
            dialogStage.initStyle(StageStyle.UNDECORATED);
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Sign Up");

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/ui_thing/views/style.css").toExternalForm());
            dialogStage.setScene(scene);

            // Center the dialog on the main window
            Stage mainStage = (Stage) rootPane.getScene().getWindow();
            dialogStage.setX(mainStage.getX() + (mainStage.getWidth() - 400) / 2);
            dialogStage.setY(mainStage.getY() + (mainStage.getHeight() - 350) / 2);

            dialogStage.showAndWait();
        } catch (IOException ex) {
            System.err.println("Error loading signup dialog: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @FXML
    public void handleLogout(ActionEvent e) {
        System.out.println("Logout clicked");
        userMenuPopup.setVisible(false);
        userMenuPopup.setManaged(false);

        // Clear the current user
        setCurrentUser(Optional.empty());
    }

    @FXML
    public void handleMyAccount(ActionEvent e) {
        System.out.println("My Account clicked");
        userMenuPopup.setVisible(false);
        userMenuPopup.setManaged(false);

        // TODO: Navigate to account screen
    }

    @FXML
    public void handleGeneSearches(ActionEvent e) {
        System.out.println("Gene Searches clicked");
        userMenuPopup.setVisible(false);
        userMenuPopup.setManaged(false);

        try {
            // Load gene search history for the current user
            if (currentUser.isPresent()) {
                List<String> searchHistory =
                        services.getUserService().getGeneSearchHistory(currentUser.get().getId());

                // Create dialog to display search history
                Dialog<Void> dialog = new Dialog<>();
                dialog.setTitle("Gene Search History");
                dialog.setHeaderText("Your Recent Gene Searches");

                VBox content = new VBox(10);
                content.setPadding(new javafx.geometry.Insets(20));

                if (searchHistory.isEmpty()) {
                    content.getChildren().add(new Label("No search history found."));
                } else {
                    // Create a ListView for searches
                    ListView<String> listView = new ListView<>();
                    listView.getItems().addAll(searchHistory);
                    listView.setPrefHeight(300);

                    // Add action to search for gene when clicked
                    listView.setOnMouseClicked(event -> {
                        if (event.getClickCount() == 2) {
                            String selectedGene = listView.getSelectionModel().getSelectedItem();
                            if (selectedGene != null) {
                                dialog.close();
                                searchField.setText(selectedGene);
                                searchGene(selectedGene);
                            }
                        }
                    });

                    content.getChildren().add(listView);
                    content.getChildren().add(new Label("Double-click a gene to search for it."));
                }

                dialog.getDialogPane().setContent(content);
                dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

                dialog.showAndWait();
            }
        } catch (ServicesException ex) {
            ex.printStackTrace();

            // Show error dialog
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Unable to load search history");
            alert.setContentText("An error occurred: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    public void handleSettings(ActionEvent e) {
        System.out.println("Settings clicked");
        userMenuPopup.setVisible(false);
        userMenuPopup.setManaged(false);

        // Create settings dialog
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Application Settings");

        // Create settings content
        VBox content = new VBox(15);
        content.setPadding(new javafx.geometry.Insets(20));

        // Theme setting
        HBox themeBox = new HBox(10);
        themeBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label themeLabel = new Label("Theme:");
        ComboBox<String> themeCombo = new ComboBox<>();
        themeCombo.getItems().addAll("Light", "Dark", "System Default");
        themeCombo.setValue("Light");
        themeBox.getChildren().addAll(themeLabel, themeCombo);

        // Data source setting
        HBox dataSourceBox = new HBox(10);
        dataSourceBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label dataSourceLabel = new Label("Data Source:");
        ComboBox<String> dataSourceCombo = new ComboBox<>();
        dataSourceCombo.getItems().addAll("KEGG", "DrugBank", "Both");
        dataSourceCombo.setValue("KEGG");
        dataSourceBox.getChildren().addAll(dataSourceLabel, dataSourceCombo);

        // Show confidence scores setting
        CheckBox confidenceBox = new CheckBox("Show confidence scores for drug repurposing");
        confidenceBox.setSelected(true);

        // Save searches setting
        CheckBox saveSearchBox = new CheckBox("Save search history");
        saveSearchBox.setSelected(true);

        // Add to content
        content.getChildren().addAll(themeBox, dataSourceBox, confidenceBox, saveSearchBox);

        // Save button
        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        dialog.getDialogPane().setContent(content);

        // Handle save action
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButton) {
                // Save settings (would be implemented in a real app)
                String theme = themeCombo.getValue();
                String dataSource = dataSourceCombo.getValue();
                boolean showConfidence = confidenceBox.isSelected();
                boolean saveSearches = saveSearchBox.isSelected();

                System.out.println("Settings saved: " +
                        "Theme=" + theme + ", " +
                        "DataSource=" + dataSource + ", " +
                        "ShowConfidence=" + showConfidence + ", " +
                        "SaveSearches=" + saveSearches);
            }
            return null;
        });

        dialog.showAndWait();
    }
}