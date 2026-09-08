package com.mochistitch.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mochistitch.core.imaging.AlignmentMode
import com.mochistitch.core.imaging.MergeDirection
import com.mochistitch.core.imaging.PaddingColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MergeSettingsCard(
    direction: MergeDirection,
    onDirectionChange: (MergeDirection) -> Unit,
    alignmentMode: AlignmentMode,
    onAlignmentModeChange: (AlignmentMode) -> Unit,
    paddingColor: PaddingColor,
    onPaddingColorChange: (PaddingColor) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Stitch Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Direction section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Direction",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DirectionChip(
                        label = "Vertical",
                        sublabel = "Webtoon",
                        selected = direction == MergeDirection.VERTICAL,
                        onClick = { onDirectionChange(MergeDirection.VERTICAL) }
                    )
                    DirectionChip(
                        label = "Horizontal LTR",
                        sublabel = "Western comic",
                        selected = direction == MergeDirection.HORIZONTAL_LTR,
                        onClick = { onDirectionChange(MergeDirection.HORIZONTAL_LTR) }
                    )
                    DirectionChip(
                        label = "Horizontal RTL",
                        sublabel = "Manga",
                        selected = direction == MergeDirection.HORIZONTAL_RTL,
                        onClick = { onDirectionChange(MergeDirection.HORIZONTAL_RTL) }
                    )
                }
            }

            // Alignment section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Alignment",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlignmentChip(
                        label = "Scale to Fit",
                        selected = alignmentMode == AlignmentMode.RESIZE_PROPORTIONAL,
                        onClick = { onAlignmentModeChange(AlignmentMode.RESIZE_PROPORTIONAL) }
                    )
                    AlignmentChip(
                        label = "Center Crop",
                        selected = alignmentMode == AlignmentMode.CENTER_CROP,
                        onClick = { onAlignmentModeChange(AlignmentMode.CENTER_CROP) }
                    )
                    AlignmentChip(
                        label = "Padding",
                        selected = alignmentMode == AlignmentMode.PADDING,
                        onClick = { onAlignmentModeChange(AlignmentMode.PADDING) }
                    )
                }
            }

            // Padding color (only when padding mode selected)
            if (alignmentMode == AlignmentMode.PADDING) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Padding Color",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaddingColorChip(
                            label = "White",
                            color = androidx.compose.ui.graphics.Color.White,
                            selected = paddingColor == PaddingColor.WHITE,
                            onClick = { onPaddingColorChange(PaddingColor.WHITE) }
                        )
                        PaddingColorChip(
                            label = "Black",
                            color = androidx.compose.ui.graphics.Color.Black,
                            selected = paddingColor == PaddingColor.BLACK,
                            onClick = { onPaddingColorChange(PaddingColor.BLACK) }
                        )
                        PaddingColorChip(
                            label = "Transparent",
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            selected = paddingColor == PaddingColor.TRANSPARENT,
                            onClick = { onPaddingColorChange(PaddingColor.TRANSPARENT) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionChip(
    label: String,
    sublabel: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp, 6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlignmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp, 6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun PaddingColorChip(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (color == androidx.compose.ui.graphics.Color.Transparent) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        color
                    },
                    RoundedCornerShape(8.dp)
                )
                .then(
                    if (selected) Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) else Modifier
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (color == androidx.compose.ui.graphics.Color.Transparent) {
                // Diagonal lines pattern for transparent
                Row {
                    for (i in 0..2) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
