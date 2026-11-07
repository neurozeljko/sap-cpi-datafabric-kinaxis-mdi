import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import java.time.Instant
import java.util.UUID

def Message processData(Message message) {
    def properties = message.getProperties()
    def ex = properties.get("CamelExceptionCaught")
    
    String rawError = ex ? ex.getMessage() : "Data Fabric Streaming or Graph Cycle Evaluation Fault"
    String msgId = (properties.get("DataFabric_MessageID") ?: UUID.randomUUID().toString()).toString()

    def dlqEnvelope = [
        "CircuitBreakerIncident": [
            "Layer"             : "SUPPLY_CHAIN_DATA_FABRIC",
            "Timestamp"         : Instant.now().toString(),
            "FabricMessageId"   : msgId,
            "TargetEndpoint"    : properties.get("Target_GraphQL_Endpoint") ?: "https://api.kinaxis.com/graphql",
            "Status"            : "QUARANTINED_TO_FABRIC_DEAD_LETTER_QUEUE"
        ],
        "Diagnostics": [
            "RootCause"         : rawError,
            "CorrectiveAction"  : "Inspect S/4HANA Master Data Integration change pointer delta or check memory allocation limit"
        ]
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(dlqEnvelope)))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("X-Fabric-Circuit-Breaker", "OPEN")
    
    return message
}
