# Ontodiff review notes

- **Typo in `SynonymPropety`**
- **Avoid hardcoded stuff (e.g. paths in `Diff`, subClassOf in `HighLevelDiffServiceImpl`)**
- **Make edge predicates consistent IRIs (not label strings)**
- **Make `DiffResult` defensive against nulls (check `inferredInUpdate`/`possiblyNotEntailedInUpdate`)**
- refactoring options
 - **`labelToDisplay` and `definitionToDisplay` could be merged simplifying the code in `HighLevelDiffServiceImpl`**
 - **What is `collectSynonyms` in `HighLevelDiffServiceImpl` for**
 - **check for unused imports**
- Consider integrating the reusable OWLdiff logic to `owldiff-core` to simplify maintenance. 
- **Document module responsibility via README** - not clear whether ontodiff is meant to be a library or a CLI.