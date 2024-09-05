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
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import edu.kit.kastel.mcse.ardoco.core.api.models.ArchitectureModelType;
import edu.kit.kastel.mcse.ardoco.core.api.models.CodeModelType;
import edu.kit.kastel.mcse.ardoco.core.api.models.ModelStates;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.ArchitectureModel;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.CodeModel;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.architecture.ArchitectureComponent;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.architecture.ArchitectureItem;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.code.CodePackage;
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

    private static final List<String> TEMPLATES_CODE_TO_ARCHITECTURE = List.of(
            """
                    You get the package names of a software project. Your task is to summarize the packages w.r.t. the architecture of the system. Try to identify possible components.

                    Packages:

                    %s
                    """,
            """
                    You get a summarization and a suggestion of the components of a software project.
                    Identify the possible component names and list them. Only list the component names. If you don't know what the component is about, omit it.

                    Summarization:

                    %s
                    """);

    private static final String AGGREGATION_PROMPT = """
            You get a list of possible component names. Your task is to aggregate the list and remove duplicates.
            Also filter out component names, that are very generic. Do not repeat what you filtered out.

            Possible component names:

            %s
            """;

    public LLMArchitectureProviderInformant(DataRepository dataRepository) {
        super(LLMArchitectureProviderInformant.class.getSimpleName(), dataRepository);
        String apiKey = System.getenv("OPENAI_API_KEY");
        String orgId = System.getenv("OPENAI_ORG_ID");
        if (apiKey == null || orgId == null) {
            throw new IllegalArgumentException("OPENAI_API_KEY and OPENAI_ORG_ID must be set as environment variables");
        }
        this.chatLanguageModel = new OpenAiChatModel.OpenAiChatModelBuilder().modelName(OpenAiChatModelName.GPT_4_O)
                .apiKey(apiKey)
                .organizationId(orgId)
                .seed(422413373)
                .temperature(0.0)
                .build();
    }

    @Override
    protected void process() {
        List<String> componentNames = new ArrayList<>();
        documentationToArchitecture(componentNames);
        // codeToArchitecture(componentNames);
        // Remove any not letter characters
        componentNames = componentNames.stream().map(it -> it.replaceAll("[^a-zA-Z -_]", "").trim()).filter(it -> !it.isBlank()).distinct().sorted().toList();

        /*
        var aggregation = chatLanguageModel.generate(AGGREGATION_PROMPT.formatted(String.join("\n", componentNames)));
        componentNames = new ArrayList<>();
        parseComponentNames(aggregation, componentNames);
        */
        logger.info("Component names:\n{}", String.join("\n", componentNames));

        buildModel(componentNames);

    }

    private void codeToArchitecture(List<String> componentNames) {
        var models = DataRepositoryHelper.getModelStatesData(dataRepository);
        CodeModel codeModel = (CodeModel) models.getModel(CodeModelType.CODE_MODEL.getModelId());
        if (codeModel == null) {
            logger.warn("Code model not found");
            return;
        }

        var packages = codeModel.getAllPackages().stream().filter(it -> it.getContent().size() > 1).toList();

        List<String> responses = new ArrayList<>();
        responses.add(String.join("\n", packages.stream().map(it -> getPackageName(it)).toList()));
        for (String template : TEMPLATES_CODE_TO_ARCHITECTURE) {
            var filledTemplate = template.formatted(responses.getLast());
            var response = chatLanguageModel.generate(filledTemplate);
            logger.info("Response: {}", response);
            responses.add(response);
        }
        parseComponentNames(responses.getLast(), componentNames);

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
            else if (line.matches("^-\\s*\\*\\*.*\\*\\*$")) {
                componentNames.add(line.split("\\*\\*")[1]);
            }
            // Version 4: - Name
            else if (line.matches("^-\\s*.*$")) {
                componentNames.add(line.split("-\\s*")[1]);
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
