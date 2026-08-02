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

package fr.acinq.phoenix.compose.ui.composable.widgets.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.acinq.phoenix.compose.ui.composable.widgets.buttons.Clickable
import fr.acinq.phoenix.compose.ui.composable.widgets.dialogs.ModalBottomSheet

object WalletAvatars {
    val list = listOf(
        "😃", "🙃", "🤑", "😎", "🥳", "🧐", "🤠", "🤖",
        "👑", "🚀", "✈️", "🚣", "⛵", "🚗", "🏍️",
        "🧘", "⛹️", "🤾", "🚴", "🧗", "🏋️", "🤼", "🏌️", "🏇", "🤺", "⛷️", "🏂", "🏄", "🏊", "🥷", "💂", "🤵", "🤵‍♀️", "🧑‍🚀", "👷", "👮", "🧑‍🔬", "🧑‍🔧", "🧑‍🚒", "🧑‍🌾", "🧑‍🎓", "🧑‍⚖️", "👶", "🧒", "🧑", "🧓", "👧", "👩", "👵",
        "🐶", "🐱", "🐴", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐤", "🦄", "🐝", "🐙", "🐬", "🐋", "🦜", "🐟", "🐛",
        "🍏", "🍎", "🍌", "🍇", "🍓", "🍺", "🍿", "🥖", "🧀", "🍔",
        "🌹", "🌻", "☘️", "❄️", "⛰️", "🌴", "🌳", "🌲", "⚡", "🌧️", "🌩️", "🌦️", "☀️",
        "🎂", "🎁", "🎈", "🎉", "🎃", "🎄", "🎀",
        "🔥", "💫", "⭐", "✨", "💰", "💸", "📈", "🎯", "♥️"
    )
}

@Composable
fun WalletAvatar(avatar: String, fontSize: TextUnit = 28.sp, borderColor: Color = Color.Transparent, backgroundColor: Color = MaterialTheme.colorScheme.surface, internalPadding: PaddingValues = PaddingValues(10.dp)) {
    Box(modifier = Modifier.clip(CircleShape).background(backgroundColor).border(1.dp, color = borderColor, shape = CircleShape).padding(internalPadding), contentAlignment = Alignment.Center) {
        Text(
            text = avatar,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ColumnScope.AvatarPicker(
    avatar: String,
    onAvatarChange: (String) -> Unit,
) {
    var showPickerDialog by remember { mutableStateOf(false) }


    Clickable(onClick = { showPickerDialog = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {


        WalletAvatar(avatar, fontSize = 48.sp, internalPadding = PaddingValues(16.dp))
    }

    if (showPickerDialog) {
        ModalBottomSheet(
            onDismiss = { showPickerDialog = false },
            horizontalAlignment = Alignment.CenterHorizontally,
            internalPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
            isContentScrollable = false,
        ) {
//            val s = stringResource(Res.string.wallet_edit_pick_avatar)
            val s = "Edit avatar"
            Text(s, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(24.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(WalletAvatars.list) { emoji ->
                    Clickable(onClick = {
                        showPickerDialog = false
                        onAvatarChange(emoji)
                    }) {
                        val mutedBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        WalletAvatar(emoji, backgroundColor = if (emoji == avatar) mutedBgColor else Color.Transparent)
                    }
                }
            }
        }
    }
}