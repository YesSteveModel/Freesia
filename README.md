# Freesia II
[![Issues](https://img.shields.io/github/issues/KikirMeow/Freesia?style=flat-square)](https://github.com/KikirMeow/Freesia/issues)
![Commit Activity](https://img.shields.io/github/commit-activity/w/KikirMeow/Freesia?style=flat-square)
![GitHub Repo stars](https://img.shields.io/github/stars/KikirMeow/Freesia?style=flat-square)
![GitHub License](https://img.shields.io/github/license/KikirMeow/Freesia)

A continuation of Cyanidin (work in progress)

# Introduction

This project was formerly known as **Cyanidin**. After the original author abandoned it, the project was handed over and renamed **Freesia** by *KikirMeow*. 
After a long period without regular updates and with many bugs, the project is now being continued by *NguyenDevs* under the name **Freesia II**.
# Documentation

Please refer to the [YSM Documentation](https://ysm.cfpa.team/wiki/freesia-plugin/) for details.

# Building

```shell
chmod +777 ./gradlew
./gradlew build
```

```shell
gradlew.bat build
```

Build artifacts are located in the `build/libs` directory of each module.

# How It Works

In broad terms, Freesia is somewhat of a hybrid between MultiPaper and Geyser. Its working principles are based on MultiPaper's cross-server data exchange mechanism. For certain packets, since it needs to determine whether players can see each other and whether entity IDs differ between the worker and sub-servers, some modifications were made accordingly.

# Performance

Freesia's performance has not been formally benchmarked. However, according to MrHua269, it should be able to handle around 130 players without issue. That said, in many cases YSM's own cache synchronization can cause memory leaks and other unusual problems that break model sync.

# Roadmap

There is no concrete roadmap yet, as the codebase still requires more time to fully understand. The general direction for the future is to attempt multi-worker support and complete the unfinished NPC plugin extension. (This may spawn a few separate new projects.)
