## 矢车菊素
打破ysm混淆反调试以及对协议库封锁的限制!

## 这是什么?
在很早很早之前有过很多用户向ysm提出过请求制作插件版但并没有同意,于是我便尝试自己做了起来,终于在盯了将近一周的oOoo0oO0OOO0o0oO0O后写出了第一代在插件服上的ysm协议支持但是由于
其对nms的较强依赖性使得其很难拓展和移植,于是在一个星期后我又写出了MyYsm用于解决这一问题.但是,ysm将这一切毁灭殆尽,胡乱使用的协议库API,将几乎整个缓存同步写入native的操作使得了解ysm
的协议库变得几乎不可能,于是MyYsm也进入了生命的末端

但是,在我重新考虑新的工作方式后以及加上其他腐竹们的支持, Cyanidin作为MyYsm的下一世代便诞生了

## 它是如何工作的?
Cyanidin并非是一个整体,而是由一组模组/插件负责着不同功能组成进而实现在插件服跑ysm
整个系统工作方式类似MultiPaper,系统分为三部分:
 - 负责转发和处理ysm数据包的Velocity部分
 - 负责进行模型同步和生成部分数据诸如EntityData的worker后端(Fabric)
 - 玩家游玩的安装了插件的子服

其中,Velocity端只会拦截少量数据包(4, 51 ,52),数据包由于实体id在后端和worker不一致外加需要多版本mc的兼容,这里会在velocity侧对实体id进行重映射并在需要时重新编码/解码存放ysm实体数据的nbt,
而worker用于处理tracker事件以保证安装了ysm的玩家之间可以看到对方的模型,worker端不会进行大部分的游戏刻,只会简单的处理ysm的数据包和放置映射对话的玩家,并且在这上面生成缓存同步用的数据包然后通过velocity转发给玩家,
或是生成实体同步的数据包以及管理模型等杂项功能,由此可见这种工作方式对ysm基本做到了0介入而且并不会因为ysm的各种奇怪爆改而暴毙,而且实现起来极其方便

## 性能
整套项目组已经在BakaMC使用,除去前期因为项目不成熟造成的性能问题以及ysm自身问题以外可以稳定承载130名玩家且worker端CPU几乎无占用

## 构建
你需要准备好Java22
克隆项目及其构建指令(第三行)如下:
```shell
git clone https://github.com/MyYsm/Cyanidin.git
cd Cyanidin
./gradlew build
```
构建成功后可在各自目录的build/libs下找到jar

## 鸣谢
 - Molean(Helping solve some technical issues)
 - BakaMC
 - MartijnMuijsers
 - MiskaDaeve
 - hexadecimal233

## Bstats
![bStats](https://bstats.org/signatures/velocity/Cyanidin.svg "bStats")