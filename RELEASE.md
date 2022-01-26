# How to release

Scio-idea-plugin uses [sbt-idea-plugin](https://github.com/JetBrains/sbt-idea-plugin) to publish plugin updates to JetBrains.

1. Bump the version in `version.sbt` and `src/main/resources/META-INF/plugin.xml`, and push to main.
2. Push a new tag with the updated version:
    ```shell
    git tag -a X.Y.Z -m "X.Y.Z"

    git push origin X.Y.Z
    ```
3. GitHub actions will verify and publish to JetBrains. Verify that your plugin was updated [here](https://plugins.jetbrains.com/plugin/8596-scio-idea/versions).
4. Create a release from the new tag at https://github.com/spotify/scio-idea-plugin/tags.
