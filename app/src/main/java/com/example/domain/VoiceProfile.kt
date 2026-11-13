package com.example.domain

enum class VoiceCategory {
    MALE,
    FEMALE,
    NARRATOR
}

data class VoiceProfile(
    val id: String,
    val displayName: String,
    val geminiVoiceName: String,
    val badge: String,
    val hinglishDescription: String,
    val stylePrompt: String,
    val previewSampleText: String,
    val category: VoiceCategory
)

object VoiceCatalog {
    val voices: List<VoiceProfile> = listOf(
        VoiceProfile(
            id = "deep_male",
            displayName = "Deep Male",
            geminiVoiceName = "Charon",
            badge = "Documentary",
            hinglishDescription = "Gehri, shant aur confident awaaz — documentaries ke liye",
            stylePrompt = "Read aloud in a deep, rich, calm, and confident documentary narrator voice with steady studio pacing, natural breathing, and zero robotic stiffness. Preserve the exact language (Hindi, English, or Hinglish) naturally without translating.",
            previewSampleText = "Namaste dosto, aaj hum ek aisi kahani sunenge jo waqt ke saath aur gehri hoti jaati hai.",
            category = VoiceCategory.MALE
        ),
        VoiceProfile(
            id = "cinematic_male",
            displayName = "Cinematic Male",
            geminiVoiceName = "Fenrir",
            badge = "Trailer",
            hinglishDescription = "Gehri aur dramatic awaaz — trailers aur movie explainers ke liye",
            stylePrompt = "Read aloud in a deep, dramatic, cinematic trailer and movie-explainer voice with commanding presence, rich resonance, and impactful pauses. Preserve the exact Hindi, English, or Hinglish words naturally without translating.",
            previewSampleText = "Jab andhera sabse gehra hota hai, tabhi ek nayi shuruaat ki awaaz goonjti hai.",
            category = VoiceCategory.MALE
        ),
        VoiceProfile(
            id = "natural_male",
            displayName = "Natural Male",
            geminiVoiceName = "Puck",
            badge = "Casual",
            hinglishDescription = "Friendly aur conversational awaaz — rozana narration ke liye",
            stylePrompt = "Read aloud in a warm, friendly, conversational everyday male voice as if talking naturally to a friend. Keep the rhythm effortless and human, pronouncing mixed Hindi and English words naturally without translating.",
            previewSampleText = "Hey guys! Aaj ka topic bahut interesting hai, chaliye bina time waste kiye shuru karte hain.",
            category = VoiceCategory.MALE
        ),
        VoiceProfile(
            id = "professional_female",
            displayName = "Professional Female",
            geminiVoiceName = "Kore",
            badge = "Education",
            hinglishDescription = "Saaf aur confident awaaz — educational content ke liye",
            stylePrompt = "Read aloud in a clear, articulate, confident, and polished female educator voice with crisp pronunciation and engaging clarity. Never translate; pronounce Hindi, English, and Hinglish seamlessly.",
            previewSampleText = "Aaj ke is lesson mein hum samjhenge ki complex ideas ko simple tareeke se kaise present kiya jaata hai.",
            category = VoiceCategory.FEMALE
        ),
        VoiceProfile(
            id = "soft_female",
            displayName = "Soft Female",
            geminiVoiceName = "Aoede",
            badge = "Stories",
            hinglishDescription = "Narm, warm aur gentle awaaz — kahaniyon ke liye",
            stylePrompt = "Read aloud in a soft, warm, gentle, and soothing female storytelling voice filled with subtle affection and calm intimacy. Never sound robotic and never translate; keep Hindi, English, and Hinglish pronunciation natural.",
            previewSampleText = "Baarish ki halki boondein khidki par gir rahi thi, aur ek purani yaad phir se taza ho gayi.",
            category = VoiceCategory.FEMALE
        ),
        VoiceProfile(
            id = "energetic_female",
            displayName = "Energetic Female",
            geminiVoiceName = "Zephyr",
            badge = "Creator",
            hinglishDescription = "Expressive aur enthusiastic awaaz — engaging videos ke liye",
            stylePrompt = "Read aloud in a bright, expressive, enthusiastic, and upbeat female creator voice full of positive energy and lively inflection. Pronounce mixed Hindi, English, and Hinglish smoothly without translating.",
            previewSampleText = "Wait, kya aapne ye naya feature try kiya? Sach mein, ye aapka poora workflow change kar dega!",
            category = VoiceCategory.FEMALE
        ),
        VoiceProfile(
            id = "storyteller",
            displayName = "Storyteller",
            geminiVoiceName = "Orus",
            badge = "Mystery",
            hinglishDescription = "Emotional narration aur intelligent pauses — psychology & mystery ke liye",
            stylePrompt = "Read aloud as a master emotional storyteller for psychology, mystery, and documentary narratives, using thoughtful pacing, intelligent dramatic pauses, and expressive tonal shifts. Never translate; pronounce Hindi, English, and Hinglish naturally.",
            previewSampleText = "Kya aapne kabhi socha hai... hamara dimaag kuch baatein yaad rakhta hai, aur kuch hamesha ke liye chhupa deta hai?",
            category = VoiceCategory.NARRATOR
        )
    )

    fun byId(id: String): VoiceProfile = voices.firstOrNull { it.id == id } ?: voices.first()
}
