package com.example.ghostai

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.ghostai.ui.theme.GhostAITheme

@Composable
fun MissingOpenAiKeyDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API Keys Required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.open_api_key_dialog_message),
                )
                Text(
                    stringResource(R.string.open_api_key_dialog_message_elevenlabs),
                )

                Spacer(Modifier.height(8.dp))

                val annotatedText = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        append("Get your keys here:\n\n")
                    }
                    pushStringAnnotation("openai", "https://platform.openai.com/account/api-keys")
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append("• OpenAI")
                    }
                    pop()

                    append("\n\n")

                    pushStringAnnotation("eleven", "https://elevenlabs.io")
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append("• ElevenLabs")
                    }
                    pop()
                }

                ClickableText(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Start),
                    onClick = { offset ->
                        annotatedText
                            .getStringAnnotations(start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                openUrl(annotation.item)
                            }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}

@Preview
@Composable
fun MissingOpenAiKeyDialogPreview() {
    GhostAITheme {
        MissingOpenAiKeyDialog(onDismiss = {})
    }
}
