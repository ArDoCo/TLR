/* Licensed under MIT 2024. */
package edu.kit.kastel.mcse.ardoco.tlr.models.informants;

import java.util.List;

public enum LLMArchitecturePrompt {
    DOCUMENTATION_ONLY_V1(
            """
                    Your task is to identify the high-level components based on a software architecture documentation. In a first step, you shall elaborate on the following documentation:

                    %s
                    """,
            "Now provide a list that only covers the component names. Omit common prefixes and suffixes in the names."), //
    CODE_ONLY_V1(
            """
                    You get the Packages of a software project. Your task is to summarize the Packages w.r.t. the high-level architecture of the system. Try to identify possible components.

                    Packages:

                    %s
                    """,
            "Now provide a list that only covers the component names. Omit common prefixes and suffixes in the names."), //
    AGGREGATION_V1("""
            You get a list of possible component names. Your task is to aggregate the list and remove duplicates.
            Also filter out component names that are very generic. Do not repeat what you filtered out. Only provide the final list.

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
