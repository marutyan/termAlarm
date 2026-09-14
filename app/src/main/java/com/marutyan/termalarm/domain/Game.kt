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

// 光った順を再現ゲームで選ぶ数字の個数。短期記憶として負荷が高すぎず寝起きに集中を要する5つとする。
private const val SEQUENCE_RECALL_COUNT = 5

// 神経衰弱ゲームでそろえるペアの総数。3×4の12枚を6種類の絵柄2枚ずつで構成するために用いる。
private const val MEMORY_PAIRS_COUNT = 6

// 歩くゲームで要求する歩数。ベッドから起きて歩行を促すために既定で30歩を要求する。
private const val WALK_REQUIRED_STEPS = 30

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
    MIRROR_TEXT, // 鏡文字（反転表示された英数字を入力する）
    SEQUENCE_RECALL, // 光った順を再現（点灯順と同じ順にタップする）
    MEMORY_PAIRS, // 神経衰弱（3×4の伏せ札から同じ絵柄のペアをすべてそろえる）
    WALK, // 歩く（端末を持って規定歩数歩く）
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

    /**
     * 左右反転して表示された英数字8文字を読み取って入力させる問題データ。
     * 表示用の文字列そのものを正解として保持し、画面側の描画で反転させる。
     */
    data class MirrorText(val text: String) : GameQuestion(GameType.MIRROR_TEXT) {
        override val correctAnswer: String get() = text
    }

    /**
     * 1〜9の中から重複なしで選んだ5つの並びを記憶させ、同じ順にタップさせる問題データ。
     * 順番のリストを保持し、画面が点灯させた順と同じカンマ区切りの文字列を正解として判定に用いる。
     */
    data class SequenceRecall(
        val sequence: List<Int>,
        override val correctAnswer: String,
    ) : GameQuestion(GameType.SEQUENCE_RECALL)

    /**
     * 3×4の12枚（6種類の絵柄各2枚）から全ペアをそろえさせる神経衰弱の問題データ。
     * 0〜5の絵柄の並びを保持し、全ペアをそろえたことを示す文字列"6"を正解として判定に用いる。
     */
    data class MemoryPairs(
        val cards: List<Int>,
        override val correctAnswer: String,
    ) : GameQuestion(GameType.MEMORY_PAIRS)

    /**
     * 端末を持って規定の歩数だけ歩かせる問題データ。
     * 達成に必要な歩数を保持し、その歩数と同じ数値文字列を正解として判定に用いる。
     */
    data class Walk(val requiredSteps: Int) : GameQuestion(GameType.WALK) {
        override val correctAnswer: String get() = requiredSteps.toString()
    }
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
 * 左右反転表示用の英数字8文字による鏡文字ゲームの問題を生成する。
 * 表示用の文字列を作成し、正解もその文字列自身とした問題データを返す。
 */
private fun generateMirrorText(random: Random): GameQuestion.MirrorText {
    val text = (1..TRANSCRIBE_LENGTH).map { TRANSCRIBE_CHARSET[random.nextInt(TRANSCRIBE_CHARSET.length)] }.joinToString("")
    return GameQuestion.MirrorText(text)
}

/**
 * 1〜9から重複なしで5つ選んだ並びを記憶・再現させるゲームの問題を生成する。
 * 選んだ5つの数列を保持し、順序通りにカンマ区切りで結合した文字列を正解として返す。
 */
private fun generateSequenceRecall(random: Random): GameQuestion.SequenceRecall {
    val sequence = (1..9).toList().shuffled(random).take(SEQUENCE_RECALL_COUNT)
    return GameQuestion.SequenceRecall(sequence, sequence.joinToString(","))
}

/**
 * 3×4の12枚（6種類の絵柄各2枚）を並べた神経衰弱ゲームの問題を生成する。
 * 0〜5の絵柄番号を2枚ずつシャッフルしたリストを持ち、全ペア数である"6"を正解として返す。
 */
private fun generateMemoryPairs(random: Random): GameQuestion.MemoryPairs {
    val cards = (0 until MEMORY_PAIRS_COUNT).flatMap { listOf(it, it) }.shuffled(random)
    return GameQuestion.MemoryPairs(cards, MEMORY_PAIRS_COUNT.toString())
}

/**
 * 規定の歩数歩行させるゲームの問題を生成する。
 * 起床とベッドからの脱出を促すため、既定歩数（30歩）を要求する問題データを返す。
 */
private fun generateWalk(): GameQuestion.Walk = GameQuestion.Walk(WALK_REQUIRED_STEPS)

/**
 * allowedTypesの中からランダムに1つ選び、1問生成する。
 * 設定で有効にした種類や端末のセンサー有無に合わせて出題可能な種類から1問を取り出すために用いる。
 * 乱数はテストで再現できるようRandomを引数で受け取る。
 */
fun generateGameQuestion(random: Random, allowedTypes: Set<GameType>): GameQuestion {
    require(allowedTypes.isNotEmpty()) { "allowedTypesが空のため出題できる種類がありません" }
    val candidates = allowedTypes.toList()
    return when (candidates[random.nextInt(candidates.size)]) {
        GameType.ARITHMETIC -> generateArithmetic(random)
        GameType.SEQUENTIAL_TAP -> generateSequentialTap(random)
        GameType.TRANSCRIBE -> generateTranscribe(random)
        GameType.SHAKE_DEVICE -> generateShakeDevice()
        GameType.COUNT_SHAPES -> generateCountShapes(random)
        GameType.COLOR_WORD -> generateColorWord(random)
        GameType.MIRROR_TEXT -> generateMirrorText(random)
        GameType.SEQUENCE_RECALL -> generateSequenceRecall(random)
        GameType.MEMORY_PAIRS -> generateMemoryPairs(random)
        GameType.WALK -> generateWalk()
    }
}

/**
 * ユーザーの回答が正解か判定する。前後の空白を無視した完全一致で比較する。
 * 「順にタップ」はユーザーがタップした順序をcorrectAnswerと同じ形式（カンマ区切り）に整形して渡す前提。
 */
fun judgeGameAnswer(question: GameQuestion, answer: String): Boolean =
    answer.trim() == question.correctAnswer.trim()
