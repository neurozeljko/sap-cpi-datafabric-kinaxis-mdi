import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def Message processData(Message message) {
    String body = message.getBody(String.class)
    def slurper = new JsonSlurper()
    def mdiPayload = slurper.parseText(body)

    def materials = mdiPayload.MasterDataItems ?: []
    
    Map<String, List<String>> bomGraph = [:]
    Map<String, Object> sanitizedMaterials = [:]

    materials.each { mat ->
        String parentId = mat.MaterialID
        sanitizedMaterials[parentId] = mat
        
        def children = mat.Components?.collect { it.ComponentID } ?: []
        bomGraph[parentId] = children
    }

    int cycleCount = 0
    Set<String> quarantinedMaterials = []

    bomGraph.keySet().each { node ->
        if (!quarantinedMaterials.contains(node)) {
            Set<String> visited = []
            Set<String> recursionStack = []
            
            if (hasCycle(node, bomGraph, visited, recursionStack)) {
                cycleCount++
                quarantinedMaterials.addAll(recursionStack)
            }
        }
    }

    def cleanPayload = sanitizedMaterials.values().findAll { !quarantinedMaterials.contains(it.MaterialID) }

    def targetFabricEnvelope = [
        "FabricMetadata": [
            "SourceSystem"       : "SAP_MDI_S4HANA",
            "TargetSystem"       : "KINAXIS_MAESTRO",
            "TotalReceived"      : materials.size(),
            "CircularBOMsDropped": quarantinedMaterials.size(),
            "SyncReady"          : cleanPayload.size()
        ],
        "SyncPayload": cleanPayload
    ]

    message.setProperty("Total_BOM_Cycles_Detected", quarantinedMaterials.size())
    message.setProperty("Valid_Materials_Count", cleanPayload.size())
    
    if (quarantinedMaterials.size() > 0) {
        message.setHeader("X-Fabric-Alert", "CIRCULAR_BOM_DETECTED")
    }

    message.setBody(JsonOutput.toJson(targetFabricEnvelope))
    message.setHeader("Content-Type", "application/json")

    return message
}

boolean hasCycle(String node, Map<String, List<String>> graph, Set<String> visited, Set<String> recStack) {
    if (recStack.contains(node)) return true
    if (visited.contains(node)) return false

    visited.add(node)
    recStack.add(node)

    List<String> children = graph.get(node) ?: []
    for (String child : children) {
        if (hasCycle(child, graph, visited, recStack)) {
            return true
        }
    }
    recStack.remove(node)
    return false
}
