/*
 * Copyright 2025 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.compose.ui.composable.widgets.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
fun Clickable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
    border: BorderStroke? = null,
    backgroundColor: Color = Color.Unspecified, // transparent by default!
    shape: Shape = RectangleShape,
    clickDescription: String = "",
    internalPadding: PaddingValues = PaddingValues(0.dp),
    indication: Indication? = LocalIndication.current,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val contentColor = LocalContentColor.current
    Surface(
        shape = shape,
        color = backgroundColor,
        border = border,
        contentColor = contentColor,
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = null,
                onDoubleClick = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = clickDescription,
                interactionSource = interactionSource,
                indication = indication
            )
            .padding(internalPadding)
    ) {
        content()
    }
}