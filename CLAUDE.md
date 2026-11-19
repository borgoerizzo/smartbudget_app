## Verzije i izdanja

- Verzija je u `app/build.gradle.kts` (`versionName = "A.BBB"`, `versionCode` se računa sam).
  - Manje izmjene: +0.001 (npr. 3.000 → 3.001). Korisnik može odbiti ažuriranje.
  - Veće izmjene / nova funkcionalnost: +0.1 na sljedeću desetinku (npr. 3.004 → 3.100). Ažuriranje je obavezno.
  - Pravilo obaveznosti je u `data/Azuriranja.kt` (`obavezno`): mijenja li se prva decimala.
- Svaka nova verzija se objavljuje na GitHub repozitoriju https://github.com/borgoerizzo/smartbudget_app.git,
  ali **uvijek prvo pitaj korisnika želi li upload** (commit, push i izdanje). Nikad ne objavljuj bez izričitog "da".
- Aplikacija nove verzije traži preko GitHub Releases tog repozitorija (`releases/latest`), pa svako izdanje mora imati:
  - oznaku (tag) `vA.BBB`, npr. `v3.001`
  - opis izdanja = popis izmjena na hrvatskom (prikazuje se korisniku u aplikaciji), npr. redci `- …`
  - priloženi potpisani release APK `SmartBudget-A.BBB.apk`
- Release build: `gradlew assembleRelease` (JAVA_HOME = Android Studio `jbr`). Potpisuje se ključem
  `C:/Users/Korisnik/kljucevi/smartbudget.jks`; putanja i lozinke su u `keystore.properties` (nije na gitu).
  Bez tog ključa ažuriranja se ne mogu instalirati preko postojeće aplikacije. Gotove APK-ove kopiraj u `izdanja/`.
- Nova verzija baze (Room) uvijek dobiva migraciju; nikad `fallbackToDestructiveMigration`.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. When the answer is under a Markdown heading, `graphify section "<heading>"` prints that section's text, so the .md need not be opened. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- A committed graphify-out/graph.json is rewritten whole on every build, so a large diff to it in a pull request is expected. Do not flag it as churn or suggest skipping the graph rebuild or its hooks.
