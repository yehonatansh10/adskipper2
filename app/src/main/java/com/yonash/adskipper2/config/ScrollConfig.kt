package com.yonash.adskipper2.config

data class ScrollConfig(
    val startHeightRatio: Float = 0.6f,
    val endHeightRatio: Float = 0.4f,
    val duration: Long = 100,
    val cooldown: Long = 2000L
)