package com.hoddesk.app.reasoning

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionDependencyAnalyzerTest {
    @Test fun linksPreparationToMeeting() {
        val graph = ActionDependencyAnalyzer.analyze(listOf("Instruct officials to attend the DME meeting", "Attend the DME meeting"))
        assertEquals(1, graph.size)
    }

    @Test fun unrelated_work_does_not_get_an_invented_dependency() {
        val graph = ActionDependencyAnalyzer.analyze(listOf("Prepare the purchase report", "Attend the DME meeting"))
        assertEquals(0, graph.size)
    }

    @Test fun document_titles_are_not_dependencies() {
        val graph = ActionDependencyAnalyzer.analyze(listOf("Instruct officials to attend the DME meeting", "DME meeting on drug return"))
        assertEquals(0, graph.size)
    }

    @Test fun dependencies_do_not_depend_on_source_sentence_order() {
        val graph = ActionDependencyAnalyzer.analyze(listOf("Submit the budget report", "Prepare the budget report"))
        assertEquals(1, graph.size)
        assertEquals("Prepare the budget report", graph.single().prerequisite)
    }
}
