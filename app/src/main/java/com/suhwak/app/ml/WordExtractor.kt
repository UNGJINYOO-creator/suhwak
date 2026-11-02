package com.suhwak.app.ml

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Runs on-device OCR (Korean + Latin) on a photo and pairs headwords with their Korean meanings. */
object WordExtractor {
    @Volatile private var dictionary: Set<String>? = null

    /** Raw OCR lines of the last photo, shown on the scan screen to diagnose missed words. */
    @Volatile var lastLines: List<OcrLine> = emptyList()
        private set

    /** The English spelling list (assets/words.txt). */
    suspend fun dictionary(context: Context): Set<String> =
        dictionary ?: withContext(Dispatchers.IO) {
            context.assets.open("words.txt").bufferedReader().useLines { it.toHashSet() }
        }.also { dictionary = it }

    private fun isWord(dict: Set<String>, w: String) = SpellFix.isWord(dict, w)

    /** Size of the last photo as the recognizer saw it (after rotation), for cutting word strips out of it. */
    @Volatile var lastSize: Pair<Int, Int> = 0 to 0
        private set

    suspend fun extract(context: Context, uri: Uri): PairResult {
        val dict = dictionary(context)
        val image = InputImage.fromFilePath(context, uri)
        lastSize = image.width to image.height
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        val lines = try {
            recognizer.process(image).await().textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    OcrLine(line.text, box.left, box.top, box.right, box.bottom, line.confidence)
                }
            }
        } finally {
            recognizer.close()
        }
        lastLines = lines.sortedWith(compareBy({ it.top }, { it.left }))
        return VocabPairer(correct = { SpellFix.fix(dict, it) }) { isWord(dict, it) }.pairWithDropped(lines)
    }
}
