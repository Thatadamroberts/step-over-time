package com.stepquest.app.art

/** Per-region colours for the battle diorama and grading. */
class RegionPalette(
    val skyTop: Int, val skyBottom: Int, val far: Int, val mid: Int, val ground: Int, val groundAlt: Int,
    val trunk: Int, val foliage: Int, val foliageLight: Int, val rock: Int, val accent: Int,
    val light: Int, val fog: Int, val props: List<String>, val fireflies: Boolean = false,
) {
    companion object {
        private fun p(vararg c: String, props: List<String>, ff: Boolean = false) = RegionPalette(
            hex(c[0]), hex(c[1]), hex(c[2]), hex(c[3]), hex(c[4]), hex(c[5]), hex(c[6]), hex(c[7]), hex(c[8]), hex(c[9]), hex(c[10]), hex(c[11]), hex(c[12]), props, ff)

        fun of(id: String): RegionPalette = when (id) {
            "forest" -> p("#1d3b3a", "#6fa38a", "#2f5a4a", "#24463a", "#3f5f2f", "#4f7a3a", "#4a3324", "#2f6a3a", "#5fa24a", "#6a7064", "#d8ff8a", "#ffe9b0", "#5a8a7a", props = listOf("tree", "tree", "rock", "grass"), ff = true)
            "desert" -> p("#e88a4a", "#ffd9a0", "#d8955a", "#c47a42", "#e3b56f", "#d9a560", "#7a5a3a", "#5a8a4a", "#8ab86a", "#b07a50", "#ffe08a", "#fff0c8", "#f0c090", props = listOf("cactus", "rock", "rock", "pillar"))
            "coast" -> p("#3a6aa8", "#bfe6f0", "#3a7ab0", "#2a5a8a", "#e8d8a8", "#d8c490", "#8a6a4a", "#3a8a5a", "#6ac08a", "#7a8a94", "#bff6ff", "#fff8e8", "#a8d0e0", props = listOf("rock", "rock", "tree", "grass"))
            "peaks" -> p("#2a3a6a", "#a8c0e0", "#8aa0c8", "#5a6a8a", "#e8f0f8", "#c8d8e8", "#4a3a3a", "#2a4a4a", "#e8f8ff", "#6a7a8a", "#8ae0ff", "#e8f4ff", "#c8d8f0", props = listOf("rock", "crystal", "tree", "rock"))
            "ruins" -> p("#140f2a", "#5a3a7a", "#3a2a5a", "#2a2040", "#5a5470", "#6a6484", "#3a3048", "#4a3a6a", "#8a7ab0", "#8a84a0", "#ffd86b", "#d8c8ff", "#5a4a7a", props = listOf("pillar", "pillar", "crystal", "rock"), ff = true)
            else -> p("#5aa0e0", "#d8f0ff", "#8ab8a0", "#5a9a5a", "#6aa84a", "#7ab858", "#6a4a2a", "#3f8a3a", "#8ad05a", "#8a8a7a", "#fff08a", "#fff4d8", "#cfe8f0", props = listOf("tree", "rock", "grass", "grass"))
        }
    }
}
