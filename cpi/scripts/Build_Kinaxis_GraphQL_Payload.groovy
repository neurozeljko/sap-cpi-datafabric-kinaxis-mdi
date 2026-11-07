import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.util.Base64

def Message processData(Message message) {
    String body = message.getBody(String.class)
    def slurper = new JsonSlurper()
    def fabricData = slurper.parseText(body)

    def cleanMaterials = fabricData.SyncPayload ?: []
    int chunkSize = 250
    
    String nextCursor = cleanMaterials.isEmpty() ? null : Base64.encoder.encodeToString("cursor-offset-${cleanMaterials.size()}".bytes)
    boolean hasMorePages = cleanMaterials.size() > chunkSize

    def graphqlMutation = """
mutation UpsertPartMasterBatch(\$input: PartMasterBatchInput!) {
    upsertPartMaster(batch: \$input) {
        successCount
        rejectedCount
        pageInfo {
            hasNextPage
            endCursor
        }
    }
}
""".stripIndent()

    def variables = [
        "input": [
            "clientContext": "SAP_MDI_DELTA_FABRIC",
            "executionTimestamp": new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'"),
            "items": cleanMaterials.collect { mat ->
                [
                    "partNumber"    : mat.MaterialID,
                    "description"   : mat.Description ?: "Raw Material / Component",
                    "baseUOM"       : mat.BaseUnitOfMeasure ?: "PCE",
                    "planningPlant" : mat.Plant ?: "1010",
                    "leadTimeDays"  : mat.LeadTimeDays ?: 14,
                    "bomComponents" : mat.Components?.collect { comp ->
                        [
                            "componentId": comp.ComponentID,
                            "quantity"   : comp.Quantity ?: 1.0
                        ]
                    } ?: []
                ]
            }
        ]
    ]

    def finalGraphQLRequest = [
        "query": graphqlMutation,
        "variables": variables,
        "cursorTelemetry": [
            "currentCursor": message.getProperty("Pagination_Cursor") ?: "START",
            "nextCursor"   : nextCursor,
            "hasMorePages" : hasMorePages,
            "chunkCount"   : cleanMaterials.size()
        ]
    ]

    message.setProperty("Next_Page_Cursor", nextCursor ?: "END_OF_STREAM")
    message.setProperty("Has_More_Pages", hasMorePages.toString())
    message.setProperty("Processed_Parts_Count", cleanMaterials.size())

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(finalGraphQLRequest)))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("X-Kinaxis-Mutation", "PartMasterBatch")

    return message
}
