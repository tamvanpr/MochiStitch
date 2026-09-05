package com.mochistitch.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Merge Options",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Direction",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = direction == MergeDirection.VERTICAL,
                    onClick = { onDirectionChange(MergeDirection.VERTICAL) },
                    label = { Text("Vertical") }
                )
                FilterChip(
                    selected = direction == MergeDirection.HORIZONTAL_LTR,
                    onClick = { onDirectionChange(MergeDirection.HORIZONTAL_LTR) },
                    label = { Text("Horizontal (LTR)") }
                )
                FilterChip(
                    selected = direction == MergeDirection.HORIZONTAL_RTL,
                    onClick = { onDirectionChange(MergeDirection.HORIZONTAL_RTL) },
                    label = { Text("Horizontal (RTL)") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Alignment Mode",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = alignmentMode == AlignmentMode.RESIZE_PROPORTIONAL,
                    onClick = { onAlignmentModeChange(AlignmentMode.RESIZE_PROPORTIONAL) },
                    label = { Text("Resize Proportional") }
                )
                FilterChip(
                    selected = alignmentMode == AlignmentMode.CENTER_CROP,
                    onClick = { onAlignmentModeChange(AlignmentMode.CENTER_CROP) },
                    label = { Text("Center Crop") }
                )
                FilterChip(
                    selected = alignmentMode == AlignmentMode.PADDING,
                    onClick = { onAlignmentModeChange(AlignmentMode.PADDING) },
                    label = { Text("Letterbox / Padding") }
                )
            }

            if (alignmentMode == AlignmentMode.PADDING) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Padding Color",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = paddingColor == PaddingColor.WHITE,
                        onClick = { onPaddingColorChange(PaddingColor.WHITE) },
                        label = { Text("White") }
                    )
                    FilterChip(
                        selected = paddingColor == PaddingColor.BLACK,
                        onClick = { onPaddingColorChange(PaddingColor.BLACK) },
                        label = { Text("Black") }
                    )
                    FilterChip(
                        selected = paddingColor == PaddingColor.TRANSPARENT,
                        onClick = { onPaddingColorChange(PaddingColor.TRANSPARENT) },
                        label = { Text("Transparent") }
                    )
                }
            }
        }
    }
}
