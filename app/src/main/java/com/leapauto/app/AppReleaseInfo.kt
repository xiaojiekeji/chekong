package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 自动更新通道改为 GitHub 仓库 xiaojiekeji/chekong 的 Releases：优先读取仓库内 update.json（raw CDN，不走 API 限流），失败自动回落到 GitHub API\n2. 安装包下载增加一次自动重试与字节数完整性校验，弱网下不再一次失败就跳浏览器\n3. 仓库或版本被作者删除（HTTP 404）时提示「更新源已被作者删除」，不崩不误弹；仓库未发布版本时按已是最新\n4. 检查更新文案去掉蒲公英字样；「代码仓库」入口指向 https://github.com/xiaojiekeji/chekong"
}
