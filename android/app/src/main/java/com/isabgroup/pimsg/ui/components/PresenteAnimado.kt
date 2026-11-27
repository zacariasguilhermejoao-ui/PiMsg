package com.isabgroup.pimsg.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PresenteAnimado(
    modifier: Modifier = Modifier,
    tamanho: Dp = 150.dp,
    aoTocar: ((Boolean) -> Unit)? = null
) {
    var aberto by remember { mutableStateOf(false) }

    val progressoAbertura by animateFloatAsState(
        targetValue = if (aberto) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "aberturaTampa"
    )

    val alphaBrilho by animateFloatAsState(
        targetValue = if (aberto) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "alphaBrilho"
    )

    val lilasCaixa = Color(0xFFA855F7)
    val lilasSombra = Color(0xFF7E22CE)
    val lilasTampa = Color(0xFFC084FC)
    val amareloFita = Color(0xFFFACC15)
    val amareloEscuro = Color(0xFFEAB308)
    val amareloClaro = Color(0xFFFDE047)

    Canvas(
        modifier = modifier
            .size(tamanho)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                aberto = !aberto
                aoTocar?.invoke(aberto)
            }
    ) {
        val escala = size.width / 200f

        // Brilhos que saem de dentro quando abre
        if (alphaBrilho > 0f) {
            val deslocamentoY = -28f * progressoAbertura * escala

            val estrela = Path().apply {
                val cx = 93f * escala
                val cy = (73f * escala) + deslocamentoY
                moveTo(cx, cy - 11f * escala)
                lineTo(cx + 3f * escala, cy - 3f * escala)
                lineTo(cx + 11f * escala, cy)
                lineTo(cx + 3f * escala, cy + 3f * escala)
                lineTo(cx, cy + 11f * escala)
                lineTo(cx - 3f * escala, cy + 3f * escala)
                lineTo(cx - 11f * escala, cy)
                lineTo(cx - 3f * escala, cy - 3f * escala)
                close()
            }
            drawPath(path = estrela, color = amareloFita.copy(alpha = alphaBrilho))

            drawCircle(
                color = lilasTampa.copy(alpha = alphaBrilho),
                radius = 4.5f * escala,
                center = Offset(115f * escala, (68f * escala) + deslocamentoY)
            )
            drawCircle(
                color = lilasTampa.copy(alpha = alphaBrilho),
                radius = 3f * escala,
                center = Offset(76f * escala, (78f * escala) + deslocamentoY)
            )
        }

        // Base da Caixa (Lilás)
        drawRoundRect(
            color = lilasCaixa,
            topLeft = Offset(50f * escala, 85f * escala),
            size = Size(100f * escala, 80f * escala),
            cornerRadius = CornerRadius(8f * escala, 8f * escala)
        )
        drawRect(
            color = lilasSombra.copy(alpha = 0.4f),
            topLeft = Offset(50f * escala, 85f * escala),
            size = Size(100f * escala, 10f * escala)
        )
        drawRect(
            color = amareloFita,
            topLeft = Offset(90f * escala, 85f * escala),
            size = Size(20f * escala, 80f * escala)
        )

        // Tampa e Laço (Animados ao tocar)
        translate(
            left = -18f * progressoAbertura * escala,
            top = -38f * progressoAbertura * escala
        ) {
            rotate(
                degrees = -22f * progressoAbertura,
                pivot = Offset(45f * escala, 85f * escala)
            ) {
                val lacoEsquerdo = Path().apply {
                    moveTo(100f * escala, 65f * escala)
                    cubicTo(
                        75f * escala, 35f * escala,
                        55f * escala, 55f * escala,
                        85f * escala, 65f * escala
                    )
                    close()
                }
                drawPath(path = lacoEsquerdo, color = amareloEscuro)

                val lacoDireito = Path().apply {
                    moveTo(100f * escala, 65f * escala)
                    cubicTo(
                        125f * escala, 35f * escala,
                        145f * escala, 55f * escala,
                        115f * escala, 65f * escala
                    )
                    close()
                }
                drawPath(path = lacoDireito, color = amareloEscuro)

                drawRoundRect(
                    color = amareloClaro,
                    topLeft = Offset(93f * escala, 57f * escala),
                    size = Size(14f * escala, 10f * escala),
                    cornerRadius = CornerRadius(4f * escala, 4f * escala)
                )

                drawRoundRect(
                    color = lilasTampa,
                    topLeft = Offset(42f * escala, 65f * escala),
                    size = Size(116f * escala, 22f * escala),
                    cornerRadius = CornerRadius(6f * escala, 6f * escala)
                )

                drawRect(
                    color = amareloFita,
                    topLeft = Offset(90f * escala, 65f * escala),
                    size = Size(20f * escala, 22f * escala)
                )
            }
        }
    }
}
