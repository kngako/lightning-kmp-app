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

package com.machankura.compose.ui.composable.widgets.nfc

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.compose.ui.composable.widgets.dialogs.ModalBottomSheet
import fr.acinq.phoenix.utils.extensions.findActivitySafe

@Composable
fun NfcReaderMonitor() {
    val context = LocalContext.current
    val state by NfcStateRepository.state.collectAsState(initial = null)
    val activity = context.findActivitySafe() ?: return

    val onDismiss = { activity.stopNfcReader() }

    when (state) {
        null -> {}
        is NfcState.Inactive -> {}
        is NfcState.EmulatingTag -> {}
        is NfcState.ShowReader -> {
            ModalBottomSheet(
                onDismiss = onDismiss,
                scrimAlpha = .5f,
                horizontalAlignment = Alignment.CenterHorizontally,
                internalPadding = PaddingValues(horizontal = 24.dp),
                dismissOnScrimClick = false,
            ) {
                Text(text = "NFC Reader", style = MaterialTheme.typography.headlineSmall)
//              TODO:  Spacer(Modifier.height(16.dp))
//                Image(
//                    painter = painterResource(id = R.drawable.ic_nfc),
//                    contentDescription = stringResource(R.string.nfc_button),
//                    modifier = Modifier.size(64.dp),
//                    colorFilter = ColorFilter.tint(MaterialTheme.colors.primary)
//                )
                Spacer(Modifier.height(16.dp))
//                Text(text = stringResource(R.string.nfc_reader_desc), style = MaterialTheme.typography.subtitle2)
                Spacer(Modifier.height(24.dp))
                FilledTonalButton(
//                  TODO:  icon = R.drawable.ic_cross,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}