package org.example;

import org.example.repository.JdbcUtils;
import org.example.repository.entitiesRepository.DrugRepurposingRepository;
import org.example.repository.entitiesRepository.GeneDiseaseDrugCompoundRepository;
import org.example.repository.entitiesRepository.InteractionRepository;
import org.example.repository.entitiesRepository.UserRepository;
import org.example.repository.interfaces.IDrugRepurposingRepository;
import org.example.repository.interfaces.IGeneDiseaseDrugCompoundRepository;
import org.example.repository.interfaces.IInteractionRepository;
import org.example.repository.interfaces.IUserRepository;
import org.example.service.AllServices;
import org.example.service.DrugRepurposingService;
import org.example.service.GeneDiseaseDrugCompoundService;
import org.example.service.InteractionService;
import org.example.service.UserService;
import org.example.service.interfaces.IDrugRepurposingService;
import org.example.service.interfaces.IGeneDiseaseDrugCompoundService;
import org.example.service.interfaces.IInteractionService;
import org.example.service.interfaces.IUserService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Properties;

@Configuration
public class GeneExplorerConfig {

    @Bean(name = "jdbcProperties")
    Properties getProperties() {
        Properties properties = new Properties();
        try {
            // Load from classpath (src/main/resources)
            properties.load(getClass().getClassLoader().getResourceAsStream("bd.config"));
        } catch(IOException e) {
            System.err.println("Failed to load bd.config: " + e.getMessage());
            throw new RuntimeException("Configuration file bd.config not found", e);
        }
        return properties;
    }

    // Create a shared JdbcUtils instance that will be used by all repositories
    @Bean(destroyMethod = "closeAllConnections")
    public JdbcUtils jdbcUtils(@Qualifier("jdbcProperties") Properties properties) {
        return new JdbcUtils(properties);
    }

    @Bean
    public IUserRepository userRepository(@Qualifier("jdbcProperties") Properties properties, JdbcUtils jdbcUtils) {
        return new UserRepository(jdbcUtils);
    }

    @Bean
    public IGeneDiseaseDrugCompoundRepository geneDiseaseDrugCompoundRepository(
            @Qualifier("jdbcProperties") Properties properties, JdbcUtils jdbcUtils) {
        return new GeneDiseaseDrugCompoundRepository(jdbcUtils);
    }

    @Bean
    public IInteractionRepository interactionRepository(
            @Qualifier("jdbcProperties") Properties properties, JdbcUtils jdbcUtils) {
        return new InteractionRepository(jdbcUtils);
    }

    @Bean
    public IDrugRepurposingRepository drugRepurposingRepository(
            @Qualifier("jdbcProperties") Properties properties, JdbcUtils jdbcUtils) {
        return new DrugRepurposingRepository(jdbcUtils);
    }

    @Bean
    public IUserService userService(IUserRepository userRepository) {
        return new UserService(userRepository);
    }

    @Bean
    public IGeneDiseaseDrugCompoundService geneDiseaseDrugCompoundService(
            IGeneDiseaseDrugCompoundRepository geneDiseaseDrugCompoundRepository,
            IUserRepository userRepository) {
        return new GeneDiseaseDrugCompoundService(geneDiseaseDrugCompoundRepository, userRepository);
    }

    @Bean
    public IInteractionService interactionService(
            IGeneDiseaseDrugCompoundRepository geneDiseaseDrugCompoundRepository,
            IInteractionRepository interactionRepository) {
        return new InteractionService(geneDiseaseDrugCompoundRepository, interactionRepository);
    }

    @Bean
    public IDrugRepurposingService drugRepurposingService(
            IGeneDiseaseDrugCompoundRepository geneDiseaseDrugCompoundRepository,
            IDrugRepurposingRepository drugRepurposingRepository) {
        return new DrugRepurposingService(geneDiseaseDrugCompoundRepository, drugRepurposingRepository);
    }

    @Bean
    public AllServices allServices(
            IUserService userService,
            IGeneDiseaseDrugCompoundService geneDiseaseDrugCompoundService,
            IInteractionService interactionService,
            IDrugRepurposingService drugRepurposingService) {
        return new AllServices(userService, geneDiseaseDrugCompoundService, interactionService, drugRepurposingService);
    }
}