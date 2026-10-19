package cz.cvut.fel.ontodiff.changes;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLDataFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiteralValueTest {

    private final OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();

    @Test
    public void preservesLanguageTaggedLiteral() {
        LiteralValue value = LiteralValue.from(
                dataFactory.getOWLLiteral("Influenza", "en"));

        assertEquals("Influenza", value.lexicalForm());
        assertEquals("en", value.language());
        assertEquals("http://www.w3.org/1999/02/22-rdf-syntax-ns#langString",
                value.datatypeIri());
        assertTrue(value.hasLanguage());
    }

    @Test
    public void preservesTypedLiteral() {
        LiteralValue value = LiteralValue.from(
                dataFactory.getOWLLiteral(
                        "42", dataFactory.getIntegerOWLDatatype()));

        assertEquals("42", value.lexicalForm());
        assertEquals("", value.language());
        assertEquals("http://www.w3.org/2001/XMLSchema#integer",
                value.datatypeIri());
        assertFalse(value.hasLanguage());
    }
}
