# 香雪兰 Freesia

[![Issues](https://img.shields.io/github/issues/KikirMeow/Freesia?style=flat-square)](https://github.com/KikirMeow/Freesia/issues)
![Commit Activity](https://img.shields.io/github/commit-activity/w/KikirMeow/Freesia?style=flat-square)
![GitHub Repo stars](https://img.shields.io/github/stars/KikirMeow/Freesia?style=flat-square)
![GitHub License](https://img.shields.io/github/license/KikirMeow/Freesia)

Cyanidin的延续版（正在施工中）

# 简介

这个项目曾叫做Cyanidin，但原所有者MrHua269放弃了它，据他本人描述，他并不想在ysm这个混乱的社区里继续做第三方开发了，这个项目只是在给他徒增压力，他不想再继续维护了，我有天在minebbs看到了他的接坑帖子，便找他把项目要了过来，并改名为Freesia香雪兰，且现在也成为了YSM的官方项目之一。

# 文档
请参见 [YSM 文档](https://ysm.cfpa.team/wiki/freesia-plugin/)的此处。

# 构建

```shell
chmod +777 ./gradlew
./gradlew build
```

```shell
gradlew.bat build
```

构建产物分别在每个模块的`build/libs`目录下。

# 原理

总体上来说，香雪兰其实更像是一个MultiPaper和Geyser的混合体，项目的工作原理也参考了multipaper的跨服数据交换机制，对于部分数据包，由于需要判断玩家是否能看见玩家，是否看得见其他玩家，并且worker和子服的实体id不一样就修改了一下。

# 性能

我没测试过香雪兰的性能，不过根据MrHua269的说法，带130人应该没什么问题，但是很多情况下ysm自己的缓存同步会出现内存泄漏等各种奇奇怪怪的问题导致模型同步炸掉。

# 路线图

目前还没有明确的路线图，毕竟我还没完全理解这玩意的代码，还需要一段时间熟悉。不过目前未来大概的方向是尝试做多worker的支持以及完成未完成的npc插件拓展。（可能会单开几个新项目）
