package com.animecliplabs.clipmark.ui.common

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.animecliplabs.clipmark.AppContainer
import com.animecliplabs.clipmark.R
import com.animecliplabs.clipmark.core.analysis.Readability
import com.animecliplabs.clipmark.core.analysis.ReadabilityResolver
import com.animecliplabs.clipmark.core.model.Anchor
import com.animecliplabs.clipmark.core.model.SizeLevel
import com.animecliplabs.clipmark.core.model.WatermarkStyle
import com.animecliplabs.clipmark.image.WatermarkRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Draws the watermark with the export renderer, scaled to this composable's size. */
@Composable
fun WatermarkOverlay(
    style: WatermarkStyle,
    smartAnchor: Anchor?,
    readability: Readability,
    renderer: WatermarkRenderer,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val placement = renderer.place(size.width, size.height, style, smartAnchor) ?: return@Canvas
        drawIntoCanvas { renderer.draw(it.nativeCanvas, placement, style, readability) }
    }
}

/** The sample artwork with a style applied — used for preset previews. */
@Composable
fun SamplePreview(style: WatermarkStyle, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val bitmap = SampleArt.bitmap
    val image = remember { bitmap.asImageBitmap() }
    val decision = remember(style) { container.analyzer.decide(bitmap, style) }
    Box(modifier.aspectRatio(bitmap.width / bitmap.height.toFloat()).clip(MaterialTheme.shapes.medium)) {
        Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        WatermarkOverlay(
            style = style,
            smartAnchor = decision?.smartAnchor,
            readability = decision?.readability ?: ReadabilityResolver.resolve(style, null),
            renderer = container.renderer,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun Thumbnail(uri: Uri?, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val container = LocalAppContainer.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<Bitmap?>(initialValue = uri?.let { container.imageLoader.cachedThumbnail(it, px) }, uri, px) {
        if (value == null && uri != null) value = withContext(Dispatchers.IO) { container.imageLoader.thumbnail(uri, px) }
    }
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (uri == null) {
            Image(SampleArt.bitmap.asImageBitmap(), contentDescription, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun MissingImage(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A full-row switch: the whole row is the touch target, and it reads as one control to TalkBack. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun anchorLabel(anchor: Anchor): String = stringResource(
    when (anchor) {
        Anchor.TOP_LEFT -> R.string.anchor_top_left
        Anchor.TOP_CENTER -> R.string.anchor_top_center
        Anchor.TOP_RIGHT -> R.string.anchor_top_right
        Anchor.CENTER_LEFT -> R.string.anchor_center_left
        Anchor.CENTER -> R.string.anchor_center
        Anchor.CENTER_RIGHT -> R.string.anchor_center_right
        Anchor.BOTTOM_LEFT -> R.string.anchor_bottom_left
        Anchor.BOTTOM_CENTER -> R.string.anchor_bottom_center
        Anchor.BOTTOM_RIGHT -> R.string.anchor_bottom_right
    },
)

@Composable
fun sizeLabel(level: SizeLevel): String = stringResource(
    when (level) {
        SizeLevel.EXTRA_SMALL -> R.string.size_xs
        SizeLevel.SMALL -> R.string.size_s
        SizeLevel.MEDIUM -> R.string.size_m
        SizeLevel.LARGE -> R.string.size_l
    },
)

/** "Bottom right · Small · 80%" (or "Smart corner · …"). */
@Composable
fun styleSummary(style: WatermarkStyle): String {
    val position = when {
        style.customCenter != null -> stringResource(R.string.position_custom)
        style.smartCorner -> stringResource(R.string.position_smart)
        else -> anchorLabel(style.anchor)
    }
    return listOf(position, sizeLabel(style.sizeLevel), "${(style.opacity * 100).roundToInt()}%").joinToString(" · ")
}

@Composable
fun ColorDot(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(modifier.size(12.dp).clip(CircleShape).background(color))
}

@Composable
fun HSpacer(width: Dp) = Spacer(Modifier.width(width))
