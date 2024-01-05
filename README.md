
# Corda5 Integration Testing

This fork builds on top of CSDE to provide a Quick setup for Corda5 integration tests with Gradle and JUnit5, 
both local and CI with GitHub Actions. 

> Note: seems corda-runtime-os release-5.0.0 disappeared from GitHub. This branch (v5.0.1-integration-tests) is based on CSDE release/corda-5-0, but uses Corda 5.1.0 and Java 17.

You can see all my changes in [this PR](https://github.com/manosbatsis/CSDE-cordapp-integration-testing/pull/1). 
There's also a Medium article [here](https://medium.com/@manosbatsis/corda5-integration-testing-4e98d6a195cd).

## Prerequisites

Same as [CSDE](https://docs.r3.com/en/tools-corda5/csde/prerequisites.html):

- Azul Zulu JDK 17
- Git ~v2.24.1
- Docker Engine ~v20.X.Y or Docker Desktop ~v3.5.X
- Corda CLI, see [Installing the Corda CLI](https://docs.r3.com/en/platform/corda/5.0/developing-applications/tooling/installing-corda-cli.html)

## Gradle Setup

The [corda5-testutils](https://github.com/manosbatsis/corda5-testutils) includes a JUnit5 extension 
for starting the Combined Worker and an API for calling flows etc. Let's add the dependency version to gradle.properties:

```properties
# Corda 5 Test Utils
corda5TestutilsVersion=1.2.1
```

Now into workflows/build.gradle, we add separate configuration, sourceSets etc. for integration tests:

```kotlin
// Add integrationTest config
apply from: "${rootDir}/gradle/integration-test.gradle"
```

And the corda5-testutils dependency for launching Corda's Combined Worker:

```kotlin
dependencies {
    // ...
    // Kotlin Test
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    // Corda 5 Test Utils
    testImplementation "com.github.manosbatsis.corda5.testutils:integration-junit5:$corda5TestutilsVersion"
   // ...
}
```


## Sample Test

Here's our [IntegrationTests](workflows/src/integrationTest/kotlin/com/r3/developers/csdetemplate/flowexample/workflows/IntegrationTests.kt):

```kotlin
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
```

## Testing with Gradle

We have to call integration tests explicitly:

```
./gradlew build integrationTest
```


## Testing with GitHub Actions

The [corda5-cli-action](https://github.com/manosbatsis/corda5-cli-action) will download, install and cache Corda5 CLI, 
so the Gradle build will just work. The workflow is [ci.yml](.github/workflows/ci.yml) contains the following:

```yaml
- name: Setup Corda CLI
  uses: manosbatsis/corda5-cli-action@v2.0.1
  with:
    cli-version: '5.0.1'
- name: Build with Gradle
  uses: gradle/gradle-build-action@v2
  with:
    arguments: build integrationTest
```
## Build Successful

All done. You can see the workflow runs in the repository’s [Actions](https://github.com/manosbatsis/CSDE-cordapp-integration-testing/actions).

