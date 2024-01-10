package com.r3.developers.csdetemplate.flowexample.workflows

import com.github.manosbatsis.corda5.testutils.integration.junit5.CombinedWorkerMode
import com.github.manosbatsis.corda5.testutils.integration.junit5.Corda5NodesConfig
import com.github.manosbatsis.corda5.testutils.integration.junit5.Corda5NodesExtension
import com.github.manosbatsis.corda5.testutils.integration.junit5.nodehandles.NodeHandles
import com.github.manosbatsis.corda5.testutils.rest.client.model.FlowRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Add the Corda5 nodes extension
@ExtendWith(Corda5NodesExtension::class)
open class IntegrationTests {

    // Optional
    val config = Corda5NodesConfig(

        authUsername = "admin",
        authPassword = "admin",
        baseUrl = "https://localhost:8888/api/v1/",
        httpMaxWaitSeconds = 120,
        debug = true,
        projectDir = Corda5NodesConfig.gradleRootDir,
        combinedWorkerMode = CombinedWorkerMode.PER_LAUNCHER

    )

    // Corda5 nodes extension provides the NodeHandles
    @Test
    fun workFlowTests(nodeHandles: NodeHandles) {
        // Get node handles
        val aliceNode = nodeHandles.getByCommonName("Alice")
        val bobNode = nodeHandles.getByCommonName("Bob")

        // Create flow args
        val flowArgs = MyFirstFlowStartArgs(bobNode.memberX500Name)
        // Call Flow
        val response = aliceNode.waitForFlow(
            FlowRequest(
                flowClass = MyFirstFlow::class.java,
                requestBody = flowArgs,
                flowResultClass = Message::class.java
            )
        )

        // Check status and flow result
        assertTrue(response.isSuccess())
        val expectedMessage = Message(bobNode.memberX500Name, "Hello Alice, best wishes from Bob")
        assertEquals(expectedMessage, response.flowResult)

    }


}