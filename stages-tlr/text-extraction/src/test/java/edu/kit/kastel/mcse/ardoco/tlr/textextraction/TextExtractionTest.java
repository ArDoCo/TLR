/* Licensed under MIT 2023-2024. */
package edu.kit.kastel.mcse.ardoco.tlr.textextraction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.set.sorted.ImmutableSortedSet;
import org.eclipse.collections.impl.factory.Lists;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import edu.kit.kastel.mcse.ardoco.core.api.Disambiguation;
import edu.kit.kastel.mcse.ardoco.core.api.textextraction.PhraseAbbreviation;
import edu.kit.kastel.mcse.ardoco.core.api.textextraction.WordAbbreviation;
import edu.kit.kastel.mcse.ardoco.core.common.util.CommonUtilities;
import edu.kit.kastel.mcse.ardoco.core.common.util.DataRepositoryHelper;
import edu.kit.kastel.mcse.ardoco.core.data.DataRepository;
import edu.kit.kastel.mcse.ardoco.core.execution.runner.AnonymousRunner;
import edu.kit.kastel.mcse.ardoco.core.pipeline.AbstractPipelineStep;
import edu.kit.kastel.mcse.ardoco.core.tests.eval.GoldStandardProject;
import edu.kit.kastel.mcse.ardoco.core.tests.eval.HistoricProject;
import edu.kit.kastel.mcse.ardoco.core.tests.eval.Project;
import edu.kit.kastel.mcse.ardoco.core.tests.eval.helper.StageTest;
import edu.kit.kastel.mcse.ardoco.tlr.text.providers.TextPreprocessingAgent;

class TextExtractionTest extends StageTest<TextExtraction, TextExtractionTest.TextProject, TextExtractionTest.TextExtractionResult> {
    public TextExtractionTest() {
        super(new TextExtraction(null), TextProject.values());
    }

    @Override
    protected TextExtractionResult runComparable(TextProject project, SortedMap<String, String> additionalConfigurations, boolean cachePreRun) {
        var dataRepository = run(project, additionalConfigurations, cachePreRun);

        var wordAbbreviations = DataRepositoryHelper.getTextState(dataRepository).getWordAbbreviations();
        var phraseAbbreviations = DataRepositoryHelper.getTextState(dataRepository).getPhraseAbbreviations();

        return new TextExtractionResult(wordAbbreviations, phraseAbbreviations);
    }

    @Override
    protected DataRepository runPreTestRunner(TextProject project) {
        return new AnonymousRunner(project.getProjectName()) {
            @Override
            public List<AbstractPipelineStep> initializePipelineSteps(DataRepository dataRepository) throws IOException {
                var pipelineSteps = new ArrayList<AbstractPipelineStep>();

                var text = CommonUtilities.readInputText(project.getTextFile());
                if (text.isBlank()) {
                    throw new IllegalArgumentException("Cannot deal with empty input text. Maybe there was " + "an error reading the file.");
                }
                DataRepositoryHelper.putInputText(dataRepository, text);
                pipelineSteps.add(TextPreprocessingAgent.get(project.getAdditionalConfigurations(), dataRepository));

                return pipelineSteps;
            }
        }.runWithoutSaving();
    }

    @Override
    protected DataRepository runTestRunner(TextProject project, SortedMap<String, String> additionalConfigurations, DataRepository preRunDataRepository) {
        return new AnonymousRunner(project.getProjectName(), preRunDataRepository) {
            @Override
            public List<AbstractPipelineStep> initializePipelineSteps(DataRepository dataRepository) {
                var pipelineSteps = new ArrayList<AbstractPipelineStep>();
                pipelineSteps.add(TextExtraction.get(project.getAdditionalConfigurations(), dataRepository));
                return pipelineSteps;
            }
        }.runWithoutSaving();
    }

    public record TextExtractionResult(ImmutableSortedSet<WordAbbreviation> wordAbbreviations, ImmutableSortedSet<PhraseAbbreviation> phraseAbbreviations) {
    }

    @DisplayName("Evaluate Text Extraction")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = TextProject.class, mode = EnumSource.Mode.MATCH_NONE, names = "^" + ".*HISTORICAL$")
    @Order(1)
    void evaluateNonHistoricalDiagramRecognition(TextProject project) {
        runComparable(project);
    }

    public enum TextProject implements GoldStandardProject {
        MEDIASTORE(//
                Project.MEDIASTORE, //
                List.of() //
        ), //
        TEASTORE( //
                Project.TEASTORE, //
                List.of(new Disambiguation("LFU", new String[] { "Least Frequently Used" }), new Disambiguation("CRUD", new String[] {
                        "Create Read Update Delete" })) //
        ), //
        TEAMMATES( //
                Project.TEAMMATES, //
                List.of(new Disambiguation("GAE", new String[] { "Google App Engine" }), new Disambiguation("POJOs", new String[] { "Plain Old Java Objects" }),
                        new Disambiguation("CRUD", new String[] { "Create, Read, Update, Delete" }), new Disambiguation("L&P", new String[] {
                                "Load & Performance" })) //
        ), //
        BIGBLUEBUTTON( //
                Project.BIGBLUEBUTTON, //
                List.of(new Disambiguation("LMS", new String[] { "learning management system" }), new Disambiguation("fsels", new String[] {
                        "FreeSWITCH Event Socket Layer" }), new Disambiguation("SVG", new String[] { "scalable vector graphics" }))  //
        ), //
        TEASTORE_HISTORICAL( //
                HistoricProject.TEASTORE_HISTORICAL, //
                List.of(new Disambiguation("REST", new String[] { "representational state transfer" }), new Disambiguation("JSP", new String[] {
                        "Java Server Page" }), new Disambiguation("JSPs", new String[] { "Java Server Pages" }), new Disambiguation("OPEN.xtrace",
                                new String[] { "Open Execution Trace " + "Exchange" })) //
        ), //
        TEAMMATES_HISTORICAL( //
                HistoricProject.TEAMMATES_HISTORICAL, //
                List.of(new Disambiguation("GAE", new String[] { "Google App Engine" }), new Disambiguation("JSP", new String[] { "Java Server Pages" }),
                        new Disambiguation("POJOs", new String[] { "Plain Old Java Objects" }), new Disambiguation("CRUD", new String[] {
                                "Create Read Update Delete" })) //
        ), //
        BIGBLUEBUTTON_HISTORICAL( //
                HistoricProject.BIGBLUEBUTTON_HISTORICAL, //
                List.of(new Disambiguation("LMS", new String[] { "learning management system" })) //
        );

        private final GoldStandardProject project;
        private final ImmutableList<Disambiguation> disambiguations;

        TextProject(GoldStandardProject project, List<Disambiguation> disambiguations) {
            this.project = project;
            this.disambiguations = Lists.immutable.ofAll(disambiguations);
        }

        @Override
        public String getProjectName() {
            return project.getProjectName();
        }

        @Override
        public SortedSet<String> getResourceNames() {
            return project.getResourceNames();
        }
    }
}
