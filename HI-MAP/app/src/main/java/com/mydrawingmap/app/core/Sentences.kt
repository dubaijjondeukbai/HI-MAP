package com.mydrawingmap.app.core

/** 한글 조사 고르기. 마지막 글자에 받침이 있으면 [withFinal], 없으면 [withoutFinal]을 붙인다. */
object Josa {
    fun pick(word: String, withFinal: String, withoutFinal: String): String {
        if (word.isEmpty()) return withoutFinal
        val c = word[word.length - 1].code
        if (c < 0xAC00 || c > 0xD7A3) return withoutFinal
        return if ((c - 0xAC00) % 28 != 0) withFinal else withoutFinal
    }
}

/**
 * 아이에게 들려주는 문장. 언제나 같은 틀을 쓴다.
 * 예고 문장이 매번 달라지면 예측하기 어려워지므로 여기 한 곳에서만 만든다.
 */
object Sentences {
    fun summary(remaining: Int, destination: String): String =
        if (remaining > 0) {
            "그림 ${remaining}개를 지나면 $destination${Josa.pick(destination, "이에요", "예요")}."
        } else {
            "조금만 가면 $destination${Josa.pick(destination, "이에요", "예요")}."
        }

    fun depart(remaining: Int, destination: String): String = "출발해요. " + summary(remaining, destination)

    fun approaching(name: String): String = "곧 $name${Josa.pick(name, "이", "가")} 나와요."

    fun rerouted(remaining: Int, destination: String): String = "길이 바뀌었어요. " + summary(remaining, destination)

    fun arrived(destination: String): String = "${destination}에 도착했어요."
}
