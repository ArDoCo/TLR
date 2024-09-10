/* Licensed under MIT 2024. */
package edu.kit.kastel.mcse.ardoco.tlr.models.informants;

import java.util.List;

public enum LLMArchitecturePrompt {
    DOCUMENTATION_ONLY_V1(
            """
                    Your task is to identify the high-level components based on a software architecture. In a first step, you shall elaborate on the following documentation:

                    %s
                    """,
            "Now provide a list that only covers the component names. Omit common prefixes and suffixes in the names."),//
    CODE_ONLY_V1(
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
                    """), //
    AGGREGATION_V1("""
            You get a list of possible component names. Your task is to aggregate the list and remove duplicates.
            Also filter out component names, that are very generic. Do not repeat what you filtered out.

            Possible component names:

            %s
            """);

    private final List<String> templates;

    LLMArchitecturePrompt(String... templates) {
        this.templates = List.of(templates);
    }

    public List<String> getTemplates() {
        return templates;
    }
}
