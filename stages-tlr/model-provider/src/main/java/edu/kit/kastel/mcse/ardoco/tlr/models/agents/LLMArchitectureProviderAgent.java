/* Licensed under MIT 2024. */
package edu.kit.kastel.mcse.ardoco.tlr.models.agents;

import java.util.List;

import edu.kit.kastel.mcse.ardoco.core.data.DataRepository;
import edu.kit.kastel.mcse.ardoco.core.pipeline.agent.PipelineAgent;
import edu.kit.kastel.mcse.ardoco.tlr.models.informants.LLMArchitectureProviderInformant;
import edu.kit.kastel.mcse.ardoco.tlr.models.informants.LargeLanguageModel;

public class LLMArchitectureProviderAgent extends PipelineAgent {

    public LLMArchitectureProviderAgent(DataRepository dataRepository, LargeLanguageModel largeLanguageModel) {
        super(List.of(new LLMArchitectureProviderInformant(dataRepository, largeLanguageModel)), LLMArchitectureProviderAgent.class.getSimpleName(),
                dataRepository);
    }
}
