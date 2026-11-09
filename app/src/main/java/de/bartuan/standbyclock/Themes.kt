package de.bartuan.standbyclock

/** clock: 0 thin, 1 neon, 2 analog, 3 bold, 4 minimal, 5 huge rounded, 6 dot-matrix
 *  bg: 0 black, 1 dark gradient, 2 dot grid, 3 soft wallpaper gradient */
data class Theme(
    val name: String, val clock: Int, val bg: Int, val fg: Int?, val sec: Int, val font: String,
    val cards: Boolean = false, val glyph: Boolean = false, val red: Boolean = false,
    val mono: Boolean = false, val numerals: Boolean = false, val fixedAccent: Int? = null, val group: String = "Basis"
)

object Themes {
    private const val G = 0xFFAAAAAA.toInt(); private const val S = 0xFF6E6E6E.toInt()
    const val NRED = 0xFFD71921.toInt()
    val ALL = listOf(
        Theme("Klassisch", 0, 0, G, S, "sans-serif-light"),
        Theme("Neon", 1, 0, null, S, "sans-serif-light"),
        Theme("Analog", 2, 0, G, S, "sans-serif-light"),
        Theme("Groß", 3, 0, null, S, "sans-serif-medium"),
        Theme("Minimal", 4, 0, 0xFF787878.toInt(), 0xFF505050.toInt(), "sans-serif-light"),
        Theme("StandBy Groß", 5, 1, 0xFFD8D8DC.toInt(), 0xFF8E8E9A.toInt(), "sans-serif-medium", group = "Apple-Stil"),
        Theme("StandBy Nacht", 5, 0, 0xFF9E1414.toInt(), 0xFF6A0E0E.toInt(), "sans-serif-medium", red = true, group = "Apple-Stil"),
        Theme("StandBy Glas", 5, 3, 0xFFE6E6EA.toInt(), 0xFFA8A8B4.toInt(), "sans-serif-medium", cards = true, group = "Apple-Stil"),
        Theme("StandBy Analog", 2, 1, 0xFFD0D0D4.toInt(), 0xFF8E8E9A.toInt(), "sans-serif-medium", cards = true, numerals = true, group = "Apple-Stil"),
        Theme("Nothing Dot", 6, 2, 0xFFE0E0E0.toInt(), 0xFF8A8A8A.toInt(), "monospace", mono = true, fixedAccent = NRED, group = "Nothing-Stil"),
        Theme("Nothing Glyph", 6, 0, 0xFFD0D0D0.toInt(), 0xFF7A7A7A.toInt(), "monospace", glyph = true, mono = true, fixedAccent = NRED, group = "Nothing-Stil"),
        Theme("Nothing Mono", 6, 0, 0xFF9A9A9A.toInt(), 0xFF5E5E5E.toInt(), "monospace", mono = true, fixedAccent = 0xFFBBBBBB.toInt(), group = "Nothing-Stil"),
    )
    /** theme-specific default positions (used until the user moves a widget) */
    fun def(t: Theme, w: String): Pos {
        if (t.clock == 5) when (w) {
            W.CLOCK -> return Pos(0.56f, 0.4f, 0.8f)
        }
        return W.DEF[w]!!
    }
    fun get(i: Int) = ALL[i.coerceIn(0, ALL.size - 1)]

    /** 5x7 dot-matrix glyphs (own design) */
    val DOTS: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to arrayOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to arrayOf(".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."),
        '4' to arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to arrayOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to arrayOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        ':' to arrayOf(".", ".", "#", ".", "#", ".", "."),
    )
}
