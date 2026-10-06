package com.vibiate.launcher.data

import android.content.pm.ApplicationInfo

/**
 * Categorizes an app first via [ApplicationInfo.category] (API 26+), falling back to
 * package-name keyword heuristics for CATEGORY_UNDEFINED.
 */
object CategoryClassifier {

    fun classify(info: ApplicationInfo): AppCategory {
        when (info.category) {
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
            ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.MUSIC
            ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.ENTERTAINMENT
            ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.PHOTOGRAPHY
            ApplicationInfo.CATEGORY_NEWS -> return AppCategory.NEWS
            ApplicationInfo.CATEGORY_MAPS -> return AppCategory.TRANSIT
        }
        return heuristic(info.packageName)
    }

    // Order matters: more specific keywords are checked first.
    private val heuristics: List<Pair<AppCategory, List<String>>> = listOf(
        AppCategory.SOCIAL to listOf(
            "facebook", "instagram", "twitter", "whatsapp", "telegram", "snapchat",
            "tiktok", "reddit", "discord", "signal", "messenger", "linkedin",
            "mastodon", "threads", "bluesky", "wechat", "viber", "pinterest", ".line."
        ),
        AppCategory.GAMES to listOf(
            "game", "minecraft", "roblox", "supercell", "clash", "candy", "pokemon",
            "playrix", "zynga", ".ea.", "activision", "epicgames", "riotgames", "hoyoverse"
        ),
        AppCategory.MUSIC to listOf(
            "spotify", "music", "soundcloud", "pandora", "tidal", "deezer",
            "shazam", "bandcamp", "audiomack", "podcast"
        ),
        AppCategory.TRANSIT to listOf(
            "maps", "waze", "uber", "lyft", "transit", "moovit", "citymapper",
            "flight", "airline", "train", "navigation", "gasbuddy"
        ),
        AppCategory.PHOTOGRAPHY to listOf(
            "camera", "photo", "gallery", "snapseed", "lightroom", "picsart",
            "vsco", "canva", "b612"
        ),
        AppCategory.NEWS to listOf(
            "news", "feedly", "flipboard", "nytimes", "bbc", "cnn", "guardian",
            "reuters", "pressreader", "smartnews"
        ),
        AppCategory.ENTERTAINMENT to listOf(
            "youtube", "netflix", "twitch", "hulu", "disney", "primevideo", "plex",
            "vlc", "video", "crunchyroll", "hbo", "max.", "player", "movie", "imdb"
        ),
        AppCategory.PRODUCTIVITY to listOf(
            "docs", "sheets", "slides", "office", "word", "excel", "powerpoint",
            "notion", "evernote", "todoist", "keep", "notes", "calendar", "mail",
            "outlook", "slack", "drive", "dropbox", "trello", "asana", "teams",
            "zoom", "meet", "editor", "pdf", "sheet", "translate"
        ),
        AppCategory.UTILITIES to listOf(
            "calculator", "clock", "flashlight", "files", "settings", "tool",
            "cleaner", "antivirus", "vpn", "wifi", "scanner", "weather",
            "keyboard", "compass", "recorder", "authenticator", "password"
        ),
    )

    private fun heuristic(packageName: String): AppCategory {
        val p = packageName.lowercase()
        for ((category, keywords) in heuristics) {
            if (keywords.any { p.contains(it) }) return category
        }
        return AppCategory.OTHER
    }
}
