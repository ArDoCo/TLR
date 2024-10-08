/* Licensed under MIT 2022-2024. */
package edu.kit.kastel.mcse.ardoco.tlr.text.providers.corenlp;

import edu.kit.kastel.mcse.ardoco.core.api.text.NlpInformant;
import edu.kit.kastel.mcse.ardoco.tlr.text.providers.base.WordTest;

class CoreNLPWordTest extends WordTest {
    @Override
    protected NlpInformant getProvider() {
        return CoreNLPProviderTest.getCoreNLPProvider();
    }
}
