/* Licensed under MIT 2024. */
package edu.kit.kastel.mcse.ardoco.tlr.models.informants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import edu.kit.kastel.mcse.ardoco.core.api.models.ArchitectureModelType;
import edu.kit.kastel.mcse.ardoco.core.api.models.CodeModelType;
import edu.kit.kastel.mcse.ardoco.core.api.models.ModelStates;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.ArchitectureModel;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.CodeModel;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.architecture.ArchitectureComponent;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.architecture.ArchitectureItem;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.code.CodePackage;
import edu.kit.kastel.mcse.ardoco.core.common.util.CommonTextToolsConfig;
import edu.kit.kastel.mcse.ardoco.core.common.util.DataRepositoryHelper;
import edu.kit.kastel.mcse.ardoco.core.common.util.wordsim.WordSimUtils;
import edu.kit.kastel.mcse.ardoco.core.common.util.wordsim.measures.levenshtein.LevenshteinMeasure;
import edu.kit.kastel.mcse.ardoco.core.data.DataRepository;
import edu.kit.kastel.mcse.ardoco.core.pipeline.agent.Informant;

public class LLMArchitectureProviderInformant extends Informant {
    private static final String MODEL_STATES_DATA = "ModelStatesData";

    private final ChatLanguageModel chatLanguageModel;
    private final LLMArchitecturePrompt documentationPrompt;
    private final LLMArchitecturePrompt codePrompt;
    private final LLMArchitecturePrompt aggregationPrompt;

    public LLMArchitectureProviderInformant(DataRepository dataRepository, LargeLanguageModel largeLanguageModel, LLMArchitecturePrompt documentation,
            LLMArchitecturePrompt code, LLMArchitecturePrompt aggregation) {
        super(LLMArchitectureProviderInformant.class.getSimpleName(), dataRepository);
        String apiKey = System.getenv("OPENAI_API_KEY");
        String orgId = System.getenv("OPENAI_ORG_ID");
        if (apiKey == null || orgId == null) {
            throw new IllegalArgumentException("OPENAI_API_KEY and OPENAI_ORG_ID must be set as environment variables");
        }
        this.chatLanguageModel = largeLanguageModel.create();
        this.documentationPrompt = documentation;
        this.codePrompt = code;
        this.aggregationPrompt = aggregation;
        if (documentationPrompt == null && codePrompt == null) {
            throw new IllegalArgumentException("At least one prompt must be provided");
        }
        if (documentationPrompt != null && codePrompt != null && aggregationPrompt == null) {
            logger.info("Using Similarity Metrics to aggregate the component names");
        }
    }

    @Override
    protected void process() {
        List<String> componentNamesDocumentation = new ArrayList<>();
        List<String> componentNamesCode = new ArrayList<>();
        if (documentationPrompt != null)
            documentationToArchitecture(componentNamesDocumentation);
        if (codePrompt != null)
            codeToArchitecture(componentNamesCode);

        List<String> componentNames = new ArrayList<>();

        if (aggregationPrompt != null) {
            var allComponentNames = Stream.concat(componentNamesDocumentation.stream(), componentNamesCode.stream()).toList();
            var aggregation = chatLanguageModel.generate(aggregationPrompt.getTemplates().getFirst().formatted(String.join("\n", allComponentNames)));
            logger.info("Response (Aggregation): {}", aggregation);
            parseComponentNames(aggregation, componentNames);
        } else if (documentationPrompt != null && codePrompt != null) {
            componentNames = mergeViaSimilarity(componentNamesDocumentation, componentNamesCode);
        } else {
            // If only one prompt is provided, use the component names from that prompt
            componentNames = Stream.concat(componentNamesDocumentation.stream(), componentNamesCode.stream()).toList();
        }

        // Remove any not letter characters
        componentNames = componentNames.stream()
                .map(it -> it.replaceAll("[^a-zA-Z0-9 \\-_]", "").replaceAll("\\s+", " ").trim())
                .map(it -> it.replace("Components", "").replace("Component", "").trim())
                .filter(it -> !it.isBlank())
                .distinct()
                .sorted()
                .toList();
        logger.info("Component names:\n{}", String.join("\n", componentNames));
        buildModel(componentNames);
    }

    private List<String> mergeViaSimilarity(List<String> componentNamesDocumentation, List<String> componentNamesCode) {
        WordSimUtils simUtils = new WordSimUtils();
        simUtils.setMeasures(Collections.singletonList(new LevenshteinMeasure(CommonTextToolsConfig.LEVENSHTEIN_MIN_LENGTH,
                CommonTextToolsConfig.LEVENSHTEIN_MAX_DISTANCE, 0.5)));
        List<String> componentNames = new ArrayList<>();
        for (String componentName : Stream.concat(componentNamesDocumentation.stream(), componentNamesCode.stream()).toList()) {
            if (componentNames.stream().noneMatch(it -> simUtils.areWordsSimilar(it, componentName))) {
                componentNames.add(componentName);
            } else {
                logger.info("Similar component name found (skipping): {}", componentName);
            }
        }
        return componentNames;
    }

    private void documentationToArchitecture(List<String> componentNames) {
        var inputText = DataRepositoryHelper.getInputText(dataRepository);
        parseComponentsFromAiRequests(componentNames, documentationPrompt.getTemplates(), inputText);
    }

    private void codeToArchitecture(List<String> componentNames) {
        var models = DataRepositoryHelper.getModelStatesData(dataRepository);
        CodeModel codeModel = (CodeModel) models.getModel(CodeModelType.CODE_MODEL.getModelId());
        if (codeModel == null) {
            logger.warn("Code model not found");
            return;
        }

        var packages = codeModel.getAllPackages().stream().filter(it -> !it.getContent().isEmpty()).toList();
        parseComponentsFromAiRequests(componentNames, codePrompt.getTemplates(), String.join("\n", packages.stream().map(this::getPackageName).toList()));
    }

    private void parseComponentsFromAiRequests(List<String> componentNames, List<String> templates, String dataForFirstPrompt) {
        String startMessage = templates.getFirst().formatted(dataForFirstPrompt);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from(startMessage));

        var initialResponse = chatLanguageModel.generate(messages).content();
        messages.add(initialResponse);
        logger.info("Initial Response: {}", initialResponse.text());

        for (String nextMessage : templates.stream().skip(1).toList()) {
            messages.add(UserMessage.from(nextMessage));
            var response = chatLanguageModel.generate(messages).content();
            logger.info("Response: {}", response.text());
            messages.add(response);
        }

        parseComponentNames(((AiMessage) messages.getLast()).text(), componentNames);
    }

    private String getPackageName(CodePackage codePackage) {
        List<String> packageName = new ArrayList<>();
        packageName.add(codePackage.getName());
        var parent = codePackage.getParent();
        while (parent != null) {
            packageName.add(parent.getName());
            parent = parent.getParent();
        }
        packageName = packageName.reversed();
        return String.join(".", packageName);
    }

    private void parseComponentNames(String response, List<String> componentNames) {
        for (String line : response.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            line = line.trim();

            // Version 5: 1. Name (NotImportant) or 2. Name (SomeString)
            if (line.matches("^\\d+\\.\\s+.+\\s*\\(.*\\)$")) {
                componentNames.add(line.split("\\.\\s+")[1].split("\\s*\\(.*\\)")[0]);
            }
            // Version 1: 1. **Name** or 2. **Name**
            else if (line.matches("^\\d+\\.\\s*\\*\\*.*\\*\\*$")) {
                componentNames.add(line.split("\\*\\*")[1]);
            }
            // Version 2: 1. Name or 2. Name
            else if (line.matches("^\\d+\\.\\s+.*$")) {
                componentNames.add(line.split("\\d+\\.\\s+")[1]);
            }
            // Version 3: - **Name**
            else if (line.matches("^([-*])\\s+\\*\\*.*\\*\\*$")) {
                componentNames.add(line.split("\\*\\*")[1]);
            }
            // Version 4: - Name
            else if (line.matches("^([-*])\\s+.*$")) {
                componentNames.add(line.split("([-*])\\s+")[1]);
            }
            // Version 5: Name, Name2, Name3, (at least 4 names .. otherwise we match to many things)
            else if (line.matches("(.*,\\s+){3,}.*")) {
                componentNames.addAll(List.of(line.split(",\\s+")));
            } else {
                logger.warn("Could not parse line: {}", line);
            }
        }
    }

    private void buildModel(List<String> componentNames) {
        List<ArchitectureItem> componentList = componentNames.stream()
                .map(it -> new ArchitectureComponent(it, it, new TreeSet<>(), new TreeSet<>(), new TreeSet<>(), "Component"))
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
