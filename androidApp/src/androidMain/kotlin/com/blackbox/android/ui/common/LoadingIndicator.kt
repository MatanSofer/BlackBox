package com.blackbox.android.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.blackbox.android.ui.theme.BlackBoxTheme
import com.blackbox.android.ui.theme.Dimens

/**
 * Centered circular loading indicator.
 *
 * Uses the secondary (amber) color to match the BlackBox accent theme.
 * Fills available space and centers the spinner.
 *
 * @param modifier Optional [Modifier] for the container.
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Dimens.LoadingSize),
            color = MaterialTheme.colorScheme.secondary,
            strokeWidth = Dimens.SpacingXs,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingIndicatorPreview() {
    BlackBoxTheme {
        LoadingIndicator()
    }
}

@Preview(showBackground = true)
@Composable
private fun LoadingIndicatorLightPreview() {
    BlackBoxTheme(darkTheme = false) {
        LoadingIndicator()
    }
}
