workspace "Cortavyn" "Durable agent systems for the JVM." {
    model {
        cortavyn = softwareSystem "Cortavyn" "A modular Java foundation for durable agents." {
            core = container "Core" "Durable run and state contracts." "Java library"
            modelApi = container "Model API" "Provider-neutral chat-model contracts." "Java library"
            graph = container "Graph" "Graph definitions and execution contracts." "Java library"
            chat = container "Chat" "Conversation, ChatAgent, and tool-loop contracts." "Java library"
            deep = container "Deep Agents" "Planning and deep-agent contracts." "Java library"
            providers = container "Provider adapters" "Optional integrations for model providers." "Java libraries"
            graph -> core "uses durable run contracts"
            chat -> modelApi "uses chat-model contracts"
            deep -> graph "plans executable graphs"
            deep -> modelApi "uses chat-model contracts"
            deep -> chat "uses tool-loop contracts"
            providers -> modelApi "implement chat-model contracts"
        }
    }
    views {
        container cortavyn "modules" { include * autoLayout lr }
        styles { element "Container" { shape RoundedBox } }
    }
}
