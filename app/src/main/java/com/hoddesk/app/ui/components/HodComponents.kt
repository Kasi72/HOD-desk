package com.hoddesk.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hoddesk.app.ui.theme.Hod
import com.hoddesk.app.ui.theme.Squircles

/** Press feedback used instead of Material ripple: a soft dim, as on iOS. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this
        .alpha(if (pressed) 0.6f else 1f)
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 30.dp, iconTint: Color = Color.White) {
    Box(Modifier.size(size).clip(Squircles.icon).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.58f))
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Hod.type.footnote, color = Hod.colors.secondaryLabel, modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp))
}

@Composable
fun SectionFooter(text: String) {
    Text(text, style = Hod.type.footnote, color = Hod.colors.secondaryLabel, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
}

/** Inset-grouped section: optional header, a white squircle card, optional footer. */
@Composable
fun GroupedSection(header: String? = null, footer: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        header?.let { SectionHeader(it) }
        Column(Modifier.fillMaxWidth().clip(Squircles.card).background(Hod.colors.surface), content = content)
        footer?.let { SectionFooter(it) }
    }
}

@Composable
fun RowDivider(inset: Dp = 16.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(0.5.dp).background(Hod.colors.separator))
}

@Composable
fun Chevron() {
    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Hod.colors.chevron, modifier = Modifier.size(22.dp))
}

/** Settings-style row: icon tile, title/subtitle, trailing value, chevron or custom trailing content. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = Hod.colors.tint,
    value: String? = null,
    valueColor: Color = Hod.colors.secondaryLabel,
    titleColor: Color = Hod.colors.label,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) modifier.pressable(onClick = onClick) else modifier
    Row(
        rowModifier.fillMaxWidth().heightIn(min = if (subtitle != null) 58.dp else 48.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon?.let { IconTile(it, iconColor) }
        Column(Modifier.weight(1f)) {
            Text(title, style = Hod.type.body, color = titleColor)
            subtitle?.let { Text(it, style = Hod.type.footnote, color = Hod.colors.secondaryLabel) }
        }
        value?.let { Text(it, style = Hod.type.body, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
        trailing?.invoke(this)
        if (chevron) Chevron()
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null, icon: ImageVector? = null, iconColor: Color = Hod.colors.tint, enabled: Boolean = true) {
    ListRow(title = title, subtitle = subtitle, icon = icon, iconColor = iconColor) {
        IosSwitch(checked, onCheckedChange, enabled)
    }
}

@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Hod.colors.green,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Hod.colors.fill,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Tinted status pill. Always carries a word, never colour alone. */
@Composable
fun Pill(text: String, background: Color, textColor: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Hod.type.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = textColor,
        modifier = modifier.clip(Squircles.pill).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

enum class HodButtonStyle { Filled, Tinted, Gray, Destructive, DestructiveTinted }

@Composable
fun HodButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: HodButtonStyle = HodButtonStyle.Filled,
    height: Dp = 52.dp,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val c = Hod.colors
    val (bg, fg) = when (style) {
        HodButtonStyle.Filled -> c.tint to c.onTint
        HodButtonStyle.Tinted -> c.tintSoft to c.tint
        HodButtonStyle.Gray -> c.fill to c.label
        HodButtonStyle.Destructive -> c.red to Color.White
        HodButtonStyle.DestructiveTinted -> c.red.copy(alpha = 0.12f) to c.redText
    }
    val shape = if (height >= 44.dp) Squircles.button else Squircles.small
    Row(
        modifier
            .heightIn(min = height)
            .clip(shape)
            .background(bg)
            .alpha(if (enabled) 1f else 0.4f)
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let { Icon(it, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = if (height >= 44.dp) Hod.type.headline else Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = fg, textAlign = TextAlign.Center)
    }
}

/** iOS segmented control with a raised squircle thumb. */
@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(34.dp).clip(Squircles.small).background(Hod.colors.segmentTrack).padding(2.dp)) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (selected) Modifier.shadow(2.dp, Squircles.small).clip(Squircles.small).background(Hod.colors.surface) else Modifier)
                    .pressable(role = Role.Tab) { onSelect(index) }
                    .semantics { contentDescription = "$label${if (selected) ", selected" else ""}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Hod.type.footnote.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), color = Hod.colors.label, maxLines = 1)
            }
        }
    }
}

/** Large-title page header with optional overline and trailing control. */
@Composable
fun LargeTitleHeader(title: String, overline: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            overline?.let { Text(it.uppercase(), style = Hod.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = Hod.colors.secondaryLabel) }
            Text(title, style = Hod.type.largeTitle, color = Hod.colors.label)
        }
        trailing?.invoke(this)
    }
}

/** Navigation bar for pushed screens: "‹ Back" in tint, centred title, optional trailing action. */
@Composable
fun NavBar(backLabel: String, onBack: () -> Unit, title: String? = null, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
        Row(
            Modifier.align(Alignment.CenterStart).heightIn(min = 44.dp).pressable(onClick = onBack).padding(horizontal = 8.dp).semantics { contentDescription = "Back to $backLabel" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ArrowBackIosNew, null, tint = Hod.colors.tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(backLabel, style = Hod.type.body, color = Hod.colors.tint)
        }
        if (title != null) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = Hod.type.headline, color = Hod.colors.label)
            subtitle?.let { Text(it, style = Hod.type.caption, color = Hod.colors.secondaryLabel) }
        }
        trailing?.let { Box(Modifier.align(Alignment.CenterEnd)) { it() } }
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(38.dp).clip(Squircles.small).background(Hod.colors.fill).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.Search, null, tint = Hod.colors.secondaryLabel, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = Hod.type.body, color = Hod.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = Hod.type.body.copy(color = Hod.colors.label),
                cursorBrush = SolidColor(Hod.colors.tint),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) Icon(
            Icons.Rounded.Cancel, "Clear search", tint = Hod.colors.tertiaryLabel,
            modifier = Modifier.size(18.dp).pressable { onValueChange("") },
        )
    }
}

enum class BannerTone { Info, Attention, Blocked }

@Composable
fun Banner(title: String, message: String, tone: BannerTone = BannerTone.Info, actionLabel: String? = null, onAction: (() -> Unit)? = null, onDismiss: (() -> Unit)? = null) {
    val c = Hod.colors
    val (bg, fg) = when (tone) {
        BannerTone.Info -> c.tintSoft to c.label
        BannerTone.Attention -> c.orange.copy(alpha = 0.14f) to c.label
        BannerTone.Blocked -> c.red.copy(alpha = 0.10f) to c.label
    }
    Row(Modifier.fillMaxWidth().clip(Squircles.card).background(bg).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = fg)
            Text(message, style = Hod.type.footnote, color = c.secondaryLabel)
        }
        if (actionLabel != null && onAction != null) Text(actionLabel, style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.tint, modifier = Modifier.padding(start = 10.dp).pressable(onClick = onAction))
        if (onDismiss != null) Text("OK", style = Hod.type.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.tint, modifier = Modifier.padding(start = 12.dp).pressable(onClick = onDismiss))
    }
}

/** Source text with the phrases that support extracted fields highlighted. */
@Composable
fun EvidenceText(text: String, highlights: List<String>, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    val mark = Hod.colors.evidenceMark
    val markText = Hod.colors.evidenceText
    val annotated = remember(text, highlights) {
        val ranges = highlights.filter { it.isNotBlank() }.flatMap { phrase ->
            Regex(Regex.escape(phrase.trim()), RegexOption.IGNORE_CASE).findAll(text).map { it.range }.toList()
        }.sortedBy { it.first }
        buildAnnotatedString {
            var cursor = 0
            ranges.forEach { range ->
                if (range.first < cursor) return@forEach
                append(text.substring(cursor, range.first))
                withStyle(SpanStyle(background = mark, color = markText, fontWeight = FontWeight.Medium)) { append(text.substring(range.first, range.last + 1)) }
                cursor = range.last + 1
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }
    Text(annotated, style = Hod.type.callout, color = Hod.colors.label.copy(alpha = 0.86f), modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Diagonal hatching used for fixed clinical blocks (OPD, ward rounds, ECT). */
fun Modifier.hatched(base: Color, stripe: Color): Modifier = this.background(base).drawBehind {
    val step = 12.dp.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(stripe, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 3.dp.toPx())
        x += step
    }
}

/** Floating glass capsule tab bar with a squircle selection and a separate capture button. */
data class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon, val badge: Int = 0)

@Composable
fun FloatingTabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, onCapture: () -> Unit, captureIcon: ImageVector, modifier: Modifier = Modifier) {
    val c = Hod.colors
    Row(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.weight(1f).height(62.dp).shadow(14.dp, Squircles.card, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                .clip(Squircles.card).background(c.surface.copy(alpha = 0.96f)).border(0.5.dp, c.separator.copy(alpha = 0.6f), Squircles.card).padding(5.dp),
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(Squircles.button).background(if (isSelected) c.tintSoft else Color.Transparent)
                        .pressable(role = Role.Tab) { onSelect(index) }
                        .semantics { contentDescription = item.label + (if (item.badge > 0) ", ${item.badge} new" else "") + (if (isSelected) ", selected" else "") },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (isSelected) item.selectedIcon else item.icon, null, tint = if (isSelected) c.tint else c.secondaryLabel, modifier = Modifier.size(22.dp))
                        Text(item.label, style = Hod.type.tabLabel.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium), color = if (isSelected) c.tint else c.secondaryLabel, maxLines = 1)
                    }
                    if (item.badge > 0) Text(
                        if (item.badge > 99) "99+" else item.badge.toString(),
                        style = Hod.type.caption.copy(fontWeight = FontWeight.SemiBold), color = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp).clip(CircleShape).background(c.red).padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
        Box(
            Modifier.size(62.dp).shadow(14.dp, Squircles.button, spotColor = c.tint.copy(alpha = 0.4f)).clip(Squircles.button).background(c.tint)
                .pressable(onClick = onCapture).semantics { contentDescription = "Capture" },
            contentAlignment = Alignment.Center,
        ) { Icon(captureIcon, null, tint = c.onTint, modifier = Modifier.size(28.dp)) }
    }
}

/** iOS-style action sheet: message + grouped options + separate Cancel. Rendered by the caller inside a Dialog. */
data class SheetAction(val label: String, val destructive: Boolean = false, val emphasized: Boolean = false, val onClick: () -> Unit)

@Composable
fun ActionSheetContent(title: String?, message: String?, actions: List<SheetAction>, onCancel: () -> Unit) {
    val c = Hod.colors
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.fillMaxWidth().clip(Squircles.card).background(c.surface)) {
            if (title != null || message != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    title?.let { Text(it, style = Hod.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.secondaryLabel, textAlign = TextAlign.Center) }
                    message?.let { Text(it, style = Hod.type.footnote, color = c.secondaryLabel, textAlign = TextAlign.Center) }
                }
                RowDivider(0.dp)
            }
            actions.forEachIndexed { index, action ->
                if (index > 0) RowDivider(0.dp)
                Box(Modifier.fillMaxWidth().height(56.dp).pressable(onClick = action.onClick), contentAlignment = Alignment.Center) {
                    Text(action.label, style = Hod.type.body.copy(fontWeight = if (action.emphasized) FontWeight.SemiBold else FontWeight.Normal), color = if (action.destructive) c.redText else c.tint, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(56.dp).clip(Squircles.card).background(c.surface).pressable(onClick = onCancel), contentAlignment = Alignment.Center) {
            Text("Cancel", style = Hod.type.headline, color = c.tint)
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = Hod.type.headline, color = Hod.colors.label, textAlign = TextAlign.Center)
        Text(message, style = Hod.type.subheadline, color = Hod.colors.secondaryLabel, textAlign = TextAlign.Center)
    }
}

/** Screen scaffold for grouped-background pages. */
@Composable
fun GroupedPage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Hod.colors.groupedBackground), content = content)
}

val PageContentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp)

@Composable
fun CheckCircle(checked: Boolean, onToggle: () -> Unit, label: String) {
    val c = Hod.colors
    Box(Modifier.size(44.dp).pressable(role = Role.Checkbox, onClick = onToggle).semantics { contentDescription = "$label, ${if (checked) "done" else "not done"}" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            if (checked) {
                drawCircle(c.tint)
                val w = size.width
                drawLine(Color.White, Offset(w * 0.30f, w * 0.52f), Offset(w * 0.44f, w * 0.66f), strokeWidth = w * 0.09f)
                drawLine(Color.White, Offset(w * 0.44f, w * 0.66f), Offset(w * 0.72f, w * 0.36f), strokeWidth = w * 0.09f)
            } else drawCircle(c.chevron, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx()))
        }
    }
}

@Composable
fun AnimatedBanner(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible) { content() }
}

@Composable
fun Modifier.defaultRowHeight(): Modifier = this.defaultMinSize(minHeight = 48.dp)
