package cz.cvut.fel.ontodiff.changes;

import org.semanticweb.owlapi.model.OWLLiteral;

import java.util.Objects;

/**
 * Lossless value of an OWL literal used in a reported ontology change.
 */
public record LiteralValue(
        String lexicalForm,
        String language,
        String datatypeIri
) {
    public LiteralValue {
        lexicalForm = Objects.requireNonNull(lexicalForm, "lexicalForm");
        language = language == null ? "" : language;
        datatypeIri = Objects.requireNonNull(datatypeIri, "datatypeIri");
        if (datatypeIri.isBlank()) {
            throw new IllegalArgumentException("datatypeIri must not be blank");
        }
    }

    public static LiteralValue from(OWLLiteral literal) {
        Objects.requireNonNull(literal, "literal");
        return new LiteralValue(
                literal.getLiteral(),
                literal.hasLang() ? literal.getLang() : "",
                literal.getDatatype().getIRI().toString()
        );
    }

    public boolean hasLanguage() {
        return !language.isBlank();
    }
}
