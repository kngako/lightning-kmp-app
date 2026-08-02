package fr.acinq.phoenix.compose.ui.composable.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun LoadingDataIndicator(
    modifier: Modifier = Modifier.fillMaxWidth(),
    color: Color = MaterialTheme.colorScheme.secondary,
    fillScreen: Boolean = true,
    text: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (fillScreen) {
            Spacer(modifier = Modifier.weight(1f))
        }

        CircularProgressIndicator(
            modifier = Modifier.width(80.dp).aspectRatio(1f),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        text?.let {

            Spacer(
                modifier = Modifier.height(30.dp)
            )

            Text(
                text = text,
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                textAlign = TextAlign.Center
            )
        }
        if (fillScreen) {
            Spacer(modifier = Modifier.weight(3f))
        }

    }
}
