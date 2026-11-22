package com.leapauto.app

object AppReleaseInfo {
    val currentVersion: String
        get() = BuildConfig.VERSION_NAME

    // Update this text together with apkVersionName before each delivery package.
    const val currentReleaseNotes = "1. 「软件与关于」去掉「支持作者」入口及相关页面，只保留当前版本、开源代码仓库、退出账号\n2. 关闭 Chucker 的常驻状态栏通知（不再常驻「Recording HTTP activity / 200 GET」），HTTP 记录仍可在应用内面板查看\n3. 自动更新继续走 chekong 仓库的 update.json + GitHub Releases，下载失败自动重试一次"
}
