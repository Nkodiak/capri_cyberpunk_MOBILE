package com.capri.companion.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.round

/**
 * Procesa audio Qwen conservando la posición entre bloques.
 */
object QwenVoiceEffects {

    fun apply(
    input: FloatArray,
    sampleRate: Int,
    speed: Float,
    robotic: Float
): FloatArray {
    val tempo = speed.takeIf { it.isFinite() }
        ?.coerceIn(1f, 1.2f) ?: 1.08f

    val processor = StreamProcessor(sampleRate, speed, robotic)
    val first = processor.process(input)
    val last = processor.finish()
    val result = first + last

    val expectedSize = (input.size / tempo).toInt()

    return if (result.size > expectedSize) {
        result.copyOf(expectedSize)
    } else {
        result
     }
    }

    class StreamProcessor(
        sampleRate: Int,
        speed: Float,
        robotic: Float
    ) {
        private val rate = sampleRate.also {
            require(it in 8000..96000) {
                "Frecuencia de audio inválida."
            }
        }

        private val tempo =
            speed.takeIf { it.isFinite() }?.coerceIn(1f, 1.2f) ?: 1.08f

        private val wet =
            robotic.takeIf { it.isFinite() }?.coerceIn(0f, .35f) ?: .12f

        private var inputPosition = 0.0
        private var outputIndex = 0L
        private var previousSample: Float? = null

        fun process(input: FloatArray): FloatArray {
            require(input.all { it.isFinite() })

            if (input.isEmpty()) return FloatArray(0)

            val source = if (previousSample != null) {
                FloatArray(input.size + 1).also {
                    it[0] = previousSample!!
                    input.copyInto(it, 1)
                }
            } else {
                input
            }

            val output = ArrayList<Float>(
                (source.size / tempo).toInt() + 2
            )

            while (inputPosition < source.size - 1) {
                val left = inputPosition.toInt()
                    .coerceIn(0, source.lastIndex)

                val right = (left + 1).coerceAtMost(source.lastIndex)
                val fraction = inputPosition - left

                val dry = source[left] +
                    (source[right] - source[left]) * fraction

                val metallic = dry * (
                    (1 - wet) +
                        wet * cos(2 * PI * 85 * outputIndex / rate)
                )

                val digital = if (wet > 0) {
                    metallic * .94 +
                        round(metallic * 2048) / 2048 * .06
                } else {
                    metallic
                }

                output.add(digital.toFloat().coerceIn(-1f, 1f))

                inputPosition += tempo
                outputIndex++
            }

            previousSample = source.last()
            inputPosition -= source.size - 1

            return output.toFloatArray()
        }

        fun finish(): FloatArray {
            val output = ArrayList<Float>(1)
            val last = previousSample ?: return FloatArray(0)

            while (inputPosition < 1.0) {
                val metallic = last * (
                    (1 - wet) +
                        wet * cos(2 * PI * 85 * outputIndex / rate)
                )

                val digital = if (wet > 0) {
                    metallic * .94 +
                        round(metallic * 2048) / 2048 * .06
                } else {
                    metallic
                }

                output.add(digital.toFloat().coerceIn(-1f, 1f))
                inputPosition += tempo
                outputIndex++
            }

            previousSample = null
            return output.toFloatArray()
        }
    }
}
