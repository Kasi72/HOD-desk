package com.hoddesk.app.privacy

enum class WorkType { LIGHT_IMPORT, SEARCH, HEAVY_INDEX, AUDIO_TRANSCRIPTION, EVALUATION }

object ProcessingPolicy {
    fun canRun(batteryPercent: Int, charging: Boolean, workType: WorkType): Boolean {
        require(batteryPercent in 0..100) { "Battery percentage must be between 0 and 100" }
        if (batteryPercent < 20) return workType == WorkType.LIGHT_IMPORT || workType == WorkType.SEARCH
        if (workType == WorkType.HEAVY_INDEX || workType == WorkType.AUDIO_TRANSCRIPTION || workType == WorkType.EVALUATION) return charging
        return true
    }
}
