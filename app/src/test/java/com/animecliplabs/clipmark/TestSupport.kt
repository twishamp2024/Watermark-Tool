package com.animecliplabs.clipmark

import com.animecliplabs.clipmark.core.layout.TextMeasurer
import com.animecliplabs.clipmark.core.layout.TextMetrics

/** Deterministic stand-in for Android's Paint: glyphs are 0.55 × size wide. Linear, like real text. */
val FakeMeasurer = TextMeasurer { text, size -> TextMetrics(width = text.length * size * 0.55f, ascent = size * 0.8f, descent = size * 0.2f) }

const val EPS = 0.01f
