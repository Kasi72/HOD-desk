package com.hoddesk.app.evaluation

/**
 * Small, synthetic, privacy-safe release corpus. It deliberately exercises
 * the values that can create a wrong official task: action, date, time,
 * location, people and reference numbers. It contains no patient data.
 */
object EvaluationCorpus {
    val baseline: List<EvaluationExample> = listOf(
        EvaluationExample("en-meeting-01", "text", "Attend the DME review at 2 PM on 19 September 2026 at Erode Collectorate.", "Attend the DME review", "19 September 2026", "2 PM", "Erode Collectorate", true),
        EvaluationExample("en-deadline-01", "document", "Submit the revised tele-psychiatry SOP to the Dean's office before Friday.", "Submit the revised tele-psychiatry SOP", null, null, "Dean's office", true),
        EvaluationExample("en-followup-01", "whatsapp", "Please follow up with Stores about the pending ward tablet quotation.", "Follow up with Stores about the ward tablet quotation", null, null, "Stores", true),
        EvaluationExample("en-info-01", "text", "The circular is available for your information. No reply is required.", null, null, null, null, false),
        EvaluationExample("ta-meeting-01", "text", "19 செப்டம்பர் 2026 அன்று மதியம் 2 மணிக்கு ஈரோடு ஆட்சியர் அலுவலகத்தில் DME கூட்டத்தில் கலந்து கொள்ளவும்.", "DME கூட்டத்தில் கலந்து கொள்ளவும்", "19 செப்டம்பர் 2026", "மதியம் 2 மணி", "ஈரோடு ஆட்சியர் அலுவலகம்", true),
        EvaluationExample("ta-duty-01", "text", "நாளை காலை 9 மணிக்கு வார்டு சுற்றுப்பார்வைக்கு வரவும்.", "வார்டு சுற்றுப்பார்வைக்கு வரவும்", "நாளை", "காலை 9 மணி", null, true),
        EvaluationExample("ta-info-01", "text", "இந்த தகவல் பதிவிற்காக மட்டும் பகிரப்படுகிறது.", null, null, null, null, false),
        EvaluationExample("tanglish-meeting-01", "whatsapp", "DME meeting 19 Sep 2026 2 PM Erode Collectorate la attend pannunga.", "Attend the DME meeting", "19 Sep 2026", "2 PM", "Erode Collectorate", true),
        EvaluationExample("tanglish-followup-01", "audio", "Stores kitta pending quotation pathi follow up pannunga.", "Follow up with Stores about the pending quotation", null, null, "Stores", true),
        EvaluationExample("ocr-go-01", "screenshot", "GO Ms No. 214 — Send compliance report by 30/09/2026.", "Send the compliance report", "30/09/2026", null, null, true),
        EvaluationExample("audio-number-01", "audio", "Join the online Zoom meeting with DME at 11:30 AM.", "Join the online Zoom meeting with DME", null, "11:30 AM", "Zoom", true),
        EvaluationExample("document-info-01", "pdf", "Minutes of the previous meeting — for record only.", null, null, null, null, false),
    )
}
