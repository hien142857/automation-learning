package vn.startupbench

/** Conservative UI hints, not a universal ad classifier. Never clicks any node. */
object AdSignals {
    private val labels = setOf("skip ad", "skip ads", "skip advertisement", "close ad", "close advertisement",
        "advertisement", "quảng cáo", "bỏ qua quảng cáo", "đóng quảng cáo", "rewarded ad")
    private val ids = setOf("ad_close", "ad_close_button", "close_ad", "skip_ad", "ad_skip_button",
        "mbridge_iv_close", "mbridge_reward_close", "tt_video_ad_close", "exo_ad_overlay")
    fun detect(text: String, description: String, resourceId: String, custom: String): String? {
        val values = listOf(text, description).map { it.trim().lowercase(java.util.Locale.ROOT) }
        if (custom.isNotBlank() && listOf(text, description).any { it.trim() == custom.trim() }) return "configured_blocker"
        if (values.any { it in labels || Regex("^(skip ads?|bỏ qua quảng cáo) [0-9]+.*$").matches(it) }) return "ad_label"
        if (resourceId.substringAfterLast('/').lowercase(java.util.Locale.ROOT) in ids) return "ad_resource_id"
        return null
    }
}
