# 浏览器依赖

页面优先从 jsDelivr CDN 加载依赖；CDN 请求失败时，通过同步脚本回退加载此目录中的相同版本，保持 Vue → Vue Router → Axios → 应用脚本的顺序。无需 npm 或 Node 构建。

| 依赖 | 版本 | 发行文件 | 许可证 |
|---|---|---|---|
| Vue | 3.5.17 | vue.global.prod.js | LICENSE-vue.txt（MIT） |
| Vue Router | 4.5.1 | vue-router.global.prod.js | LICENSE-vue-router.txt（MIT） |
| Axios | 1.10.0 | axios.min.js | LICENSE-axios.txt（MIT） |

这些文件为各上游包的浏览器发行版本。升级时应同时更新 index.html 的 CDN 版本和本目录的对应文件，并保留许可证。
