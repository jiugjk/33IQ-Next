# 🧩 33IQ Next

**简体中文** | [English](README.en.md)

[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.x-blue.svg)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-8.x-blue?style=flat)](https://developer.android.com/studio/releases/gradle-plugin)
[![Gradle](https://img.shields.io/badge/Gradle-9.x-blue?style=flat)](https://gradle.org)

[33IQ](https://www.33iq.com)（智力题 / 推理题社区）的**非官方第三方 Android 客户端**，仅用于个人学习与技术研究。

33IQ 没有公开的第三方 API，本应用访问的是移动浏览器同样能拿到的服务端渲染页面（使用 [Jsoup](https://jsoup.org/) 解析），以及官方 App 自身在用的少量 JSON 接口。为了让这种逆向方式的边界保持透明，仓库里的代码注释与下文都会说明哪些行为是被真实验证过的、哪些只是尽力而为。

> **免责声明**：本项目与 33IQ 无任何隶属、认可或合作关系。题目内容、图片与账号数据均归 33IQ 及其用户/作者所有。请勿用本应用进行大规模抓取、绕过付费内容或二次分发 —— 参见[项目边界与限制](#项目边界与限制)。

## 目录

- [应用截图](#应用截图)
- [功能范围](#功能范围)
- [数据是怎么来的](#数据是怎么来的)
- [已验证 vs 尽力而为](#已验证-vs-尽力而为)
- [技术栈](#技术栈)
- [架构](#架构)
- [Gradle 配置](#gradle-配置)
- [代码校验](#代码校验)
- [项目边界与限制](#项目边界与限制)
- [快速开始](#快速开始)
- [构建与签名](#构建与签名)
- [路线图](#路线图)
- [致谢](#致谢)
- [许可证](#许可证)

## 应用截图

| 题库 Feed | 库 · 历史记录 |
|---|---|
| <img src="docs/screenshots/feed.jpg" width="280" alt="题库列表" /> | <img src="docs/screenshots/library-history.jpg" width="280" alt="历史记录" /> |

## 功能范围

- **题库 Feed** —— 按分类浏览题目（侦探推理 / 逻辑思维 / 脑筋急转弯 / 知识百科 / …），无限滚动加载
- **搜索** —— 基于 33IQ 自身搜索页的关键词搜索
- **题目详情** —— 标题、标签、作者、统计数据、选项（若有）、解析、评论数
- **答题** —— 提交选择题或开放式作答，由服务器真实判分（对/错 + 学识增减均以服务器返回为准）
- **查看提示 / 解析** —— 走 33IQ 自己的付费流程：先取价 → 明确确认 → 需要时付费 → 显示内容
- **词库题** —— 服务器返回候选字块时使用点选式作答界面
- **收藏与历史** —— 完全本地（Room）的收藏夹与答题历史，可按答对/答错/仅看解析筛选，无需登录即可使用
- **登录** —— 通过 33IQ 自身的登录接口登录，会话 Cookie 持久化保存
- **每日签到** —— 存在已验证会话时，启动应用自动完成当天签到（按 Asia/Shanghai 日历日，每天一次）
- **设置** —— 会话/账号状态、浅色/深色/跟随系统主题、开源许可、免责声明

## 数据是怎么来的

33IQ 并未面向第三方客户端公开任何文档化的 API。本项目最初完全依靠**公开的服务端渲染 HTML**（页面源码、内嵌脚本和站点自身的 AJAX 请求）来获取数据；后来一位贡献者提供了官方 33IQ Android App 真实流量的抓包分析包，才确认了一个仅靠猜测无法得知的细节：**同一个 URL**，在带上正确的查询参数时会返回结构清晰的 **JSON**，而不是网页 HTML。

出于对 33IQ 的尊重，本 README 不再罗列具体的接口地址与字段名；它们只保留在代码与代码注释里。客户端明确处理的两个站点特性值得一提：

- 站点 HTML 以 **GBK**（而非 UTF-8）编码返回，网络层会按站点实际编码解码请求与响应。
- 看起来像自动化的请求可能被重定向到登录墙，客户端会识别这种情况并抛出明确的异常，而不是把登录页错当成内容解析。

那份抓包资料是**做过隐私脱敏**的结构分析包（只有请求/响应结构与字段名，没有头部与正文的真实值），因此其中出现、但当前功能用不到的部分被记入[路线图](#路线图)而非直接接入。数据层被隔离在仓库接口之后，后续若有更精确的实现，可以替换而不动 UI。

## 已验证 vs 尽力而为

为了对可靠性保持诚实：

| 能力 | 状态 |
|---|---|
| 浏览题目、标签页、搜索 | ✅ 基于真实响应验证 |
| 题目详情（标题/标签/作者/选项/点赞/评论与收藏数/正确率） | ✅ 真实 JSON 接口与字段名经抓包确认，并用真实请求复验 |
| 登录字段与成功响应 | ✅ 由真实的成功登录抓包确认 |
| 登录失败判定 | ⚠️ 从未抓到失败登录，错误状态串仍是推测；无法验证的登录状态一律报告为「未验证」，绝不当成成功 |
| 提交答案 | ✅ 选择题已实测：答对/答错/重复作答是三种可区分的真实响应，带真实学识增减。开放式题目的提交仍未确认 |
| 查看解析 | ✅ 实现为「取价 → 确认 → 按需付费 → 显示」的独立流程，文本与图片原生渲染。⚠️ 自动化测试使用 mock，验证过程中没有发生真实消费 |
| 查看提示 | ✅ 实测可用，含 33IQ 自己给出的普通/会员/终身会员价格，原样展示而非客户端重算 |
| 评论列表 | ❌ 未加载：抓包中没有评论列表接口，界面只显示评论数并说明这一点 |
| 词库题 | ✅ 由匿名请求确认；候选块保留服务器顺序与重复项，元数据不完整时禁用提交而不是退回自由输入 |
| 已答标记 / 隐藏已答 | ✅ 按账号本地保存答对/答错/重复及「已看解析」限制。⚠️ 没有可用的服务器历史接口，更早或其他设备上的记录属于**未知**，不等于未作答 |
| 翻页 | ⚠️ 优先使用列表自身的下一页链接，必要时回退到旧的页码协议；空页或全重复页会停止翻页。新一轮实时验证仍受站点安全校验阻挡 |
| 服务器端「收藏」 | ❌ 未实现：抓包中没有服务器收藏接口，因此收藏是完整可用的**本地**书签 |
| 搜索 | ⚠️ 仅 HTML 抓取；开发后期该路径对重复自动请求返回登录墙/反爬响应，未能在新会话下复测，属尽力而为 |
| 每日签到 | ⚠️ 地址与字段由抓包确认，但抓包只断言了 HTTP 200，成功状态串未确认；客户端把非错误响应视为完成，每个 Asia/Shanghai 日历日最多一次 |

### 列表刷新与本地进度

- 下拉刷新重载第一页；重新进入或切换分类同样从第一页开始；从后台返回不会自动替换列表。
- 上滑加载使用站点自身的下一页链接，只与当前列表去重。出现可见新内容会重置自动扫描预算，因此正常浏览不会被固定页数限制；全重复/全隐藏的扫描有上限并提供「继续」操作。
- 「隐藏已答/已看解析」是持久化的设备偏好；被隐藏的卡片仍保留在当前批次里，关掉开关即可重新出现。
- 答对、答错、重复提交都确认「已作答」；「已看解析」是**独立**的另一种限制（看过解析无法再答题），两者可能同时存在。网络错误、无法识别的响应与答题次数限制都不会确认任何一种状态。本地只保存题目 ID 与偏好，不保存提交过的答案内容。
- 已验证登录使用服务器 UID 做账号隔离；没有 UID 的历史会话使用独立的匿名命名空间，之后的登录不会把它错误地并入某个账号。网页端/其他设备/旧版本的历史不会被导入，本地缺记录会在详情页标为「未知」。

### 安全地查看解析

- 「查看提示」与「查看解析」是两个独立操作；打开或取消价格弹窗都不会付费或获取答案。即使报价为 0 也需要确认，因为查看解析会使该题无法再作答并影响错题统计。
- 报价中的价格原样展示，不做客户端硬编码或按会员身份换算；付费前会重新取价，条款变化需要重新确认。
- 任何付费/显示请求发出**之前**，会按账号+题目同步落盘一个 pending 标记。网络超时、异常响应与取消都会保留它（进程重启后依然有效）；恢复流程只重新请求「显示」，绝不重复调用付费接口，无法恢复时引导用户去网页端核对，而不是再买一次。
- 付费动作的请求体是一次性的，并禁用传输层重试与重定向，避免 POST 被透明重放；仓库级锁与 ViewModel 守卫防止重复确认与并发的答题/提示流程。

## 技术栈

**核心：**
- **[Kotlin 2.x](https://kotlinlang.org/)** —— Coroutines、Flow、KSP、Serialization
- **[Jsoup](https://jsoup.org/)** —— HTML 解析（33IQ 无公开 API，仅在确认过的地方使用官方 App 的 JSON 接口）

**Android Jetpack：**
- **[Compose](https://developer.android.com/jetpack/compose)** + **[Navigation Compose](https://developer.android.com/jetpack/compose/navigation)**（类型安全路由）
- **[ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel)**、**[Room](https://developer.android.com/jetpack/androidx/releases/room)**（本地收藏与历史）、Core Splashscreen

**网络与图片：**
- **[OkHttp](https://square.github.io/okhttp/)** —— 带持久化 Cookie Jar，保持会话
- **[Coil 3](https://github.com/coil-kt/coil)** —— 图片加载（复用同一 OkHttp 栈）

**依赖注入：** **[Koin](https://insert-koin.io/)**

**架构：** Clean Architecture（每个模块自带 Presentation/Domain/Data 层）+ Single Activity + MVVM/MVI

**代码质量：** Konsist（架构与约定测试）、Ktlint、Detekt、Spotless

## 架构

项目保留了所 fork 骨架的模块化 Clean Architecture（源自 [android-showcase](https://github.com/igorwojda/android-showcase)），把数据源从音乐 API 换成了 33IQ。

### 模块划分

- **`app`** —— 导航图、DI 装配、主题
- **`feature-feed`** —— 题目列表 / 搜索 / 详情（「题库」体验）
- **`feature-favourite`** —— 本地收藏与历史（Room）
- **`feature-auth`** —— 登录界面
- **`feature-settings`** —— 主题、会话/账号、许可、免责声明
- **`feature-base`** —— 各 feature 共用的 `BaseViewModel`/`Result`/组件
- **`library-network`** —— 33IQ 的 HTTP/解析层（编码感知的客户端、Cookie 持久化、会话管理、每日签到）
- **`library-test-utils`** —— 共用测试工具

```
feature-feed ──▶ feature-favourite
     │                  │
     ├──▶ library-network            (feature-settings、feature-auth 同样依赖)
     └──▶ feature-base ◀── (所有 feature 模块)
app ──▶ feature-feed, feature-favourite, feature-auth, feature-settings, library-network
```

### Feature 模块内部结构

**Presentation 层**：`MVVM` + `MVI`，ViewModel 暴露单一的不可变 UI State 流，`Action` 把当前状态归约为下一个状态；`@Composable` 只负责渲染状态并把用户意图回传。

**Domain 层**：独立于 Data/Presentation，`UseCase` 承载业务逻辑，`Repository` 接口让领域层与「数据具体怎么拿」解耦。

**Data 层**：`feature-feed` 的列表与搜索来自 HTML 解析，详情来自官方 App 面向的 JSON，远程数据源为两者构造正确的请求并把解析放到调用方线程之外；`feature-favourite` 的数据源是本地 Room 数据库。

### `library/network`

所有与 `www.33iq.com` 通信相关的代码都集中在这里，与 UI 完全隔离：编码感知的 HTTP 客户端与登录墙检测、跨重启保持会话的持久化 Cookie Jar，以及「我是否已登录」的唯一事实来源 —— 会话状态由站点自身的访客探测结果分类为已登录 / 访客 / 未知（HTTP 200 或仅仅持有 Cookie 都不算登录证明），探测失败则保持上一次已验证的状态不变。

## Gradle 配置

- **依赖管理**：使用 Gradle [version catalog](gradle/libs.versions.toml) 统一管理各模块依赖版本。
- **约定插件**：[convention plugins](build-logic/src/main/kotlin/com/jiugjk/iq33/buildlogic) 统一 `application`/`feature`/`library`/`kotlin`/`test`/`detekt`/`spotless` 等构建配置，让各模块的 `build.gradle.kts` 保持精简。
- **类型安全的项目引用**：

```kotlin
implementation(projects.feature.feed)
implementation(projects.library.network)
```

## 代码校验

```bash
./gradlew konsist-test:test --rerun-tasks           # 架构与约定校验
./gradlew lintDebug                                 # Android Lint
./gradlew detektCheck                               # 复杂度与风格检查
./gradlew spotlessCheck                             # 格式校验
./gradlew testDebugUnitTest -x konsist-test:test    # 单元测试
./gradlew :app:bundleDebug                          # 产物构建验证
```

> **说明**：开发环境中已安装 Android SDK + JDK 17 工具链，上述命令（以及 `:app:assembleDebug`）都真实跑过，暴露出的编译/lint/detekt/格式问题均已就地修复。原模板里「每个类都必须有对应单测」的 Konsist 门禁已移除（本 fork 不追求 1:1 覆盖，详见对应文件注释）。CI（`.github/workflows/check.yml`）在每次推送时运行同样的检查。

## 项目边界与限制

- **与 33IQ 无关联。** 这是个人学习性质的逆向练习，不是内容再分发，也不是商业产品。
- **本就没有可用的公开 API。** 数据层依赖解析 33IQ 自己的公开页面，哪些经过验证、哪些是推测见上文表格。
- **付费内容会消耗你账号里真实的学识。** 查看解析或提示会调用 33IQ 自己的付费接口，真实扣除当前登录账号的学识 —— 本客户端不会绕过这套货币体系，只是给官方同样的付费动作提供了界面。
- **请善待 33IQ 的服务器。** 本客户端发出的请求与移动浏览器同量级，请不要改造它来高频抓取。

## 快速开始

**前置条件：** Android Studio、JDK 17+、Android SDK（compileSdk 36）。

```bash
git clone https://github.com/jiugjk/33IQ-Next.git
# 在 Android Studio 中打开：File -> Open -> 选择克隆下来的目录
```

浏览题目不需要任何 API Key 或配置，应用直接访问 `https://www.33iq.com`。登录使用你真实的 33IQ 账号，凭据直接发送给 33iq.com 自己的服务器（登录页内有对应说明）。

## 构建与签名

两个 CI 工作流（`build.yml` 手动或在特性分支运行，`check.yml` 在 PR 和推送到 `main` 时运行）都会同时构建 `:app:assembleDebug` 与 `:app:assembleRelease`，并把 APK 作为产物上传。每次都构建 release，是为了让只在该构建类型生效的 R8 与资源压缩持续受检，而不是等到真正需要发布时才第一次踩坑。

签名材料从环境读取，绝不会存进本仓库。配置以下四个仓库 Secret 即可让 CI 对产物签名：

| Secret | 内容 |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | keystore 本体的 base64（`base64 -w0 upload-keystore.jks`） |
| `SIGNING_KEYSTORE_PASSWORD` | keystore 密码 |
| `SIGNING_KEY_ALIAS` | keystore 内的 key 别名 |
| `SIGNING_KEY_PASSWORD` | 该 key 的密码 |

工作流会在任务期间把 keystore 解码到 runner 的临时目录，两种构建类型共用这把 key，因此后续 CI 产物可以互相覆盖安装，而不会因签名不一致被拒绝。

缺少这些 Secret 也不会失败：fork 的 PR（GitHub 不会下发 Secret）或全新克隆照样能构建 —— debug 使用 AGP 生成的临时 key，release 产出 `app-release-unsigned.apk`。

本地可以把同样的四个值写进 `~/.gradle/gradle.properties`（`signingKeystoreFile`、`signingKeystorePassword`、`signingKeyAlias`、`signingKeyPassword`，此处是路径而非 base64），或导出为 `SIGNING_KEYSTORE_FILE`、`SIGNING_KEYSTORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD` 环境变量。

## 路线图

- 在真实账号下验证服务器端「收藏」与评论发表/评论列表能力，并接到已有的仓库接口后面
- 在可用的实时会话下复验翻页行为：优先使用列表自身广告的同站链接，不可用时回退旧的页码协议，全重复响应停止继续请求
- 找到只读的历史答题状态来源，补足本地的已答标记
- 接入抓包中出现但当前功能尚未使用的能力（通知数、签到历史、关注动态更新等）
- 确认登录失败时的错误状态串（目前只抓到过成功登录），并补全账号学识值的展示
- 在用户明确同意下于真机验证解析查看流程；绝不假设「答对过」或「看过」就意味着免费，一律以服务器报价为准
- 确认开放式（非选择题）答案提交（所有抓到的提交样本都是选择题）
- 海龟汤类猜谜玩法、考场/竞技模式 —— 尚未建模

## 致谢

模块化 Clean Architecture 骨架（build-logic 约定插件、基础 ViewModel/状态模式、Konsist 规则）改编自 Igor Wojda 的 [android-showcase](https://github.com/igorwojda/android-showcase)（MIT 许可）。

上文提到的真实 JSON 详情接口、正确的登录字段以及访客检测能力，来自项目维护者贡献的官方 33IQ Android App 真实流量抓包；后续一次真实登录会话（答题、买提示、看解析）的抓包确认了答题与付费显示流程，以及真实的登录成功响应结构。

## 许可证

```
MIT License

Copyright (c) 2025 Igor Wojda (original android-showcase skeleton)
Copyright (c) 2026 33IQ Next contributors (33IQ-specific code)

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial
portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

本项目与 33IQ 无任何隶属关系。所有题目内容、商标与账号数据均归 33IQ 及其各自所有者所有。
