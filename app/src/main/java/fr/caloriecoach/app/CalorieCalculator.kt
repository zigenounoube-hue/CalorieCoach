package fr.caloriecoach.app

data class CalorieSuggestion(val maintenance: Int, val target: Int, val explanation: String)

object CalorieCalculator {
    fun estimate(ageText: String, heightText: String, weightText: String, sex: String, activity: String, goal: String): CalorieSuggestion? {
        val age = ageText.toIntOrNull() ?: return null
        val height = heightText.toIntOrNull() ?: return null
        val weight = weightText.toDoubleOrNull() ?: return null
        if (age !in 18..100 || height !in 120..230 || weight !in 35.0..300.0) return null
        val sexOffset = when (sex) { "Femme" -> -161.0; "Homme" -> 5.0; "Moyenne" -> -78.0; else -> return null }
        val activityFactor = when (activity) { "Sédentaire" -> 1.2; "Léger" -> 1.375; "Modéré" -> 1.55; "Très actif" -> 1.725; else -> return null }
        val resting = 10.0 * weight + 6.25 * height - 5.0 * age + sexOffset
        val maintenance = (resting * activityFactor).toInt().coerceAtLeast(0)
        val floor = when (sex) { "Femme" -> 1200; "Homme" -> 1500; else -> 1350 }
        val target = when (goal) {
            "Perte de poids" -> (maintenance - 500).coerceAtLeast(floor)
            "Prise de muscle" -> maintenance + 250
            else -> maintenance
        }
        if (goal == "Perte de poids" && maintenance < floor + 150) return null
        return CalorieSuggestion(maintenance, target, if (goal == "Perte de poids") "Estimation pour une perte progressive" else "Estimation de maintien")
    }
}
