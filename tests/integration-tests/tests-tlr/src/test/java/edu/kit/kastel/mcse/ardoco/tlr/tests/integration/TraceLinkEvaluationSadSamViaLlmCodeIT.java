/* Licensed under MIT 2023-2024. */
package edu.kit.kastel.mcse.ardoco.tlr.tests.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.mcse.ardoco.core.api.output.ArDoCoResult;
import edu.kit.kastel.mcse.ardoco.core.tests.eval.CodeProject;
import edu.kit.kastel.mcse.ardoco.tlr.models.informants.LLMArchitecturePrompt;
import edu.kit.kastel.mcse.ardoco.tlr.models.informants.LargeLanguageModel;

class TraceLinkEvaluationSadSamViaLlmCodeIT {
    private static final Logger logger = LoggerFactory.getLogger(TraceLinkEvaluationSadSamViaLlmCodeIT.class);
    protected static final String LOGGING_ARDOCO_CORE = "org.slf4j.simpleLogger.log.edu.kit.kastel.mcse.ardoco.core";

    private static final Map<Pair<CodeProject, LargeLanguageModel>, ArDoCoResult> RESULTS = new HashMap<>();

    @BeforeAll
    static void beforeAll() {
        System.setProperty(LOGGING_ARDOCO_CORE, "info");
    }

    @AfterAll
    static void afterAll() {
        System.setProperty(LOGGING_ARDOCO_CORE, "error");
    }

    @DisplayName("Evaluate SAD-SAM-via-LLM-Code TLR")
    @ParameterizedTest(name = "{0} ({1})")
    @MethodSource("llmsXprojects")
    void evaluateSadCodeTlrIT(CodeProject project, LargeLanguageModel llm) {
        if (llm.isGeneric()) {
            Assumptions.abort("Generic LLM is disabled");
        }
        if (!llm.isOpenAi()) {
            Assumptions.abort("Local Models are disabled");
        }

        logger.info("###############################################");
        logger.info("Evaluating project {} with LLM '{}'", project, llm);
        var evaluation = new SadSamViaLlmCodeTraceabilityLinkRecoveryEvaluation(true, llm, LLMArchitecturePrompt.DOCUMENTATION_ONLY_V1, null, null);
        var result = evaluation.runTraceLinkEvaluation(project);
        if (result != null) {
            RESULTS.put(Tuples.pair(project, llm), result);
        }
        System.err.printf("--- Evaluated project %s with LLM '%s' ---%n", project, llm);
        logger.info("###############################################");
    }

    @AfterAll
    static void printResults() {
        logger.info("!!!!!!!!! Results !!!!!!!!!!");
        System.out.println(Arrays.stream(CodeProject.values()).map(Enum::name).collect(Collectors.joining(" & ")) + " \\\\");
        for (LargeLanguageModel llm : LargeLanguageModel.values()) {
            if (llm.isGeneric()) {
                continue;
            }
            String llmResult = llm.name() + " ";
            for (CodeProject project : CodeProject.values()) {
                if (!RESULTS.containsKey(Tuples.pair(project, llm))) {
                    llmResult += "&--&--&--";
                    continue;
                }
                ArDoCoResult result = RESULTS.get(Tuples.pair(project, llm));

                // Just some instance .. parameters do not matter ..
                var evaluation = new SadSamViaLlmCodeTraceabilityLinkRecoveryEvaluation(true, llm, LLMArchitecturePrompt.DOCUMENTATION_ONLY_V1, null, null);
                var goldStandard = project.getSadCodeGoldStandard();
                goldStandard = TraceabilityLinkRecoveryEvaluation.enrollGoldStandardForCode(goldStandard, result);
                var evaluationResults = evaluation.calculateEvaluationResults(result, goldStandard);
                llmResult += String.format("&%.2f&%.2f&%.2f", evaluationResults.precision(), evaluationResults.recall(), evaluationResults.f1());
            }
            llmResult += "&&&&&&\\\\"; // end of line
            System.out.println(llmResult);
        }
    }

    private static Stream<Arguments> llmsXprojects() {
        List<Arguments> result = new ArrayList<>();
        for (LargeLanguageModel llm : LargeLanguageModel.values()) {
            for (CodeProject codeProject : CodeProject.values()) {
                result.add(Arguments.of(codeProject, llm));
            }
        }
        return result.stream();
    }
}
