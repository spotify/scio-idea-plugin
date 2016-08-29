# Scio IDEA plugin

Scio plugin for IDEA, enables BigQuery macro support in IDEA.

# Install

Download most recent [release](https://github.com/ravwojdyla/scio-idea-plugin/releases).

Inside IntelliJ: `Preferences` -> `Plugins` -> `Install plugin from disk`

## Build locally:

```bash
sbt packagePlugin
```

Zipped and ready to install plugin is inside `target`.

# Usage

Install following instructions above. Plugin should work out of the box.

## BigQuery location

If you get the location (EU/US) error - you need to add location setting to scala compiler in your IntelliJ.
The easiest way is to click on the weird looking "clock" in the bottom-right corner - it's actually a shortcut to scala compiler.
If it's already running - stop it, then press `Configure...` and in `JVM parameters` add `-Dbigquery.staging_dataset.location=EU`.

# Logging

This pluging is using IDEA diagnostic logger, you can find the log file
under standard IntelliJ directory ([doc](https://intellij-support.jetbrains.com/hc/en-us/articles/206544519-Directories-used-by-the-IDE-to-store-settings-caches-plugins-and-logs)). For example of Mac: `~/Library/Logs/<PRODUCT><VERSION>/idea.log`.

If there is error level messaged logged, it will show up in IntelliJ Event Log, visible to the user.

# License

Copyright 2016 Spotify AB.

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
