/* Licensed under MIT 2024. */
package edu.kit.kastel.mcse.ardoco.tlr.models.informants;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import edu.kit.kastel.mcse.ardoco.core.api.models.ArchitectureModelType;
import edu.kit.kastel.mcse.ardoco.core.api.models.ModelStates;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.ArchitectureModel;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.architecture.ArchitectureComponent;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.architecture.ArchitectureItem;
import edu.kit.kastel.mcse.ardoco.core.common.util.DataRepositoryHelper;
import edu.kit.kastel.mcse.ardoco.core.data.DataRepository;
import edu.kit.kastel.mcse.ardoco.core.pipeline.agent.Informant;

public class LLMArchitectureProviderInformant extends Informant {
    private static final String MODEL_STATES_DATA = "ModelStatesData";

    private final ChatLanguageModel chatLanguageModel;

    private static final List<String> TEMPLATES_DOC_TO_ARCHITECTURE = List.of(
            """
                    Your task is to identify the high-level components based on a software architecture. In a first step, you shall elaborate on the following documentation:

                    %s
                    """,
            "Now provide a list that only covers the component names. Omit common prefixes and suffixes in the names.");

    public LLMArchitectureProviderInformant(DataRepository dataRepository, LargeLanguageModel largeLanguageModel) {
        super(LLMArchitectureProviderInformant.class.getSimpleName(), dataRepository);
        String apiKey = System.getenv("OPENAI_API_KEY");
        String orgId = System.getenv("OPENAI_ORG_ID");
        if (apiKey == null || orgId == null) {
            throw new IllegalArgumentException("OPENAI_API_KEY and OPENAI_ORG_ID must be set as environment variables");
        }
        this.chatLanguageModel = largeLanguageModel.create();
    }

    @Override
    protected void process() {
        List<String> componentNames = new ArrayList<>();
        documentationToArchitecture(componentNames);
        // Remove any not letter characters
        componentNames = componentNames.stream().map(it -> it.replaceAll("[^a-zA-Z -_]", "").trim()).filter(it -> !it.isBlank()).distinct().sorted().toList();
        logger.info("Component names:\n{}", String.join("\n", componentNames));
        buildModel(componentNames);
    }

    private void documentationToArchitecture(List<String> componentNames) {
        var inputText = DataRepositoryHelper.getInputText(dataRepository);
        String startMessage = TEMPLATES_DOC_TO_ARCHITECTURE.getFirst().formatted(inputText);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from(startMessage));

        var initialResponse = chatLanguageModel.generate(messages).content();
        messages.add(initialResponse);
        logger.info("Initial Response: {}", initialResponse.text());

        for (String nextMessage : TEMPLATES_DOC_TO_ARCHITECTURE.stream().skip(1).toList()) {
            messages.add(UserMessage.from(nextMessage));
            var response = chatLanguageModel.generate(messages).content();
            logger.info("Response: {}", response.text());
            messages.add(response);
        }

        parseComponentNames(((AiMessage) messages.getLast()).text(), componentNames);
    }

    private void parseComponentNames(String response, List<String> componentNames) {
        for (String line : response.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            line = line.trim();
            // Version 1: 1. **Name** or 2. **Name**
            if (line.matches("^\\d+\\.\\s*\\*\\*.*\\*\\*$")) {
                componentNames.add(line.split("\\*\\*")[1]);
            }
            // Version 2: 1. Name or 2. Name
            else if (line.matches("^\\d+\\.\\s*.*$")) {
                componentNames.add(line.split("\\.\\s*")[1]);
            }
            // Version 3: - **Name**
            else if (line.matches("^([-*])\\s*\\*\\*.*\\*\\*$")) {
                componentNames.add(line.split("\\*\\*")[1]);
            }
            // Version 4: - Name
            else if (line.matches("^([-*])\\s*.*$")) {
                componentNames.add(line.split("([-*])\\s*")[1]);
            } else {
                logger.warn("Could not parse line: {}", line);
            }
        }
    }

    private void buildModel(List<String> componentNames) {
        List<ArchitectureItem> componentList = componentNames.stream()
                .map(it -> new ArchitectureComponent(it.replace("Component", "").trim(), it, new TreeSet<>(), new TreeSet<>(), new TreeSet<>(), "Component"))
                .collect(Collectors.toList());
        ArchitectureModel am = new ArchitectureModel(componentList);
        Optional<ModelStates> modelStatesOptional = dataRepository.getData(MODEL_STATES_DATA, ModelStates.class);
        var modelStates = modelStatesOptional.orElseGet(ModelStates::new);

        modelStates.addModel(ArchitectureModelType.PCM.getModelId(), am);

        if (modelStatesOptional.isEmpty()) {
            dataRepository.addData(MODEL_STATES_DATA, modelStates);
        }
    }
}
