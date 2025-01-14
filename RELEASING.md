# Releasing Guide
Release procedure for Kotlin Formatter

1. Bump version number in `gradle.properties` to next stable version (removing the `-SNAPSHOT` suffix).
2. Update CHANGELOG.md
   1. Change the `Unreleased` header to the release version e.g. `[x.y.z] - YYYY-MM-DD`
   2. Add a new `Unreleased` section to the top.
   ```md
   ## [Unreleased]

   New:
   - Nothing yet!

   Changed:
   - Nothing yet!

   Fixed:
   - Nothing yet!
   ```

3. Update README.md if needed
4. Commit
   ```
   $ git commit -am "chore: prepare for release x.y.z."
   ```
5. Tag
   ```
   $ git tag -am "Version x.y.z" x.y.z
   ```
6. Push
   ```
   $ git push && git push --tags
   ```
The tag will trigger a GitHub Action workflow which will upload the artifacts to Maven Central and create a GitHub release
7. Once publish is done, update version number `gradle.properties` to next snapshot version (x.y.z-SNAPSHOT)
8. Commit and push
   ```
   $ git commit -am "chore: prepare next development version." && git push
   ```
