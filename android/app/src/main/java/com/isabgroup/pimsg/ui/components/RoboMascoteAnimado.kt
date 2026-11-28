package com.isabgroup.pimsg.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun RoboMascoteAnimado(
    estaEscrevendo: Boolean,
    modifier: Modifier = Modifier,
    progressoTexto: Float = 0.5f,
    tamanho: Dp = 150.dp,
    idleTrigger: Int = 0
) {
    var idleMovement by remember { mutableStateOf(false) }
    LaunchedEffect(idleTrigger) {
        if (idleTrigger > 0) {
            idleMovement = true
            delay(1400)
            idleMovement = false
        }
    }
    val idleProgress by animateFloatAsState(
        targetValue = if (idleMovement) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "movimentoParado"
    )

    // 1. Transição suave ao começar ou parar de escrever
    val estadoEscrevendo by animateFloatAsState(
        targetValue = if (estaEscrevendo) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "estadoEscrevendo"
    )

    // 2. Animação contínua da risada e pulso das antenas enquanto escreve
    val transicaoInfinita = rememberInfiniteTransition(label = "risadaInfinita")
    val cicloRisada by transicaoInfinita.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cicloRisada"
    )

    val risadaAtiva = if (estaEscrevendo) cicloRisada * estadoEscrevendo else 0f

    // Paleta de cores fiel ao robô da imagem (Azul vibrante e Ciano neon)
    val azulEscuro = Color(0xFF0A66B2)
    val azulMedio = Color(0xFF0E7AD1)
    val azulClaro = Color(0xFF1CB0F6)
    val azulCabeca = Color(0xFF22B8FC)
    val visorFundo = Color(0xFF0E63A8)
    val visorBorda = Color(0xFFA5F3FC)
    val cianoBrilho = Color(0xFF80F8FF)
    val cianoBordaAntena = Color(0xFF00C2FF)

    Canvas(modifier = modifier.size(tamanho)) {
        val escala = size.width / 200f

        // Base / Pescoço arredondado inferior (fica mais estático enquanto a cabeça mexe)
        drawArc(
            color = azulClaro,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(66f * escala, 136f * escala),
            size = Size(68f * escala, 48f * escala)
        )
        // Sombra do pescoço logo abaixo do queixo
        drawArc(
            color = azulEscuro,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(66f * escala, 136f * escala),
            size = Size(68f * escala, 26f * escala)
        )

        // Movimento da cabeça: olha para baixo e vibra rindo ao escrever
        val deslocamentoCabecaY = (4.5f * estadoEscrevendo - 2.2f * risadaAtiva - 4.5f * idleProgress) * escala
        val inclinacaoCabeca = 3f * estadoEscrevendo * ((progressoTexto.coerceIn(0f, 1f) - 0.5f) * 2f) + 3.5f * idleProgress

        translate(top = deslocamentoCabecaY) {
            rotate(degrees = inclinacaoCabeca, pivot = Offset(100f * escala, 108f * escala)) {

                // 1. Orelhas / Fones laterais
                drawArc(
                    color = azulMedio,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(30f * escala, 86f * escala),
                    size = Size(26f * escala, 44f * escala)
                )
                drawArc(
                    color = azulMedio,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(144f * escala, 86f * escala),
                    size = Size(26f * escala, 44f * escala)
                )

                // 2. Hastes curvas das duas antenas
                val hasteEsquerda = Path().apply {
                    moveTo(64f * escala, 66f * escala)
                    quadraticTo(58f * escala, 52f * escala, 48f * escala, 42f * escala)
                }
                drawPath(
                    path = hasteEsquerda,
                    color = azulEscuro,
                    style = Stroke(width = 7f * escala, cap = StrokeCap.Round)
                )

                val hasteDireita = Path().apply {
                    moveTo(136f * escala, 66f * escala)
                    quadraticTo(142f * escala, 52f * escala, 152f * escala, 42f * escala)
                }
                drawPath(
                    path = hasteDireita,
                    color = azulEscuro,
                    style = Stroke(width = 7f * escala, cap = StrokeCap.Round)
                )

                // Pontas circulares brilhantes das antenas
                val raioExternoAntena = (10f + 1.5f * risadaAtiva) * escala
                val raioInternoAntena = (5.5f + 1f * risadaAtiva) * escala

                // Antena esquerda
                drawCircle(
                    color = cianoBordaAntena,
                    radius = raioExternoAntena,
                    center = Offset(46f * escala, 40f * escala)
                )
                drawCircle(
                    color = cianoBrilho,
                    radius = raioInternoAntena,
                    center = Offset(46f * escala, 40f * escala)
                )

                // Antena direita
                drawCircle(
                    color = cianoBordaAntena,
                    radius = raioExternoAntena,
                    center = Offset(154f * escala, 40f * escala)
                )
                drawCircle(
                    color = cianoBrilho,
                    radius = raioInternoAntena,
                    center = Offset(154f * escala, 40f * escala)
                )

                // 3. Cabeça principal (Oval azul claro)
                drawOval(
                    color = azulCabeca,
                    topLeft = Offset(40f * escala, 58f * escala),
                    size = Size(120f * escala, 100f * escala)
                )

                // Reflexo superior na testa da cabeça
                drawArc(
                    color = visorBorda.copy(alpha = 0.7f),
                    startAngle = -115f,
                    sweepAngle = 50f,
                    useCenter = false,
                    topLeft = Offset(58f * escala, 65f * escala),
                    size = Size(84f * escala, 34f * escala),
                    style = Stroke(width = 5f * escala, cap = StrokeCap.Round)
                )

                // 4. Visor / Rosto (Oval azul escuro com borda luminosa)
                drawOval(
                    color = visorFundo,
                    topLeft = Offset(52f * escala, 76f * escala),
                    size = Size(96f * escala, 72f * escala)
                )
                drawOval(
                    color = visorBorda,
                    topLeft = Offset(52f * escala, 76f * escala),
                    size = Size(96f * escala, 72f * escala),
                    style = Stroke(width = 2.2f * escala)
                )

                // 5. Olhos e Boca (Olham para baixo e riem ao escrever)
                val olharX = (progressoTexto.coerceIn(0f, 1f) - 0.5f) * 8f * estadoEscrevendo * escala
                val olharY = (5.5f * estadoEscrevendo - 1.5f * risadaAtiva) * escala

                translate(left = olharX, top = olharY) {
                    // Halo de brilho suave ao redor dos olhos
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(cianoBrilho.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(78f * escala, 108f * escala),
                            radius = 18f * escala
                        ),
                        radius = 18f * escala,
                        center = Offset(78f * escala, 108f * escala)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(cianoBrilho.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(122f * escala, 108f * escala),
                            radius = 18f * escala
                        ),
                        radius = 18f * escala,
                        center = Offset(122f * escala, 108f * escala)
                    )

                    // Olho esquerdo (formato semicircular fofo igual à imagem)
                    val olhoEsquerdo = Path().apply {
                        moveTo(68f * escala, 112f * escala)
                        cubicTo(
                            68f * escala, 98f * escala,
                            88f * escala, 98f * escala,
                            88f * escala, 112f * escala
                        )
                        quadraticTo(
                            78f * escala,
                            (108f - 4f * estadoEscrevendo) * escala,
                            68f * escala,
                            112f * escala
                        )
                        close()
                    }
                    drawPath(path = olhoEsquerdo, color = cianoBrilho)

                    // Olho direito
                    val olhoDireito = Path().apply {
                        moveTo(112f * escala, 112f * escala)
                        cubicTo(
                            112f * escala, 98f * escala,
                            132f * escala, 98f * escala,
                            132f * escala, 112f * escala
                        )
                        quadraticTo(
                            122f * escala,
                            (108f - 4f * estadoEscrevendo) * escala,
                            112f * escala,
                            112f * escala
                        )
                        close()
                    }
                    drawPath(path = olhoDireito, color = cianoBrilho)

                    // Boca: Sorriso simples parado -> Abre e dá risada ao escrever
                    if (estadoEscrevendo < 0.2f) {
                        val sorrisoParado = Path().apply {
                            moveTo(91f * escala, 125f * escala)
                            quadraticTo(100f * escala, 133f * escala, 109f * escala, 125f * escala)
                        }
                        drawPath(
                            path = sorrisoParado,
                            color = cianoBrilho,
                            style = Stroke(width = 3.2f * escala, cap = StrokeCap.Round)
                        )
                    } else {
                        val aberturaBoca = (8f * estadoEscrevendo + 5f * risadaAtiva) * escala
                        val bocaRindo = Path().apply {
                            moveTo(89f * escala, 123f * escala)
                            lineTo(111f * escala, 123f * escala)
                            quadraticTo(
                                100f * escala,
                                123f * escala + aberturaBoca * 1.6f,
                                89f * escala,
                                123f * escala
                            )
                            close()
                        }
                        drawPath(path = bocaRindo, color = cianoBrilho)
                    }
                }
            }
        }
    }
}
