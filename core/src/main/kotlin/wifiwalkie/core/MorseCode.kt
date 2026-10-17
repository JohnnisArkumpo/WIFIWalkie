package wifiwalkie.core

/** International Morse mappings for letters, digits and the punctuation used by this app. */
object MorseCode {
    private val codes = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
        'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
        'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
        'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---",
        '3' to "...--", '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
        '8' to "---..", '9' to "----.", '.' to ".-.-.-", ',' to "--..--", '?' to "..--..",
        '/' to "-..-.", '=' to "-...-", '-' to "-....-"
    )
    private val characters = codes.entries.associate { (character, pattern) -> pattern to character }

    fun patternFor(char: Char): String? = codes[char.uppercaseChar()]
    fun charFor(pattern: String): Char? = characters[pattern]
}
