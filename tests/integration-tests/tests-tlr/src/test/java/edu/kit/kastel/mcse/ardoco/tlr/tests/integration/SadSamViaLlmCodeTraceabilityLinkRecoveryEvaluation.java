/* Licensed under MIT 2023-2024. */
package edu.kit.kastel.mcse.ardoco.tlr.tests.integration;

import static edu.kit.kastel.mcse.ardoco.tlr.tests.integration.TraceLinkEvaluationIT.OUTPUT;

import java.io.File;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import edu.kit.kastel.mcse.ardoco.core.api.models.CodeModelType;
import edu.kit.kastel.mcse.ardoco.core.api.models.ModelStates;
import edu.kit.kastel.mcse.ardoco.core.api.models.arcotl.Model;
import edu.kit.kastel.mcse.ardoco.core.api.output.ArDoCoResult;
import edu.kit.kastel.mcse.ardoco.core.api.text.Text;
import edu.kit.kastel.mcse.ardoco.core.common.util.DataRepositoryHelper;
import edu.kit.kastel.mcse.ardoco.core.common.util.TraceLinkUtilities;
import edu.kit.kastel.mcse.ardoco.core.data.DataRepository;
import edu.kit.kastel.mcse.ardoco.core.execution.runner.ArDoCoRunner;
import edu.kit.kastel.mcse.ardoco.core.tests.eval.CodeProject;
import edu.kit.kastel.mcse.ardoco.core.tests.eval.results.ExpectedResults;
import edu.kit.kastel.mcse.ardoco.tlr.execution.ArDoCoForSadSamViaLlmCodeTraceabilityLinkRecovery;

class SadSamViaLlmCodeTraceabilityLinkRecoveryEvaluation extends TraceabilityLinkRecoveryEvaluation<CodeProject> {
    private final boolean acmFile;

    public SadSamViaLlmCodeTraceabilityLinkRecoveryEvaluation(boolean acmFile) {
        super();
        this.acmFile = acmFile;
    }

    @Override
    protected boolean resultHasRequiredData(ArDoCoResult arDoCoResult) {
        var traceLinks = arDoCoResult.getSadCodeTraceLinks();
        return !traceLinks.isEmpty();
    }

    @Override
    protected ArDoCoRunner getAndSetupRunner(CodeProject codeProject) {
        String name = codeProject.name().toLowerCase();
        File textInput = codeProject.getTextFile();
        File inputCode = getInputCode(codeProject, acmFile);
        SortedMap<String, String> additionalConfigsMap = new TreeMap<>();
        File outputDir = new File(OUTPUT);

        var runner = new ArDoCoForSadSamViaLlmCodeTraceabilityLinkRecovery(name);
        runner.setUp(textInput, inputCode, additionalConfigsMap, outputDir);
        return runner;
    }

    @Override
    protected ImmutableList<String> createTraceLinkStringList(ArDoCoResult arDoCoResult) {
        var traceLinks = arDoCoResult.getSadCodeTraceLinks();

        return TraceLinkUtilities.getSadCodeTraceLinksAsStringList(Lists.immutable.ofAll(traceLinks));
    }

    @Override
    protected ImmutableList<String> getGoldStandard(CodeProject codeProject) {
        return codeProject.getSadCodeGoldStandard();
    }

    @Override
    protected ImmutableList<String> enrollGoldStandard(ImmutableList<String> goldStandard, ArDoCoResult result) {
        return enrollGoldStandardForCode(goldStandard, result);
    }

    @Override
    protected ExpectedResults getExpectedResults(CodeProject codeProject) {
        return codeProject.getExpectedResultsForSadSamCode();
    }

    @Override
    protected int getConfusionMatrixSum(ArDoCoResult arDoCoResult) {
        DataRepository dataRepository = arDoCoResult.dataRepository();

        Text text = DataRepositoryHelper.getAnnotatedText(dataRepository);
        int sentences = text.getSentences().size();

        ModelStates modelStatesData = DataRepositoryHelper.getModelStatesData(dataRepository);
        Model codeModel = modelStatesData.getModel(CodeModelType.CODE_MODEL.getModelId());
        var codeModelEndpoints = codeModel.getEndpoints().size();

        return sentences * codeModelEndpoints;
    }
}
