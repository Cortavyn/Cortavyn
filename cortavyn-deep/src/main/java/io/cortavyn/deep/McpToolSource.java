package io.cortavyn.deep;

import io.cortavyn.chat.ChatTool;
import java.util.List;

/** Application-owned MCP adapter; transports, authentication, and lifecycle stay outside Cortavyn. */
public interface McpToolSource {
    /** Stable identifier used to address this source's resources. */
    default String name() { return getClass().getSimpleName(); }
    List<ChatTool> tools();
    default DeepWorkspace resources() { return new InMemoryWorkspace(); }
}
