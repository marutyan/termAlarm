package com.marutyan.termalarm.domain

import kotlin.random.Random

// 書き写しゲームで使う文字種。紛らわしい0/Oや1/lは避けず単純に全大文字英数字とする（難易度調整はしない、docs/SPEC.md）
private const val TRANSCRIBE_CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

// 書き写しゲームの文字数（SPEC「ランダムな英数字8文字」）
private const val TRANSCRIBE_LENGTH = 8

// 順にタップゲームで並べる数の範囲（SPEC「ばらばらに並んだ1〜12」）
private const val SEQUENTIAL_TAP_MAX = 12

// 端末を振るゲームで要求する回数。難易度調整はせず1問固定にする（docs/SPEC.md「ゲームの実装方針」）
private const val SHAKE_REQUIRED_COUNT = 10

// 図形を数えるゲームで並べる図形の総数
private const val COUNT_SHAPES_TOTAL = 12

// 色と文字ゲーム（ストループ）で使う色名の一覧。文字の意味と文字色をこの中からずらして選ぶ
private val STROOP_COLOR_NAMES = listOf("赤", "青", "緑", "黄", "紫", "橙")

/**
 * 当日終了の前に挟むゲームの種類（docs/SPEC.md「ゲーム」）。出題のたびにこの中からランダムに1つ選ぶ。
 */
enum class GameType {
    ARITHMETIC, // 計算
    SEQUENTIAL_TAP, // 順にタップ
    TRANSCRIBE, // 書き写し
    SHAKE_DEVICE, // 端末を振る
    COUNT_SHAPES, // 図形を数える
    COLOR_WORD, // 色と文字（文字色を答える）
}

// 図形を数えるゲームで出す図形の種類
enum class ShapeKind { CIRCLE, SQUARE, TRIANGLE, STAR }

/**
 * 1問分のデータ。種類ごとに画面表示へ必要な情報だけを持ち、正誤判定は共通のcorrectAnswerとjudge()で行う。
 * UI（担当D）はtypeを見て対応する画面を出し、ユーザーの回答をjudge()へ渡す。
 */
sealed class GameQuestion(val type: GameType) {
    abstract val correctAnswer: String

    // 2桁の足し算・引き算。promptに演算式を持たせ、correctAnswerは計算結果の文字列
    data class Arithmetic(
        val left: Int,
        val right: Int,
        val isAddition: Boolean,
        override val correctAnswer: String,
    ) : GameQuestion(GameType.ARITHMETIC)

    // ばらばらに並んだ1〜12を昇順にタップさせる。shuffledNumbersが画面に並べる順、
    // correctAnswerはユーザーが昇順にタップした結果と同じ形式（カンマ区切りの昇順文字列）で比較する
    data class SequentialTap(
        val shuffledNumbers: List<Int>,
        override val correctAnswer: String,
    ) : GameQuestion(GameType.SEQUENTIAL_TAP)

    // ランダムな英数字8文字を見て入力させる。correctAnswerは表示した文字列そのもの
    data class Transcribe(val text: String) : GameQuestion(GameType.TRANSCRIBE) {
        override val correctAnswer: String get() = text
    }

    // 加速度センサーで規定回数振らせる。correctAnswerは必要な振動回数の文字列
    data class ShakeDevice(val requiredShakes: Int) : GameQuestion(GameType.SHAKE_DEVICE) {
        override val correctAnswer: String get() = requiredShakes.toString()
    }

    // 混在する図形からtargetの個数を数えさせる。correctAnswerは個数の文字列
    data class CountShapes(
        val shapes: List<ShapeKind>,
        val target: ShapeKind,
        override val correctAnswer: String,
    ) : GameQuestion(GameType.COUNT_SHAPES)

    // 文字の意味(word)と文字色(displayColor)が異なる語を見せ、文字色を選択肢から選ばせる
    data class ColorWord(
        val word: String,
        val displayColor: String,
        val choices: List<String>,
        override val correctAnswer: String,
    ) : GameQuestion(GameType.COLOR_WORD)
}

// 2桁の足し算・引き算を1問作る
private fun generateArithmetic(random: Random): GameQuestion.Arithmetic {
    val a = random.nextInt(10, 100)
    val b = random.nextInt(10, 100)
    val isAddition = random.nextBoolean()
    // 引き算では答えが負にならないよう大きい方を左に置く。回答用のテンキーに符号が無く、
    // 負の答えは入力する手段が無いため。寝起きに負の数を計算させる必要も無い。
    val left = if (isAddition || a >= b) a else b
    val right = if (isAddition || a >= b) b else a
    val answer = if (isAddition) left + right else left - right
    return GameQuestion.Arithmetic(left, right, isAddition, answer.toString())
}

// 1〜12をシャッフルして並べ、昇順タップの正解列（"1,2,...,12"）を持つ問題を作る
private fun generateSequentialTap(random: Random): GameQuestion.SequentialTap {
    val numbers = (1..SEQUENTIAL_TAP_MAX).toList()
    val shuffled = numbers.shuffled(random)
    return GameQuestion.SequentialTap(shuffled, numbers.joinToString(","))
}

// ランダムな英数字8文字の書き写し問題を作る
private fun generateTranscribe(random: Random): GameQuestion.Transcribe {
    val text = (1..TRANSCRIBE_LENGTH).map { TRANSCRIBE_CHARSET[random.nextInt(TRANSCRIBE_CHARSET.length)] }.joinToString("")
    return GameQuestion.Transcribe(text)
}

// 端末を振るゲームの問題を作る（回数は固定）
private fun generateShakeDevice(): GameQuestion.ShakeDevice = GameQuestion.ShakeDevice(SHAKE_REQUIRED_COUNT)

// 図形をランダムに並べ、その中から選んだ1種類の個数を数えさせる問題を作る
private fun generateCountShapes(random: Random): GameQuestion.CountShapes {
    val shapes = (1..COUNT_SHAPES_TOTAL).map { ShapeKind.entries[random.nextInt(ShapeKind.entries.size)] }
    val target = ShapeKind.entries[random.nextInt(ShapeKind.entries.size)]
    val count = shapes.count { it == target }
    return GameQuestion.CountShapes(shapes, target, count.toString())
}

// 文字の意味と文字色が異なる語を1問作る（ストループ課題）
private fun generateColorWord(random: Random): GameQuestion.ColorWord {
    val word = STROOP_COLOR_NAMES[random.nextInt(STROOP_COLOR_NAMES.size)]
    // 表示色は語の意味と必ず異なるものにする（同じだと文字色を問う意味がなくなるため）
    val displayColor = STROOP_COLOR_NAMES.filter { it != word }[random.nextInt(STROOP_COLOR_NAMES.size - 1)]
    return GameQuestion.ColorWord(word, displayColor, STROOP_COLOR_NAMES, displayColor)
}

/**
 * excludedTypesに含まれない種類からランダムに1つ選び、1問生成する。
 * 「端末を振る」のようにセンサーが無い端末では出題できない種類を、呼び出し側がexcludedTypesで除外する
 * （センサーの有無の判定自体はAndroid依存になるため呼び出し側の責任、docs/SPEC.md「ゲームの実装方針」）。
 * 乱数はテストで再現できるようRandomを引数で受け取る。
 */
fun generateGameQuestion(random: Random, excludedTypes: Set<GameType> = emptySet()): GameQuestion {
    val available = GameType.entries.filter { it !in excludedTypes }
    require(available.isNotEmpty()) { "excludedTypesによって出題できる種類がありません" }
    return when (available[random.nextInt(available.size)]) {
        GameType.ARITHMETIC -> generateArithmetic(random)
        GameType.SEQUENTIAL_TAP -> generateSequentialTap(random)
        GameType.TRANSCRIBE -> generateTranscribe(random)
        GameType.SHAKE_DEVICE -> generateShakeDevice()
        GameType.COUNT_SHAPES -> generateCountShapes(random)
        GameType.COLOR_WORD -> generateColorWord(random)
    }
}

/**
 * ユーザーの回答が正解か判定する。前後の空白を無視した完全一致で比較する。
 * 「順にタップ」はユーザーがタップした順序をcorrectAnswerと同じ形式（カンマ区切り）に整形して渡す前提。
 */
fun judgeGameAnswer(question: GameQuestion, answer: String): Boolean =
    answer.trim() == question.correctAnswer.trim()
